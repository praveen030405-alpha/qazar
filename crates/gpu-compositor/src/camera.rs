//! Viewport and Camera Navigation Model.
//!
//! Architecture Reference (Section 3, lines 64-85):
//! - Scroll / fling physics integration
//! - Zoom transform in render graph (vector-first on settle)
//! - Clamping to content boundaries

#[derive(Debug, Clone, Copy)]
pub struct Viewport {
    pub width: f32,
    pub height: f32,
    pub scale_factor: f32,
}

impl Viewport {
    pub fn new(width: f32, height: f32, scale_factor: f32) -> Self {
        Self {
            width,
            height,
            scale_factor,
        }
    }
}

/// Camera holding scroll offsets, zoom scale, and gesture state.
#[derive(Debug, Clone)]
pub struct Camera {
    pub scroll_x: f64,
    pub scroll_y: f64,
    pub zoom: f32,
    pub min_zoom: f32,
    pub max_zoom: f32,
}

impl Camera {
    pub fn new() -> Self {
        Self {
            scroll_x: 0.0,
            scroll_y: 0.0,
            zoom: 1.0,
            min_zoom: 0.25,
            max_zoom: 5.0,
        }
    }

    /// Pan camera by (dx, dy) pixels.
    pub fn pan(&mut self, dx: f64, dy: f64, max_scroll_y: f64) {
        self.scroll_x = (self.scroll_x - dx).max(0.0);
        self.scroll_y = (self.scroll_y - dy).clamp(0.0, max_scroll_y.max(0.0));
    }

    /// Zoom centered around a specific screen anchor point (anchor_x, anchor_y).
    pub fn zoom_at(&mut self, factor: f32, anchor_x: f64, anchor_y: f64) {
        let old_zoom = self.zoom;
        let new_zoom = (old_zoom * factor).clamp(self.min_zoom, self.max_zoom);
        let ratio = (new_zoom / old_zoom) as f64;

        // Adjust scroll to keep anchor stationary under zoom
        self.scroll_x = anchor_x - ratio * (anchor_x - self.scroll_x);
        self.scroll_y = anchor_y - ratio * (anchor_y - self.scroll_y);
        self.zoom = new_zoom;
    }

    /// Reset camera to top of document.
    pub fn reset(&mut self) {
        self.scroll_x = 0.0;
        self.scroll_y = 0.0;
        self.zoom = 1.0;
    }
}

impl Default for Camera {
    fn default() -> Self {
        Self::new()
    }
}
