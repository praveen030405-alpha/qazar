use crate::color_space::{CieLabColor, SrgbColor};

/// Standard 24-patch Macbeth ColorChecker reference chart data
/// (ISO 17321-1 reference specifications under D65 standard illuminant)
#[derive(Debug, Clone)]
pub struct ColorCheckerPatch {
    pub id: u8,
    pub name: &'static str,
    pub ref_srgb: SrgbColor,
    pub ref_lab: CieLabColor,
}

impl ColorCheckerPatch {
    pub fn new(id: u8, name: &'static str, l: f32, a: f32, b: f32) -> Self {
        let ref_lab = CieLabColor::new(l, a, b);
        let ref_srgb = ref_lab.to_srgb();
        Self {
            id,
            name,
            ref_srgb,
            ref_lab,
        }
    }
}

pub fn get_standard_color_checker_24() -> Vec<ColorCheckerPatch> {
    vec![
        ColorCheckerPatch::new(1,  "Dark Skin",          37.986, 13.555,  14.059),
        ColorCheckerPatch::new(2,  "Light Skin",         65.711, 18.130,  17.810),
        ColorCheckerPatch::new(3,  "Blue Sky",           49.927, -4.878,  -21.925),
        ColorCheckerPatch::new(4,  "Foliage",            43.139, -13.095, 21.905),
        ColorCheckerPatch::new(5,  "Blue Flower",        55.112, 8.844,   -25.399),
        ColorCheckerPatch::new(6,  "Bluish Green",       70.719, -33.397, -0.199),
        ColorCheckerPatch::new(7,  "Orange",             62.661, 36.067,  57.096),
        ColorCheckerPatch::new(8,  "Purplish Blue",      40.020, 10.410,  -45.964),
        ColorCheckerPatch::new(9,  "Moderate Red",       51.124, 48.239,  16.248),
        ColorCheckerPatch::new(10, "Purple",             30.325, 22.976,  -21.587),
        ColorCheckerPatch::new(11, "Yellow Green",       72.532, -23.709, 57.255),
        ColorCheckerPatch::new(12, "Orange Yellow",      71.941, 19.363,  67.857),
        ColorCheckerPatch::new(13, "Blue",               28.778, 14.179,  -50.297),
        ColorCheckerPatch::new(14, "Green",              55.261, -38.342, 31.370),
        ColorCheckerPatch::new(15, "Red",                42.101, 53.378,  28.190),
        ColorCheckerPatch::new(16, "Yellow",             81.733, 4.039,   79.819),
        ColorCheckerPatch::new(17, "Magenta",            51.935, 49.986,  -14.574),
        ColorCheckerPatch::new(18, "Cyan",               51.038, -28.631, -28.638),
        ColorCheckerPatch::new(19, "White 9.5 (.05 D)",  96.539, -0.425,  1.186),
        ColorCheckerPatch::new(20, "Neutral 8 (.23 D)",  81.257, -0.638,  -0.335),
        ColorCheckerPatch::new(21, "Neutral 6.5 (.44 D)",66.766, -0.734,  -0.504),
        ColorCheckerPatch::new(22, "Neutral 5 (.70 D)",  50.867, -0.153,  -0.270),
        ColorCheckerPatch::new(23, "Neutral 3.5 (1.05 D)",35.656, -0.421, -1.231),
        ColorCheckerPatch::new(24, "Black 2 (1.5 D)",    20.461, -0.079,  -0.973),
    ]
}
