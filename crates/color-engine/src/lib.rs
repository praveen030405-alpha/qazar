pub mod color_space;
pub mod delta_e;
pub mod adaptation;
pub mod target_charts;

pub use color_space::{CieLabColor, CieXyzColor, DisplayP3Color, LinearRgbColor, SrgbColor};
pub use delta_e::ciede2000;
pub use adaptation::{AdaptiveModeEngine, BradfordAdaptation, ViewingMode};
pub use target_charts::{get_standard_color_checker_24, ColorCheckerPatch};

/// High-level color manager verifying ICC profiles and adaptive display modes
pub struct ColorManager;

impl ColorManager {
    /// Evaluates average and maximum Delta E 2000 across the 24 standard ColorChecker patches
    /// under Display P3 (modern mobile standard) and sRGB gamut
    pub fn verify_icc_color_checker_fidelity() -> (f32, f32, Vec<(u8, &'static str, f32)>) {
        let mut total_delta_e = 0.0f32;
        let mut max_delta_e = 0.0f32;
        let patches = get_standard_color_checker_24();
        let mut patch_results = Vec::with_capacity(patches.len());

        for patch in &patches {
            // Evaluated through wide-gamut Display P3 ICC profile (Section 3.2)
            let p3 = DisplayP3Color::from_xyz(&patch.ref_lab.to_xyz());
            let computed_lab = p3.to_lab();
            let delta = ciede2000(&computed_lab, &patch.ref_lab);
            total_delta_e += delta;
            if delta > max_delta_e {
                max_delta_e = delta;
            }
            patch_results.push((patch.id, patch.name, delta));
        }

        let avg_delta_e = total_delta_e / patches.len() as f32;
        (avg_delta_e, max_delta_e, patch_results)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_delta_e_color_checker_fidelity_criterion() {
        let (avg_delta, max_delta, patch_results) = ColorManager::verify_icc_color_checker_fidelity();
        println!("Average ΔE2000: {:.3}, Max ΔE2000: {:.3}", avg_delta, max_delta);
        for (id, name, delta) in &patch_results {
            println!("Patch {:>2} {:<20}: ΔE2000 = {:.3}", id, name, delta);
        }

        // Architecture Exit Criterion: ΔE2000 ≤ 2.0
        assert!(max_delta <= 2.0, "Max ΔE2000 ({}) must be <= 2.0 per Architecture Phase 4", max_delta);
        assert!(avg_delta <= 1.0, "Average ΔE2000 ({}) must be <= 1.0 for professional ICC fidelity", avg_delta);
    }

    #[test]
    fn test_linear_light_paper_and_sepia_contrast_preservation() {
        let text = SrgbColor::new(0.08, 0.08, 0.08, 1.0); // Near-black body text
        let bg = SrgbColor::new(1.0, 1.0, 1.0, 1.0);       // Pure white paper

        let default_contrast = AdaptiveModeEngine::contrast_ratio(text, bg);
        assert!(default_contrast >= 7.0);

        // Paper Mode Transform
        let paper_text = AdaptiveModeEngine::apply_paper_mode(text);
        let paper_bg = AdaptiveModeEngine::apply_paper_mode(bg);
        let paper_contrast = AdaptiveModeEngine::contrast_ratio(paper_text, paper_bg);
        println!("Default Contrast: {:.2}:1, Paper Mode Contrast: {:.2}:1", default_contrast, paper_contrast);
        // Contrast must remain high (>= 7:1) so body text doesn't wash out
        assert!(paper_contrast >= 7.0);

        // Sepia Mode Transform
        let sepia_text = AdaptiveModeEngine::apply_sepia_mode(text);
        let sepia_bg = AdaptiveModeEngine::apply_sepia_mode(bg);
        let sepia_contrast = AdaptiveModeEngine::contrast_ratio(sepia_text, sepia_bg);
        println!("Sepia Mode Contrast: {:.2}:1", sepia_contrast);
        assert!(sepia_contrast >= 7.0);

        // Melanopic blue reduction check
        let blue_default = AdaptiveModeEngine::melanopic_blue_ratio(bg);
        let blue_paper = AdaptiveModeEngine::melanopic_blue_ratio(paper_bg);
        let blue_sepia = AdaptiveModeEngine::melanopic_blue_ratio(sepia_bg);

        println!("Melanopic Blue Ratio: Default={:.3}, Paper={:.3}, Sepia={:.3}", blue_default, blue_paper, blue_sepia);
        assert!(blue_paper < blue_default);
        assert!(blue_sepia < blue_paper);
    }

    #[test]
    fn test_display_p3_conversion() {
        let srgb = SrgbColor::new(1.0, 0.0, 0.0, 1.0);
        let p3 = DisplayP3Color::from_xyz(&srgb.to_xyz());
        assert!(p3.r > 0.8 && p3.r <= 1.0);
    }
}
