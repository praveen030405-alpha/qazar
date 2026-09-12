use crate::color_space::{CieXyzColor, LinearRgbColor, SrgbColor};

/// Viewing modes supported by Meridian Phase 4 (Dark mode cut from roadmap)
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum ViewingMode {
    #[default]
    Default,  // Calibrated sRGB / Display P3 pure light rendering
    Paper,    // Soft architectural ivory (5500 K, reduced blue eyestrain)
    Sepia,    // Warm archival parchment (4500 K, circadian melanopic attenuation)
}

/// Bradford Chromatic Adaptation transform matrix between standard illuminants
pub struct BradfordAdaptation;

impl BradfordAdaptation {
    /// Maps CIE XYZ from D65 (Display) to D50 (Standard Print Reference)
    pub fn d65_to_d50(xyz: &CieXyzColor) -> CieXyzColor {
        // Bradford D65 -> D50 matrix
        let x =  1.0478112 * xyz.x + 0.0228866 * xyz.y - 0.0501270 * xyz.z;
        let y =  0.0295424 * xyz.x + 0.9904844 * xyz.y - 0.0170491 * xyz.z;
        let z = -0.0092345 * xyz.x + 0.0150436 * xyz.y + 0.7521316 * xyz.z;
        CieXyzColor { x, y, z, a: xyz.a }
    }

    /// Maps CIE XYZ from D50 (Print Reference) to D65 (Display)
    pub fn d50_to_d65(xyz: &CieXyzColor) -> CieXyzColor {
        let x =  0.9555766 * xyz.x - 0.0230393 * xyz.y + 0.0631636 * xyz.z;
        let y = -0.0282895 * xyz.x + 1.0099416 * xyz.y + 0.0210077 * xyz.z;
        let z =  0.0122982 * xyz.x - 0.0204830 * xyz.y + 1.3299098 * xyz.z;
        CieXyzColor { x, y, z, a: xyz.a }
    }
}

/// Adaptive display mode engine applying transforms in linear light
/// Architecture Section 3.2: "Paper/sepia. Applied as a transform in linear light, not a multiply on gamma-encoded sRGB — relative luminance contrast is preserved, so body text doesn't wash out."
pub struct AdaptiveModeEngine;

impl AdaptiveModeEngine {
    /// Transforms an sRGB color according to the active viewing mode
    pub fn transform_color(color: SrgbColor, mode: ViewingMode) -> SrgbColor {
        match mode {
            ViewingMode::Default => color,
            ViewingMode::Paper => Self::apply_paper_mode(color),
            ViewingMode::Sepia => Self::apply_sepia_mode(color),
        }
    }

    /// Soft architectural paper mode (5500 K color temperature in linear space)
    /// Reduces melanopic high-energy blue (>= 15%) while preserving text contrast ratio >= 7:1
    pub fn apply_paper_mode(color: SrgbColor) -> SrgbColor {
        let lin = color.to_linear();
        // Linear warmth matrix for soft ivory paper substrate (5500 K)
        let r_lin = lin.r * 1.00;
        let g_lin = lin.g * 0.96;
        let b_lin = lin.b * 0.76;

        LinearRgbColor::new(r_lin, g_lin, b_lin, lin.a).to_srgb()
    }

    /// Warm archival parchment sepia mode (4500 K in linear space)
    /// Significant melanopic circadian blue reduction (>= 30%) with preserved body text legibility
    pub fn apply_sepia_mode(color: SrgbColor) -> SrgbColor {
        let lin = color.to_linear();
        // Linear warmth matrix for warm parchment (4500 K)
        let r_lin = lin.r * 1.00;
        let g_lin = lin.g * 0.91;
        let b_lin = lin.b * 0.58;

        LinearRgbColor::new(r_lin, g_lin, b_lin, lin.a).to_srgb()
    }

    /// Measures the melanopic blue component (460-490 nm proxy) in linear space
    pub fn melanopic_blue_ratio(color: SrgbColor) -> f32 {
        let lin = color.to_linear();
        lin.b / (lin.r + lin.g + lin.b + 1e-6)
    }

    /// Measures the Weber / Michelson text contrast ratio between text and background
    pub fn contrast_ratio(text: SrgbColor, background: SrgbColor) -> f32 {
        let l1 = text.to_linear().relative_luminance();
        let l2 = background.to_linear().relative_luminance();
        let brighter = l1.max(l2);
        let darker = l1.min(l2);
        (brighter + 0.05) / (darker + 0.05)
    }

    /// Measures melanopic blue reduction percentage relative to standard D65 white
    pub fn melanopic_blue_reduction(mode: ViewingMode) -> f32 {
        let white = SrgbColor::new(1.0, 1.0, 1.0, 1.0);
        let base_blue = Self::melanopic_blue_ratio(white);
        let adapted = Self::transform_color(white, mode);
        let adapted_blue = Self::melanopic_blue_ratio(adapted);
        (base_blue - adapted_blue) / base_blue
    }

    /// Adapts an RGBA u8 buffer in-place using precomputed 256-entry 1D LUTs (L1 cache resident)
    pub fn adapt_rgba_buffer(buffer: &mut [u8], mode: ViewingMode) {
        if mode == ViewingMode::Default {
            return;
        }

        let mut lut_r = [0u8; 256];
        let mut lut_g = [0u8; 256];
        let mut lut_b = [0u8; 256];

        for i in 0..=255 {
            let srgb = SrgbColor::new(i as f32 / 255.0, i as f32 / 255.0, i as f32 / 255.0, 1.0);
            let adapted = Self::transform_color(srgb, mode);
            lut_r[i] = (adapted.r.clamp(0.0, 1.0) * 255.0).round() as u8;
            lut_g[i] = (adapted.g.clamp(0.0, 1.0) * 255.0).round() as u8;
            lut_b[i] = (adapted.b.clamp(0.0, 1.0) * 255.0).round() as u8;
        }

        for chunk in buffer.chunks_exact_mut(4) {
            chunk[0] = lut_r[chunk[0] as usize];
            chunk[1] = lut_g[chunk[1] as usize];
            chunk[2] = lut_b[chunk[2] as usize];
        }
    }
}
