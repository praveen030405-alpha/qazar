//! Types crossing the PDF kernel boundary.

/// Width and height of a PDF page in points (1 point = 1/72 inch).
#[derive(Debug, Clone, Copy)]
pub struct PageDimensions {
    pub width_points: f64,
    pub height_points: f64,
}

impl PageDimensions {
    /// Convert point dimensions to pixel dimensions at the given DPI.
    pub fn to_pixels(&self, dpi: f32) -> (u32, u32) {
        let scale = dpi as f64 / 72.0;
        let w = (self.width_points * scale).ceil() as u32;
        let h = (self.height_points * scale).ceil() as u32;
        (w, h)
    }
}

/// A raw RGBA8 bitmap buffer — the universal interchange format
/// between the PDF kernel and the tile engine / compositor.
#[derive(Debug, Clone)]
pub struct RgbaBuffer {
    pub width: u32,
    pub height: u32,
    /// Row-major, 4 bytes per pixel (R, G, B, A).
    pub data: Vec<u8>,
}

impl RgbaBuffer {
    pub fn new(width: u32, height: u32) -> Self {
        let len = (width as usize) * (height as usize) * 4;
        Self {
            width,
            height,
            data: vec![255u8; len], // opaque white
        }
    }

    /// Total byte size of the pixel data.
    pub fn byte_size(&self) -> usize {
        self.data.len()
    }
}

/// Metadata extracted from a document after parsing.
#[derive(Debug, Clone)]
pub struct DocumentInfo {
    pub page_count: usize,
    pub title: Option<String>,
    pub author: Option<String>,
}

/// Bounding box of a single glyph or character on a page in PDF points.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct GlyphQuad {
    pub left: f64,
    pub top: f64,
    pub right: f64,
    pub bottom: f64,
}

/// Positioned text character extracted from a page.
#[derive(Debug, Clone, PartialEq)]
pub struct TextChar {
    pub ch: char,
    pub bounds: GlyphQuad,
}

/// Positioned text segment or line on a page.
#[derive(Debug, Clone)]
pub struct TextSpan {
    pub text: String,
    pub bounds: GlyphQuad,
    pub char_indices: (usize, usize),
}

/// Extracted positioned text for an entire page.
#[derive(Debug, Clone)]
pub struct PageText {
    pub page_index: usize,
    pub text: String,
    pub chars: Vec<TextChar>,
}
