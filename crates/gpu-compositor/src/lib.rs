//! # GPU Compositor
//!
//! wgpu-backed tile atlas, continuous scroll page layout, camera transform,
//! and high-performance quad rendering pipeline.
//!
//! Architecture Reference (Section 3, lines 62-85 & Section 5, line 143):
//! - GPU backend: wgpu
//! - L0 GPU Tile Atlas: 96 MB VRAM budget
//! - Continuous vertical scroll layout with inter-page gaps
//! - Zoom transform in render graph (vector-first re-raster on settle)
//! - 120 Hz frame budget

pub mod atlas;
pub mod camera;
pub mod layout;
pub mod pipeline;

pub use atlas::{AtlasSlot, GpuTileAtlas};
pub use camera::{Camera, Viewport};
pub use layout::{ChunkedLayoutResolver, ContinuousPageLayout, VisiblePage, VisibleTile};
pub use pipeline::{CompositorPipeline, QuadVertex};
