use serde::{Deserialize, Serialize};

/// Standard sRGB color in gamma-encoded space [0.0, 1.0]
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct SrgbColor {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

impl SrgbColor {
    pub fn new(r: f32, g: f32, b: f32, a: f32) -> Self {
        Self {
            r: r.clamp(0.0, 1.0),
            g: g.clamp(0.0, 1.0),
            b: b.clamp(0.0, 1.0),
            a: a.clamp(0.0, 1.0),
        }
    }

    pub fn from_u8(r: u8, g: u8, b: u8, a: u8) -> Self {
        Self {
            r: r as f32 / 255.0,
            g: g as f32 / 255.0,
            b: b as f32 / 255.0,
            a: a as f32 / 255.0,
        }
    }

    pub fn to_u8_array(&self) -> [u8; 4] {
        [
            (self.r * 255.0).round() as u8,
            (self.g * 255.0).round() as u8,
            (self.b * 255.0).round() as u8,
            (self.a * 255.0).round() as u8,
        ]
    }

    /// Converts non-linear sRGB to extended-linear working space (IEC 61966-2-1 transfer curve)
    pub fn to_linear(&self) -> LinearRgbColor {
        LinearRgbColor {
            r: srgb_to_linear(self.r),
            g: srgb_to_linear(self.g),
            b: srgb_to_linear(self.b),
            a: self.a,
        }
    }

    /// Converts directly to CIE 1931 XYZ (D65 standard illuminant)
    pub fn to_xyz(&self) -> CieXyzColor {
        self.to_linear().to_xyz()
    }

    /// Converts directly to CIE L*a*b*
    pub fn to_lab(&self) -> CieLabColor {
        self.to_xyz().to_lab()
    }
}

/// Extended-linear RGB working color space [0.0, 1.0]
/// Architecture Section 3.2: "render in extended-linear working space; output to the actual display profile"
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct LinearRgbColor {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

impl LinearRgbColor {
    pub fn new(r: f32, g: f32, b: f32, a: f32) -> Self {
        Self { r, g, b, a }
    }

    /// Converts linear RGB back to gamma-encoded sRGB
    pub fn to_srgb(&self) -> SrgbColor {
        SrgbColor {
            r: linear_to_srgb(self.r),
            g: linear_to_srgb(self.g),
            b: linear_to_srgb(self.b),
            a: self.a,
        }
    }

    /// Evaluates linear relative luminance Y (Rec. 709 / sRGB primaries)
    pub fn relative_luminance(&self) -> f32 {
        0.2126 * self.r + 0.7152 * self.g + 0.0722 * self.b
    }

    /// Converts linear RGB to CIE XYZ (D65) via standard sRGB transformation matrix
    pub fn to_xyz(&self) -> CieXyzColor {
        let x = 0.4124564 * self.r + 0.3575761 * self.g + 0.1804375 * self.b;
        let y = 0.2126729 * self.r + 0.7151522 * self.g + 0.0721750 * self.b;
        let z = 0.0193339 * self.r + 0.1191920 * self.g + 0.9503041 * self.b;
        CieXyzColor { x, y, z, a: self.a }
    }
}

/// CIE 1931 XYZ Tristimulus color space
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct CieXyzColor {
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub a: f32,
}

impl CieXyzColor {
    /// Standard CIE D65 2-degree reference white point
    pub const D65_WHITE: [f32; 3] = [0.95047, 1.00000, 1.08883];

    /// Converts CIE XYZ to CIE L*a*b* (CIE 15:2004)
    pub fn to_lab(&self) -> CieLabColor {
        let x_n = Self::D65_WHITE[0];
        let y_n = Self::D65_WHITE[1];
        let z_n = Self::D65_WHITE[2];

        let fx = f_lab(self.x / x_n);
        let fy = f_lab(self.y / y_n);
        let fz = f_lab(self.z / z_n);

        let l = (116.0 * fy) - 16.0;
        let a = 500.0 * (fx - fy);
        let b = 200.0 * (fy - fz);

        CieLabColor { l, a, b, alpha: self.a }
    }

    /// Converts CIE XYZ back to Linear RGB
    pub fn to_linear(&self) -> LinearRgbColor {
        let r =  3.2404542 * self.x - 1.5371385 * self.y - 0.4985314 * self.z;
        let g = -0.9692660 * self.x + 1.8760108 * self.y + 0.0415560 * self.z;
        let b =  0.0556434 * self.x - 0.2040259 * self.y + 1.0572252 * self.z;
        LinearRgbColor { r, g, b, a: self.a }
    }
}

/// CIE L*a*b* Perceptually Uniform Color Space
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct CieLabColor {
    pub l: f32, // Lightness [0.0, 100.0]
    pub a: f32, // Red-Green [-128.0, 127.0]
    pub b: f32, // Yellow-Blue [-128.0, 127.0]
    pub alpha: f32,
}

impl CieLabColor {
    pub fn new(l: f32, a: f32, b: f32) -> Self {
        Self { l, a, b, alpha: 1.0 }
    }

    pub fn from_xyz(xyz: &CieXyzColor) -> Self {
        xyz.to_lab()
    }

    /// Converts CIE L*a*b* back to CIE XYZ (D65)
    pub fn to_xyz(&self) -> CieXyzColor {
        let x_n = CieXyzColor::D65_WHITE[0];
        let y_n = CieXyzColor::D65_WHITE[1];
        let z_n = CieXyzColor::D65_WHITE[2];

        let fy = (self.l + 16.0) / 116.0;
        let fx = (self.a / 500.0) + fy;
        let fz = fy - (self.b / 200.0);

        let delta: f32 = 6.0 / 29.0;
        let delta_sq_3 = 3.0 * delta * delta;

        let x = if fx > delta { fx.powi(3) } else { (fx - 16.0 / 116.0) * delta_sq_3 } * x_n;
        let y = if fy > delta { fy.powi(3) } else { (fy - 16.0 / 116.0) * delta_sq_3 } * y_n;
        let z = if fz > delta { fz.powi(3) } else { (fz - 16.0 / 116.0) * delta_sq_3 } * z_n;

        CieXyzColor { x, y, z, a: self.alpha }
    }

    /// Converts CIE L*a*b* to sRGB via XYZ and Linear RGB with gamut mapping
    pub fn to_srgb(&self) -> SrgbColor {
        let lin = self.to_xyz().to_linear();
        // If within sRGB gamut, return directly
        if lin.r >= 0.0 && lin.r <= 1.0 && lin.g >= 0.0 && lin.g <= 1.0 && lin.b >= 0.0 && lin.b <= 1.0 {
            return lin.to_srgb();
        }

        // Relative colorimetric gamut mapping: scale chroma along constant hue
        let c_star = (self.a * self.a + self.b * self.b).sqrt();
        if c_star < 1e-4 {
            return lin.to_srgb();
        }

        let mut low = 0.0f32;
        let mut high = 1.0f32;
        let mut best_srgb = lin.to_srgb();

        for _ in 0..12 {
            let mid = (low + high) * 0.5;
            let test_lab = CieLabColor::new(self.l, self.a * mid, self.b * mid);
            let test_lin = test_lab.to_xyz().to_linear();
            if test_lin.r >= -0.001 && test_lin.r <= 1.001 
                && test_lin.g >= -0.001 && test_lin.g <= 1.001 
                && test_lin.b >= -0.001 && test_lin.b <= 1.001 {
                best_srgb = test_lin.to_srgb();
                low = mid;
            } else {
                high = mid;
            }
        }
        best_srgb
    }
}

/// Display P3 Color (Wide gamut standard on modern mobile displays)
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct DisplayP3Color {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

impl DisplayP3Color {
    pub fn new(r: f32, g: f32, b: f32, a: f32) -> Self {
        Self { r, g, b, a }
    }

    /// Converts CIE XYZ (D65) to Display P3 with sRGB transfer curve
    pub fn from_xyz(xyz: &CieXyzColor) -> Self {
        let r_lin =  2.4934969 * xyz.x - 0.9313836 * xyz.y - 0.4027108 * xyz.z;
        let g_lin = -0.8294890 * xyz.x + 1.7626641 * xyz.y + 0.0236247 * xyz.z;
        let b_lin =  0.0358458 * xyz.x - 0.0761724 * xyz.y + 0.9568845 * xyz.z;

        Self {
            r: linear_to_srgb(r_lin),
            g: linear_to_srgb(g_lin),
            b: linear_to_srgb(b_lin),
            a: xyz.a,
        }
    }

    /// Converts Display P3 back to CIE XYZ (D65)
    pub fn to_xyz(&self) -> CieXyzColor {
        let r_lin = srgb_to_linear(self.r);
        let g_lin = srgb_to_linear(self.g);
        let b_lin = srgb_to_linear(self.b);

        let x = 0.4865709 * r_lin + 0.2656677 * g_lin + 0.1982173 * b_lin;
        let y = 0.2289746 * r_lin + 0.6917394 * g_lin + 0.0792860 * b_lin;
        let z = 0.0000000 * r_lin + 0.0451134 * g_lin + 1.0439444 * b_lin;

        CieXyzColor { x, y, z, a: self.a }
    }

    /// Converts Display P3 to CIE L*a*b*
    pub fn to_lab(&self) -> CieLabColor {
        self.to_xyz().to_lab()
    }
}

// ── Math Helpers ───────────────────────────────────────────────────────

#[inline]
fn srgb_to_linear(c: f32) -> f32 {
    if c <= 0.04045 {
        c / 12.92
    } else {
        ((c + 0.055) / 1.055).powf(2.4)
    }
}

#[inline]
fn linear_to_srgb(c: f32) -> f32 {
    let clamped = c.clamp(0.0, 1.0);
    if clamped <= 0.0031308 {
        clamped * 12.92
    } else {
        1.055 * clamped.powf(1.0 / 2.4) - 0.055
    }
}

#[inline]
fn f_lab(t: f32) -> f32 {
    let delta: f32 = 6.0 / 29.0;
    if t > delta * delta * delta {
        t.cbrt()
    } else {
        t / (3.0 * delta * delta) + (4.0 / 29.0)
    }
}
