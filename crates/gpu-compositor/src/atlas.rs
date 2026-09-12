//! L0 GPU Tile Atlas.
//!
//! Architecture Reference (Section 5, line 143):
//! "L0: GPU tile atlas (tiles being composited + immediate neighbors, 96 MB VRAM budget)"
//!
//! A 256×256 RGBA8 tile is 262,144 bytes (256 KB).
//! 2048×2048 = 8×8 = 64 slots = 16 MB.
//! 2048×4096 = 8×16 = 128 slots = 32 MB.
//! A 2048×2048 default sheet dynamically scales and minimizes initial driver allocation,
//! keeping process RSS well below the 250 MB ceiling while having plenty of slots for visible tiles.

use std::collections::{HashMap, VecDeque};
use std::sync::Arc;
use memory_governor::{AllocationToken, MemoryGovernor, MemoryTier};
use parking_lot::Mutex;
use tile_engine::{TileCoord, TILE_SIZE};

/// Size in bytes of one tile in VRAM (256×256 × 4 bytes = 256 KB).
pub const ATLAS_SLOT_BYTES: usize = (TILE_SIZE * TILE_SIZE * 4) as usize;

/// Maximum number of tile slots allowed by the 96 MB VRAM budget (384 slots).
pub const MAX_ATLAS_SLOTS: usize = (96 * 1024 * 1024) / ATLAS_SLOT_BYTES;

/// Location of a tile within an atlas sheet.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AtlasSlot {
    pub slot_id: u32,
    pub x: u32,
    pub y: u32,
    pub width: u32,
    pub height: u32,
}

/// GPU Tile Atlas state tracking slot allocation and LRU eviction.
pub struct GpuTileAtlas {
    governor: Arc<MemoryGovernor>,
    sheet_width: u32,
    sheet_height: u32,
    slots_per_row: u32,
    _total_slots: u32,
    occupied_slots: Mutex<HashMap<TileCoord, (AtlasSlot, AllocationToken)>>,
    free_slot_ids: Mutex<Vec<u32>>,
    lru_history: Mutex<VecDeque<TileCoord>>,
}

impl GpuTileAtlas {
    /// Create a new tile atlas matching the 96 MB VRAM budget with optimal sheet dimensions.
    /// Default: 2048×2048 texture sheet = 8×8 = 64 slots = 16 MB initial VRAM.
    pub fn new(governor: Arc<MemoryGovernor>) -> Self {
        let sheet_width = 2048;
        let sheet_height = 2048;
        let slots_per_row = sheet_width / TILE_SIZE; // 8
        let total_slots = (sheet_width / TILE_SIZE) * (sheet_height / TILE_SIZE); // 64 slots

        let free_ids = (0..total_slots).collect();

        Self {
            governor,
            sheet_width,
            sheet_height,
            slots_per_row,
            _total_slots: total_slots,
            occupied_slots: Mutex::new(HashMap::new()),
            free_slot_ids: Mutex::new(free_ids),
            lru_history: Mutex::new(VecDeque::new()),
        }
    }

    pub fn sheet_width(&self) -> u32 {
        self.sheet_width
    }

    pub fn sheet_height(&self) -> u32 {
        self.sheet_height
    }

    /// Check if a tile coordinate is currently resident in the GPU atlas.
    pub fn get_slot(&self, coord: &TileCoord) -> Option<AtlasSlot> {
        let occ = self.occupied_slots.lock();
        if let Some((slot, _)) = occ.get(coord) {
            let mut lru = self.lru_history.lock();
            if let Some(idx) = lru.iter().position(|c| c == coord) {
                lru.remove(idx);
                lru.push_back(*coord);
            }
            Some(*slot)
        } else {
            None
        }
    }

    /// Allocate or evict a slot for the incoming tile coordinate.
    pub fn allocate_slot(&self, coord: TileCoord) -> Option<AtlasSlot> {
        // If already present, return existing slot
        if let Some(slot) = self.get_slot(&coord) {
            return Some(slot);
        }

        // Try getting a free slot ID or evict oldest
        let slot_id = {
            let mut free = self.free_slot_ids.lock();
            if let Some(id) = free.pop() {
                id
            } else {
                // Atlas is full, evict oldest LRU tile
                drop(free);
                self.evict_oldest()?
            }
        };

        // Try allocating from memory governor
        let token = match self
            .governor
            .try_allocate(MemoryTier::L0GpuAtlas, ATLAS_SLOT_BYTES)
        {
            Ok(tok) => tok,
            Err(_) => {
                // Free slot back and fail gracefully
                self.free_slot_ids.lock().push(slot_id);
                return None;
            }
        };

        let col = slot_id % self.slots_per_row;
        let row = slot_id / self.slots_per_row;

        let slot = AtlasSlot {
            slot_id,
            x: col * TILE_SIZE,
            y: row * TILE_SIZE,
            width: TILE_SIZE,
            height: TILE_SIZE,
        };

        self.occupied_slots
            .lock()
            .insert(coord, (slot, token));
        self.lru_history.lock().push_back(coord);

        Some(slot)
    }

    /// Evict the oldest tile and reclaim its slot ID.
    fn evict_oldest(&self) -> Option<u32> {
        let mut lru = self.lru_history.lock();
        if let Some(oldest) = lru.pop_front() {
            let mut occ = self.occupied_slots.lock();
            if let Some((slot, _)) = occ.remove(&oldest) {
                Some(slot.slot_id)
            } else {
                None
            }
        } else {
            None
        }
    }

    /// Total resident tiles currently on GPU.
    pub fn resident_count(&self) -> usize {
        self.occupied_slots.lock().len()
    }

    /// Invalidate tiles for a given page.
    pub fn invalidate_page(&self, page: usize) {
        let mut occ = self.occupied_slots.lock();
        let mut free = self.free_slot_ids.lock();
        let mut lru = self.lru_history.lock();

        let coords: Vec<TileCoord> = occ
            .keys()
            .filter(|c| c.page == page)
            .copied()
            .collect();

        for c in coords {
            if let Some((slot, _)) = occ.remove(&c) {
                free.push(slot.slot_id);
                lru.retain(|x| *x != c);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_atlas_slot_assignment() {
        let gov = MemoryGovernor::new();
        let atlas = GpuTileAtlas::new(gov);

        let c1 = TileCoord::new(0, 0, 0, 0);
        let s1 = atlas.allocate_slot(c1).unwrap();
        assert_eq!(s1.slot_id, 63); // pops from end of 0..64

        let c2 = TileCoord::new(0, 0, 0, 1);
        let s2 = atlas.allocate_slot(c2).unwrap();
        assert_eq!(s2.slot_id, 62);

        assert_eq!(atlas.resident_count(), 2);
    }
}
