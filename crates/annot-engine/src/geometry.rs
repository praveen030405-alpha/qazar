use serde::{Deserialize, Serialize};

/// High-precision stylus point containing spatial, kinematic, and sensor metadata
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub struct InkPoint {
    pub x: f32,
    pub y: f32,
    pub pressure: f32,
    pub tilt_x: f32,
    pub tilt_y: f32,
    pub timestamp_ms: u32,
}

impl InkPoint {
    pub fn new(x: f32, y: f32, pressure: f32) -> Self {
        Self {
            x,
            y,
            pressure: pressure.clamp(0.0, 1.0),
            tilt_x: 0.0,
            tilt_y: 0.0,
            timestamp_ms: 0,
        }
    }

    pub fn with_sensors(x: f32, y: f32, pressure: f32, tilt_x: f32, tilt_y: f32, timestamp_ms: u32) -> Self {
        Self {
            x,
            y,
            pressure: pressure.clamp(0.0, 1.0),
            tilt_x,
            tilt_y,
            timestamp_ms,
        }
    }
}

/// A contiguous ink stroke formed by stylus/touch points
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct InkStroke {
    pub points: Vec<InkPoint>,
}

impl InkStroke {
    pub fn new(points: Vec<InkPoint>) -> Self {
        Self { points }
    }

    /// Evaluates bounding rectangle [min_x, min_y, max_x, max_y]
    pub fn bounds(&self) -> Option<[f32; 4]> {
        if self.points.is_empty() {
            return None;
        }
        let mut min_x = f32::MAX;
        let mut min_y = f32::MAX;
        let mut max_x = f32::MIN;
        let mut max_y = f32::MIN;

        for pt in &self.points {
            if pt.x < min_x { min_x = pt.x; }
            if pt.y < min_y { min_y = pt.y; }
            if pt.x > max_x { max_x = pt.x; }
            if pt.y > max_y { max_y = pt.y; }
        }
        Some([min_x, min_y, max_x, max_y])
    }

    /// Catmull-Rom spline interpolation producing smoothed curve samples
    pub fn smooth_catmull_rom(&self, subdivisions_per_segment: usize) -> Vec<InkPoint> {
        if self.points.len() < 3 {
            return self.points.clone();
        }

        let mut smoothed = Vec::with_capacity(self.points.len() * subdivisions_per_segment);
        smoothed.push(self.points[0]);

        for i in 0..self.points.len() - 1 {
            let p0 = if i > 0 { self.points[i - 1] } else { self.points[i] };
            let p1 = self.points[i];
            let p2 = self.points[i + 1];
            let p3 = if i + 2 < self.points.len() { self.points[i + 2] } else { p2 };

            for step in 1..=subdivisions_per_segment {
                let t = step as f32 / subdivisions_per_segment as f32;
                let t2 = t * t;
                let t3 = t2 * t;

                // Catmull-Rom basis matrix formulation (alpha = 0.5 centenary / standard)
                let x = 0.5 * ((2.0 * p1.x) +
                    (-p0.x + p2.x) * t +
                    (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 +
                    (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);

                let y = 0.5 * ((2.0 * p1.y) +
                    (-p0.y + p2.y) * t +
                    (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 +
                    (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);

                let pressure = p1.pressure + (p2.pressure - p1.pressure) * t;
                let tilt_x = p1.tilt_x + (p2.tilt_x - p1.tilt_x) * t;
                let tilt_y = p1.tilt_y + (p2.tilt_y - p1.tilt_y) * t;
                let timestamp_ms = (p1.timestamp_ms as f32 + (p2.timestamp_ms as f32 - p1.timestamp_ms as f32) * t) as u32;

                smoothed.push(InkPoint {
                    x,
                    y,
                    pressure,
                    tilt_x,
                    tilt_y,
                    timestamp_ms,
                });
            }
        }

        smoothed
    }
}

/// 8-coordinate quad representation conforming to ISO 32000 /QuadPoints:
/// [x1, y1, x2, y2, x3, y3, x4, y4] (top-left, top-right, bottom-left, bottom-right)
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub struct QuadPoints {
    pub coords: [f32; 8],
}

impl QuadPoints {
    pub fn from_rect(x: f32, y: f32, width: f32, height: f32) -> Self {
        Self {
            coords: [
                x, y + height,             // Top-left
                x + width, y + height,     // Top-right
                x, y,                      // Bottom-left
                x + width, y,              // Bottom-right
            ],
        }
    }

    pub fn bounds(&self) -> [f32; 4] {
        let min_x = self.coords[0].min(self.coords[2]).min(self.coords[4]).min(self.coords[6]);
        let max_x = self.coords[0].max(self.coords[2]).max(self.coords[4]).max(self.coords[6]);
        let min_y = self.coords[1].min(self.coords[3]).min(self.coords[5]).min(self.coords[7]);
        let max_y = self.coords[1].max(self.coords[3]).max(self.coords[5]).max(self.coords[7]);
        [min_x, min_y, max_x, max_y]
    }
}

/// Rectangular bounding box in standard PDF points
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub struct RectBox {
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
}

impl RectBox {
    pub fn new(x: f32, y: f32, width: f32, height: f32) -> Self {
        Self { x, y, width, height }
    }

    pub fn to_pdf_rect(&self) -> [f32; 4] {
        [self.x, self.y, self.x + self.width, self.y + self.height]
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Polygon {
    pub points: Vec<[f32; 2]>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Polyline {
    pub points: Vec<[f32; 2]>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct Cloud {
    pub polygon: Polygon,
    pub intensity: f32, // Bulge amplitude
}

/// Affine transformation matrix [a, b, c, d, e, f]
#[derive(Debug, Clone, Copy, Serialize, Deserialize, PartialEq)]
pub struct Transform {
    pub matrix: [f32; 6],
}

