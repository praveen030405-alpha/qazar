//! Priority-based Tile Scheduler for asynchronous tile rendering and prefetch.
//!
//! Architecture Reference (Section 3.1, line 74):
//! "Prioritize the prefetch queue by predicted tile entry time × required mip level."

use std::cmp::Ordering;
use std::collections::BinaryHeap;
use std::sync::Arc;
use crossbeam_channel::{unbounded, Receiver, Sender};
use parking_lot::Mutex;
use crate::pyramid::TileCoord;

/// Priority score for scheduling tile rasterization. Lower score = higher priority.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct TilePriority {
    /// Score = predicted_entry_time_ms × (1.0 + mip_level as f64)
    pub score: f64,
}

impl Eq for TilePriority {}

impl PartialOrd for TilePriority {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl Ord for TilePriority {
    fn cmp(&self, other: &Self) -> Ordering {
        // BinaryHeap is a max-heap, so we invert ordering to get lowest score first
        other.score.partial_cmp(&self.score).unwrap_or(Ordering::Equal)
    }
}

/// A request to rasterize a tile.
#[derive(Debug, Clone, Eq, PartialEq)]
pub struct ScheduledTileRequest {
    pub coord: TileCoord,
    pub priority: TilePriority,
}

impl Ord for ScheduledTileRequest {
    fn cmp(&self, other: &Self) -> Ordering {
        self.priority.cmp(&other.priority)
    }
}

impl PartialOrd for ScheduledTileRequest {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

/// Scheduler managing the priority work queue for background tile workers.
pub struct TileScheduler {
    work_sender: Sender<ScheduledTileRequest>,
    work_receiver: Receiver<ScheduledTileRequest>,
    pending_queue: Mutex<BinaryHeap<ScheduledTileRequest>>,
}

impl TileScheduler {
    pub fn new() -> Arc<Self> {
        let (s, r) = unbounded();
        Arc::new(Self {
            work_sender: s,
            work_receiver: r,
            pending_queue: Mutex::new(BinaryHeap::new()),
        })
    }

    /// Enqueue a tile request with its calculated entry time and mip level.
    pub fn enqueue(&self, coord: TileCoord, predicted_entry_ms: f64) {
        let score = predicted_entry_ms * (1.0 + coord.mip_level as f64 * 0.5);
        let req = ScheduledTileRequest {
            coord,
            priority: TilePriority { score },
        };

        // If immediately visible (entry <= 0 ms), send directly to worker channel
        if predicted_entry_ms <= 0.0 {
            let _ = self.work_sender.send(req);
        } else {
            self.pending_queue.lock().push(req);
        }
    }

    /// Flush the highest priority pending prefetch requests into the worker queue.
    pub fn dispatch_prefetch(&self, count: usize) {
        let mut queue = self.pending_queue.lock();
        for _ in 0..count {
            if let Some(req) = queue.pop() {
                let _ = self.work_sender.send(req);
            } else {
                break;
            }
        }
    }

    /// Worker receiver end of the work channel.
    pub fn worker_receiver(&self) -> Receiver<ScheduledTileRequest> {
        self.work_receiver.clone()
    }

    /// Clear all pending requests (e.g. on rapid scroll direction change or document jump).
    pub fn clear(&self) {
        self.pending_queue.lock().clear();
        while self.work_receiver.try_recv().is_ok() {}
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_scheduler_priority_order() {
        let sched = TileScheduler::new();

        // Enqueue tile with predicted entry 100ms vs 20ms
        let c_far = TileCoord::new(0, 0, 0, 0);
        let c_near = TileCoord::new(0, 0, 0, 1);

        sched.enqueue(c_far, 100.0);
        sched.enqueue(c_near, 20.0);

        sched.dispatch_prefetch(2);
        let r = sched.worker_receiver();

        let first = r.recv().unwrap();
        assert_eq!(first.coord, c_near);

        let second = r.recv().unwrap();
        assert_eq!(second.coord, c_far);
    }
}
