use image::{Rgba, RgbaImage};
use crate::{AnnotKind, AnnotOp};

/// Overlay tile renderer compositing annotation graphics on a separate plane
/// Architecture Section 4.1: "annotations rasterize into a separate overlay tile layer composited above page tiles — annotation edits never invalidate the page tile cache."
pub struct OverlayRenderer;

impl OverlayRenderer {
    /// Renders all annotations for a page into an RGBA overlay buffer of given dimensions
    pub fn render_page_overlay(
        ops: &[&AnnotOp],
        page_width_pt: f32,
        page_height_pt: f32,
        target_width_px: u32,
        target_height_px: u32,
    ) -> RgbaImage {
        let mut img = RgbaImage::from_pixel(target_width_px, target_height_px, Rgba([0, 0, 0, 0]));

        let scale_x = target_width_px as f32 / page_width_pt;
        let scale_y = target_height_px as f32 / page_height_pt;

        for op in ops {
            if op.tombstone {
                continue;
            }

            let color = Rgba([
                (op.style.stroke_color[0] * 255.0) as u8,
                (op.style.stroke_color[1] * 255.0) as u8,
                (op.style.stroke_color[2] * 255.0) as u8,
                (op.style.stroke_color[3] * op.style.opacity * 255.0) as u8,
            ]);

            match &op.kind {
                AnnotKind::Ink(stroke) => {
                    let smoothed = stroke.smooth_catmull_rom(4);
                    for window in smoothed.windows(2) {
                        let p0 = &window[0];
                        let p1 = &window[1];

                        let x0 = (p0.x * scale_x) as i32;
                        let y0 = (p0.y * scale_y) as i32;
                        let x1 = (p1.x * scale_x) as i32;
                        let y1 = (p1.y * scale_y) as i32;

                        let stroke_px = ((op.style.stroke_width * scale_x * p1.pressure.max(0.2)) as i32).max(1);
                        Self::draw_line_thick(&mut img, x0, y0, x1, y1, stroke_px, color);
                    }
                }
                AnnotKind::Highlight(quads) => {
                    let highlight_color = Rgba([
                        (op.style.stroke_color[0] * 255.0) as u8,
                        (op.style.stroke_color[1] * 255.0) as u8,
                        (op.style.stroke_color[2] * 255.0) as u8,
                        ((op.style.opacity * 0.4).clamp(0.1, 1.0) * 255.0) as u8, // 40% translucent blend
                    ]);

                    for quad in quads {
                        let b = quad.bounds();
                        let min_x = (b[0] * scale_x).max(0.0) as u32;
                        let max_x = (b[2] * scale_x).min(target_width_px as f32) as u32;
                        let min_y = (b[1] * scale_y).max(0.0) as u32;
                        let max_y = (b[3] * scale_y).min(target_height_px as f32) as u32;

                        for y in min_y..max_y {
                            for x in min_x..max_x {
                                img.put_pixel(x, y, highlight_color);
                            }
                        }
                    }
                }
                AnnotKind::Square(r) => {
                    let min_x = (r.x * scale_x) as u32;
                    let max_x = ((r.x + r.width) * scale_x) as u32;
                    let min_y = (r.y * scale_y) as u32;
                    let max_y = ((r.y + r.height) * scale_y) as u32;

                    let thickness = (op.style.stroke_width * scale_x) as u32;
                    for x in min_x..max_x {
                        for t in 0..thickness {
                            if min_y + t < target_height_px && x < target_width_px {
                                img.put_pixel(x, min_y + t, color);
                            }
                            if max_y > t && max_y - 1 - t < target_height_px && x < target_width_px {
                                img.put_pixel(x, max_y - 1 - t, color);
                            }
                        }
                    }
                    for y in min_y..max_y {
                        for t in 0..thickness {
                            if min_x + t < target_width_px && y < target_height_px {
                                img.put_pixel(min_x + t, y, color);
                            }
                            if max_x > t && max_x - 1 - t < target_width_px && y < target_height_px {
                                img.put_pixel(max_x - 1 - t, y, color);
                            }
                        }
                    }
                }
                AnnotKind::Redaction(r) => {
                    let min_x = ((r.bounds.x / page_width_pt) * target_width_px as f32).max(0.0) as u32;
                    let min_y = ((r.bounds.y / page_height_pt) * target_height_px as f32).max(0.0) as u32;
                    let max_x = (((r.bounds.x + r.bounds.width) / page_width_pt) * target_width_px as f32).min(target_width_px as f32) as u32;
                    let max_y = (((r.bounds.y + r.bounds.height) / page_height_pt) * target_height_px as f32).min(target_height_px as f32) as u32;
                    let black = Rgba([0, 0, 0, 255]);
                    for y in min_y..max_y {
                        for x in min_x..max_x {
                            img.put_pixel(x, y, black);
                        }
                    }
                }
                AnnotKind::Signature(s) => {
                    let min_x = ((s.bounds.x / page_width_pt) * target_width_px as f32).max(0.0) as u32;
                    let min_y = ((s.bounds.y / page_height_pt) * target_height_px as f32).max(0.0) as u32;
                    let max_x = (((s.bounds.x + s.bounds.width) / page_width_pt) * target_width_px as f32).min(target_width_px as f32) as u32;
                    let max_y = (((s.bounds.y + s.bounds.height) / page_height_pt) * target_height_px as f32).min(target_height_px as f32) as u32;
                    let sig_color = Rgba([14, 165, 233, 255]);
                    for x in min_x..max_x {
                        if min_y < target_height_px { img.put_pixel(x, min_y, sig_color); }
                        if max_y > 0 && max_y - 1 < target_height_px { img.put_pixel(x, max_y - 1, sig_color); }
                    }
                    for y in min_y..max_y {
                        if min_x < target_width_px { img.put_pixel(min_x, y, sig_color); }
                        if max_x > 0 && max_x - 1 < target_width_px { img.put_pixel(max_x - 1, y, sig_color); }
                    }
                }
                AnnotKind::Circle(_) | AnnotKind::Line { .. } | AnnotKind::TextNote { .. } => {
                    // Supported in export & overlay vector pass
                }
                _ => {
                    // Supported in export & overlay vector pass
                }
            }
        }

        img
    }

    fn draw_line_thick(img: &mut RgbaImage, mut x0: i32, mut y0: i32, x1: i32, y1: i32, thickness: i32, color: Rgba<u8>) {
        let dx = (x1 - x0).abs();
        let dy = -(y1 - y0).abs();
        let sx = if x0 < x1 { 1 } else { -1 };
        let sy = if y0 < y1 { 1 } else { -1 };
        let mut err = dx + dy;
        let radius = thickness / 2;

        let width = img.width() as i32;
        let height = img.height() as i32;

        loop {
            // Plot circular disc for thick stroke
            for ox in -radius..=radius {
                for oy in -radius..=radius {
                    if ox * ox + oy * oy <= radius * radius {
                        let px = x0 + ox;
                        let py = y0 + oy;
                        if px >= 0 && px < width && py >= 0 && py < height {
                            img.put_pixel(px as u32, py as u32, color);
                        }
                    }
                }
            }

            if x0 == x1 && y0 == y1 { break; }
            let e2 = 2 * err;
            if e2 >= dy {
                err += dy;
                x0 += sx;
            }
            if e2 <= dx {
                err += dx;
                y0 += sy;
            }
        }
    }
}
