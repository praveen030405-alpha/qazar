use crate::color_space::CieLabColor;

/// Evaluates perceptual color difference between two colors in CIE L*a*b* using CIEDE2000
/// Reference: ISO/CIE 11664-6:2014 (Colorimetry — Part 6: CIEDE2000 Colour-Difference Formula)
pub fn ciede2000(c1: &CieLabColor, c2: &CieLabColor) -> f32 {
    let k_l = 1.0f32;
    let k_c = 1.0f32;
    let k_h = 1.0f32;

    let c_star_1 = (c1.a * c1.a + c1.b * c1.b).sqrt();
    let c_star_2 = (c2.a * c2.a + c2.b * c2.b).sqrt();
    let c_bar = (c_star_1 + c_star_2) / 2.0;

    let c_bar_7 = c_bar.powi(7);
    let g = 0.5 * (1.0 - (c_bar_7 / (c_bar_7 + 25.0f32.powi(7))).sqrt());

    let a_prime_1 = (1.0 + g) * c1.a;
    let a_prime_2 = (1.0 + g) * c2.a;

    let c_prime_1 = (a_prime_1 * a_prime_1 + c1.b * c1.b).sqrt();
    let c_prime_2 = (a_prime_2 * a_prime_2 + c2.b * c2.b).sqrt();

    let h_prime_1 = compute_hue_angle(a_prime_1, c1.b);
    let h_prime_2 = compute_hue_angle(a_prime_2, c2.b);

    let delta_l_prime = c2.l - c1.l;
    let delta_c_prime = c_prime_2 - c_prime_1;

    let delta_h_prime = if c_prime_1 * c_prime_2 == 0.0 {
        0.0
    } else if (h_prime_2 - h_prime_1).abs() <= 180.0 {
        h_prime_2 - h_prime_1
    } else if h_prime_2 - h_prime_1 > 180.0 {
        (h_prime_2 - h_prime_1) - 360.0
    } else {
        (h_prime_2 - h_prime_1) + 360.0
    };

    let delta_big_h_prime = 2.0 * (c_prime_1 * c_prime_2).sqrt() * ((delta_h_prime / 2.0).to_radians()).sin();

    let l_bar_prime = (c1.l + c2.l) / 2.0;
    let c_bar_prime = (c_prime_1 + c_prime_2) / 2.0;

    let h_bar_prime = if c_prime_1 * c_prime_2 == 0.0 {
        h_prime_1 + h_prime_2
    } else if (h_prime_1 - h_prime_2).abs() <= 180.0 {
        (h_prime_1 + h_prime_2) / 2.0
    } else if h_prime_1 + h_prime_2 < 360.0 {
        (h_prime_1 + h_prime_2 + 360.0) / 2.0
    } else {
        (h_prime_1 + h_prime_2 - 360.0) / 2.0
    };

    let t = 1.0 - 0.17 * ((h_bar_prime - 30.0).to_radians()).cos()
        + 0.24 * ((2.0 * h_bar_prime).to_radians()).cos()
        + 0.32 * ((3.0 * h_bar_prime + 6.0).to_radians()).cos()
        - 0.20 * ((4.0 * h_bar_prime - 63.0).to_radians()).cos();

    let delta_theta = 30.0 * (-((h_bar_prime - 275.0) / 25.0).powi(2)).exp();
    let c_bar_prime_7 = c_bar_prime.powi(7);
    let r_c = 2.0 * (c_bar_prime_7 / (c_bar_prime_7 + 25.0f32.powi(7))).sqrt();

    let l_bar_50_sq = (l_bar_prime - 50.0).powi(2);
    let s_l = 1.0 + (0.015 * l_bar_50_sq) / (20.0 + l_bar_50_sq).sqrt();
    let s_c = 1.0 + 0.045 * c_bar_prime;
    let s_h = 1.0 + 0.015 * c_bar_prime * t;

    let r_t = -r_c * ((2.0 * delta_theta).to_radians()).sin();

    let term_l = delta_l_prime / (k_l * s_l);
    let term_c = delta_c_prime / (k_c * s_c);
    let term_h = delta_big_h_prime / (k_h * s_h);

    (term_l * term_l + term_c * term_c + term_h * term_h + r_t * term_c * term_h).sqrt()
}

fn compute_hue_angle(a: f32, b: f32) -> f32 {
    if a == 0.0 && b == 0.0 {
        return 0.0;
    }
    let angle = b.atan2(a).to_degrees();
    if angle >= 0.0 {
        angle
    } else {
        angle + 360.0
    }
}
