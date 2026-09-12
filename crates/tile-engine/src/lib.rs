//! # Tile Engine
//!
//! Sub-page tile pyramid, kinematic prefetch scheduler, and L1 cache.
//!
//! Architecture Reference (Section 3.1, line 74):
//! "Each page is a mip pyramid of 256×256 device-pixel tiles (like a slippy-map, per page).
//! The compositor samples from a GPU tile atlas; a scheduler decides what to rasterize next.
//! The predictor is a kinematic model fed by the platform scroller's own physics: we sample
//! touch/scroll velocity and deceleration and integrate forward 100–300 ms to predict the
//! future viewport, then prioritize the prefetch queue by predicted tile entry time × required mip level.
//! Fast fling -> only low mips are requested; decelerating -> high mips fill in before settle."

pub mod cache;
pub mod kinematic;
pub mod pyramid;
pub mod raster_backend;
pub mod scheduler;

pub use cache::{L1TileCache, TileBitmap};
pub use kinematic::{KinematicPredictor, MotionState, ViewportPredictor};
pub use pyramid::{MipPyramid, TileCoord, TILE_SIZE};
pub use raster_backend::{PdfiumRasterBackend, RasterBackend};
pub use scheduler::{ScheduledTileRequest, TilePriority, TileScheduler};
