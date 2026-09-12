//! # Memory Governor
//!
//! Enforces multi-tier memory budgets across the entire application.
//!
//! Architecture Reference (Section 5, lines 137-165):
//! "Hard rule: no unbounded in-memory page objects. Every cache has a byte budget
//! enforced by a single memory governor that owns all allocation accounting."
//!
//! Tiers (for 4GB device / single doc):
//! - L0 (GPU Tile Atlas): 96 MB VRAM
//! - L1 (Near-viewport Bitmaps): 96 MB RAM
//! - L2 (Decoded Resources): 32 MB
//! - L3 (Parsed Page Graphs): 16 MB
//! - Hard overall memory ceiling under pressure: 250 MB

use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;
use parking_lot::RwLock;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum MemoryGovernorError {
    #[error("Allocation of {requested_bytes} bytes in tier {tier:?} exceeds budget (current: {current_bytes}, max: {budget_bytes})")]
    BudgetExceeded {
        tier: MemoryTier,
        requested_bytes: usize,
        current_bytes: usize,
        budget_bytes: usize,
    },
    #[error("Allocation failed: total process memory ceiling ({0} bytes) exceeded")]
    TotalCeilingExceeded(usize),
}

/// Cache tiers defined in Architecture Section 5.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum MemoryTier {
    /// L0: GPU Tile Atlas (VRAM slots being composited + immediate neighbors)
    L0GpuAtlas,
    /// L1: Near-viewport decoded bitmap tiles (RAM)
    L1NearViewportBitmaps,
    /// L2: Decoded resources (fonts, ICC profiles, image XObjects)
    L2DecodedResources,
    /// L3: Parsed page graphs (PDF object graphs & xref structures)
    L3PageGraphs,
}

impl MemoryTier {
    pub fn name(&self) -> &'static str {
        match self {
            MemoryTier::L0GpuAtlas => "L0 (GPU Atlas)",
            MemoryTier::L1NearViewportBitmaps => "L1 (Near-Viewport Bitmaps)",
            MemoryTier::L2DecodedResources => "L2 (Decoded Resources)",
            MemoryTier::L3PageGraphs => "L3 (Page Graphs)",
        }
    }

    /// Default byte budgets per tier for single-document desktop/4GB target.
    pub fn default_budget(&self) -> usize {
        match self {
            MemoryTier::L0GpuAtlas => 96 * 1024 * 1024,          // 96 MB
            MemoryTier::L1NearViewportBitmaps => 96 * 1024 * 1024, // 96 MB
            MemoryTier::L2DecodedResources => 32 * 1024 * 1024,   // 32 MB
            MemoryTier::L3PageGraphs => 16 * 1024 * 1024,         // 16 MB
        }
    }
}

/// System memory pressure level.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MemoryPressureLevel {
    Normal,
    Moderate,
    Critical,
}

struct TierState {
    current_bytes: AtomicUsize,
    budget_bytes: AtomicUsize,
}

impl TierState {
    fn new(budget: usize) -> Self {
        Self {
            current_bytes: AtomicUsize::new(0),
            budget_bytes: AtomicUsize::new(budget),
        }
    }
}

/// Central Memory Governor that tracks memory allocations across all tiers.
pub struct MemoryGovernor {
    l0: TierState,
    l1: TierState,
    l2: TierState,
    l3: TierState,
    total_ceiling: AtomicUsize,
    pressure_level: RwLock<MemoryPressureLevel>,
}

impl MemoryGovernor {
    /// Create a memory governor with architecture defaults (250 MB process ceiling).
    pub fn new() -> Arc<Self> {
        Self::with_ceiling(250 * 1024 * 1024)
    }

    /// Create a memory governor with a custom total memory ceiling.
    pub fn with_ceiling(total_ceiling_bytes: usize) -> Arc<Self> {
        Arc::new(Self {
            l0: TierState::new(MemoryTier::L0GpuAtlas.default_budget()),
            l1: TierState::new(MemoryTier::L1NearViewportBitmaps.default_budget()),
            l2: TierState::new(MemoryTier::L2DecodedResources.default_budget()),
            l3: TierState::new(MemoryTier::L3PageGraphs.default_budget()),
            total_ceiling: AtomicUsize::new(total_ceiling_bytes),
            pressure_level: RwLock::new(MemoryPressureLevel::Normal),
        })
    }

    fn tier_state(&self, tier: MemoryTier) -> &TierState {
        match tier {
            MemoryTier::L0GpuAtlas => &self.l0,
            MemoryTier::L1NearViewportBitmaps => &self.l1,
            MemoryTier::L2DecodedResources => &self.l2,
            MemoryTier::L3PageGraphs => &self.l3,
        }
    }

    /// Current allocated bytes in a tier.
    pub fn current_bytes(&self, tier: MemoryTier) -> usize {
        self.tier_state(tier).current_bytes.load(Ordering::Relaxed)
    }

    /// Current budget of a tier.
    pub fn budget_bytes(&self, tier: MemoryTier) -> usize {
        self.tier_state(tier).budget_bytes.load(Ordering::Relaxed)
    }

    /// Total allocated bytes across all monitored tiers.
    pub fn total_allocated_bytes(&self) -> usize {
        self.current_bytes(MemoryTier::L0GpuAtlas)
            + self.current_bytes(MemoryTier::L1NearViewportBitmaps)
            + self.current_bytes(MemoryTier::L2DecodedResources)
            + self.current_bytes(MemoryTier::L3PageGraphs)
    }

    /// Overall process ceiling budget.
    pub fn total_ceiling_bytes(&self) -> usize {
        self.total_ceiling.load(Ordering::Relaxed)
    }

    /// Try allocating `bytes` in `tier`. Returns an `AllocationToken` on success.
    pub fn try_allocate(
        self: &Arc<Self>,
        tier: MemoryTier,
        bytes: usize,
    ) -> Result<AllocationToken, MemoryGovernorError> {
        let state = self.tier_state(tier);
        let current = state.current_bytes.load(Ordering::Relaxed);
        let budget = state.budget_bytes.load(Ordering::Relaxed);

        if current + bytes > budget {
            return Err(MemoryGovernorError::BudgetExceeded {
                tier,
                requested_bytes: bytes,
                current_bytes: current,
                budget_bytes: budget,
            });
        }

        let total = self.total_allocated_bytes();
        let ceiling = self.total_ceiling_bytes();
        if total + bytes > ceiling {
            return Err(MemoryGovernorError::TotalCeilingExceeded(ceiling));
        }

        state.current_bytes.fetch_add(bytes, Ordering::SeqCst);

        Ok(AllocationToken {
            governor: Arc::clone(self),
            tier,
            bytes,
        })
    }

    /// Record a deallocation when an allocation token drops or is manually returned.
    fn release(&self, tier: MemoryTier, bytes: usize) {
        let state = self.tier_state(tier);
        state.current_bytes.fetch_sub(bytes, Ordering::SeqCst);
    }

    /// Check if a tier needs eviction to stay comfortably under budget.
    pub fn needs_eviction(&self, tier: MemoryTier) -> bool {
        let current = self.current_bytes(tier);
        let budget = self.budget_bytes(tier);
        current >= (budget * 9) / 10 // 90% threshold for proactive LRU eviction
    }

    /// Set simulated or platform system memory pressure level.
    pub fn set_pressure_level(&self, level: MemoryPressureLevel) {
        *self.pressure_level.write() = level;
        match level {
            MemoryPressureLevel::Normal => {
                self.l0.budget_bytes.store(MemoryTier::L0GpuAtlas.default_budget(), Ordering::SeqCst);
                self.l1.budget_bytes.store(MemoryTier::L1NearViewportBitmaps.default_budget(), Ordering::SeqCst);
            }
            MemoryPressureLevel::Moderate => {
                // Shrink budgets by 30%
                self.l0.budget_bytes.store((MemoryTier::L0GpuAtlas.default_budget() * 7) / 10, Ordering::SeqCst);
                self.l1.budget_bytes.store((MemoryTier::L1NearViewportBitmaps.default_budget() * 7) / 10, Ordering::SeqCst);
            }
            MemoryPressureLevel::Critical => {
                // Shrink budgets by 60%
                self.l0.budget_bytes.store((MemoryTier::L0GpuAtlas.default_budget() * 4) / 10, Ordering::SeqCst);
                self.l1.budget_bytes.store((MemoryTier::L1NearViewportBitmaps.default_budget() * 4) / 10, Ordering::SeqCst);
            }
        }
    }

    /// Current memory pressure level.
    pub fn pressure_level(&self) -> MemoryPressureLevel {
        *self.pressure_level.read()
    }
}

/// An RAII token representing an allocation in a specific tier.
/// Automatically releases its reserved memory when dropped.
pub struct AllocationToken {
    governor: Arc<MemoryGovernor>,
    tier: MemoryTier,
    bytes: usize,
}

impl AllocationToken {
    pub fn tier(&self) -> MemoryTier {
        self.tier
    }

    pub fn bytes(&self) -> usize {
        self.bytes
    }
}

impl Drop for AllocationToken {
    fn drop(&mut self) {
        self.governor.release(self.tier, self.bytes);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_allocation_and_drop_release() {
        let gov = MemoryGovernor::new();
        assert_eq!(gov.current_bytes(MemoryTier::L0GpuAtlas), 0);

        {
            let token = gov.try_allocate(MemoryTier::L0GpuAtlas, 1024 * 1024).unwrap();
            assert_eq!(gov.current_bytes(MemoryTier::L0GpuAtlas), 1024 * 1024);
            assert_eq!(token.bytes(), 1024 * 1024);
        }

        // Token dropped, should be 0
        assert_eq!(gov.current_bytes(MemoryTier::L0GpuAtlas), 0);
    }

    #[test]
    fn test_budget_exceeded() {
        let gov = MemoryGovernor::with_ceiling(1000);
        // Force tiny budget
        gov.l0.budget_bytes.store(500, Ordering::SeqCst);

        let _token1 = gov.try_allocate(MemoryTier::L0GpuAtlas, 400).unwrap();
        let res = gov.try_allocate(MemoryTier::L0GpuAtlas, 200);
        assert!(res.is_err());
    }

    #[test]
    fn test_pressure_level_adjustment() {
        let gov = MemoryGovernor::new();
        gov.set_pressure_level(MemoryPressureLevel::Moderate);
        assert_eq!(
            gov.budget_bytes(MemoryTier::L0GpuAtlas),
            (96 * 1024 * 1024 * 7) / 10
        );
    }
}
