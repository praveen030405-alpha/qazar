//! L1 In-Memory Tile Bitmap Cache.
//!
//! Architecture Reference (Section 5, lines 144):
//! "L1: near-viewport bitmaps (decoded tiles ±N pages, N velocity-adaptive, 96 MB RAM budget)"

use std::collections::{HashMap, VecDeque};
use std::sync::Arc;
use memory_governor::{AllocationToken, MemoryGovernor, MemoryTier};
use parking_lot::Mutex;
use pdf_kernel::RgbaBuffer;
use crate::pyramid::{TileCoord, TILE_SIZE};

/// Size of one RGBA8 256×256 tile in bytes (256 KB).
pub const TILE_BYTE_SIZE: usize = (TILE_SIZE * TILE_SIZE * 4) as usize;

/// A cached tile bitmap held in RAM with its MemoryGovernor allocation token.
pub struct TileBitmap {
    pub coord: TileCoord,
    pub buffer: RgbaBuffer,
    _token: AllocationToken,
}

/// Thread-safe LRU ring cache for L1 decoded tile bitmaps.
pub struct L1TileCache {
    governor: Arc<MemoryGovernor>,
    entries: Mutex<HashMap<TileCoord, Arc<TileBitmap>>>,
    lru_order: Mutex<VecDeque<TileCoord>>,
}

impl L1TileCache {
    pub fn new(governor: Arc<MemoryGovernor>) -> Self {
        Self {
            governor,
            entries: Mutex::new(HashMap::new()),
            lru_order: Mutex::new(VecDeque::new()),
        }
    }

    /// Retrieve a tile from L1 cache if resident. Updates LRU position.
    pub fn get(&self, coord: &TileCoord) -> Option<Arc<TileBitmap>> {
        let entries = self.entries.lock();
        if let Some(tile) = entries.get(coord) {
            let mut lru = self.lru_order.lock();
            if let Some(pos) = lru.iter().position(|c| c == coord) {
                lru.remove(pos);
                lru.push_back(*coord);
            }
            Some(Arc::clone(tile))
        } else {
            None
        }
    }

    /// Insert a newly rasterized tile into L1 cache, evicting oldest resident tiles if budget requires.
    pub fn insert(&self, coord: TileCoord, buffer: RgbaBuffer) -> Option<Arc<TileBitmap>> {
        // First check if governor needs eviction
        while self.governor.needs_eviction(MemoryTier::L1NearViewportBitmaps) {
            if !self.evict_one_oldest() {
                break;
            }
        }

        // Try allocating from memory governor
        let token = match self
            .governor
            .try_allocate(MemoryTier::L1NearViewportBitmaps, TILE_BYTE_SIZE)
        {
            Ok(tok) => tok,
            Err(_) => {
                // If allocation failed, force evict until space is available
                if self.evict_one_oldest() {
                    match self
                        .governor
                        .try_allocate(MemoryTier::L1NearViewportBitmaps, TILE_BYTE_SIZE)
                    {
                        Ok(tok) => tok,
                        Err(_) => return None,
                    }
                } else {
                    return None;
                }
            }
        };

        let tile = Arc::new(TileBitmap {
            coord,
            buffer,
            _token: token,
        });

        let mut entries = self.entries.lock();
        let mut lru = self.lru_order.lock();

        entries.insert(coord, Arc::clone(&tile));
        lru.push_back(coord);

        Some(tile)
    }

    /// Evict the single oldest tile from the cache.
    fn evict_one_oldest(&self) -> bool {
        let mut lru = self.lru_order.lock();
        if let Some(oldest) = lru.pop_front() {
            let mut entries = self.entries.lock();
            entries.remove(&oldest);
            true
        } else {
            false
        }
    }

    /// Number of tiles currently in cache.
    pub fn len(&self) -> usize {
        self.entries.lock().len()
    }

    /// Is cache empty?
    pub fn is_empty(&self) -> bool {
        self.entries.lock().is_empty()
    }

    /// Clear all tiles for a specific page (e.g. on invalidation or zoom change).
    pub fn invalidate_page(&self, page: usize) {
        let mut entries = self.entries.lock();
        let mut lru = self.lru_order.lock();

        let coords_to_remove: Vec<TileCoord> = entries
            .keys()
            .filter(|c| c.page == page)
            .copied()
            .collect();

        for c in coords_to_remove {
            entries.remove(&c);
            lru.retain(|x| *x != c);
        }
    }
}
