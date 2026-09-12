//! Text shaping, Unicode bidi classification, and CJK/RTL analysis.
//!
//! Architecture ref (Section 2, line 45): deterministic rustybuzz + ICU4X
//! stack so selection rectangles, search hit-quads, and advances are
//! byte-for-byte identical across all platforms.

use icu::properties::{maps, BidiClass, Script};
use crate::error::{Result, TextEngineError};

/// Text reading and writing direction.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TextDirection {
    LeftToRight,
    RightToLeft,
}

/// Unicode Bidi classifier backed by ICU4X property maps.
pub struct BidiClassifier {
    _private: (),
}

impl Default for BidiClassifier {
    fn default() -> Self {
        Self::new()
    }
}

impl BidiClassifier {
    pub fn new() -> Self {
        Self { _private: () }
    }

    /// Check if character is a Right-To-Left character (Hebrew, Arabic, etc.).
    pub fn is_rtl(&self, ch: char) -> bool {
        let bidi_map = maps::bidi_class();
        let class = bidi_map.get(ch);
        matches!(
            class,
            BidiClass::RightToLeft
                | BidiClass::ArabicLetter
                | BidiClass::ArabicNumber
                | BidiClass::RightToLeftEmbedding
                | BidiClass::RightToLeftOverride
                | BidiClass::RightToLeftIsolate
        )
    }

    /// Detect primary direction of a string slice.
    pub fn detect_direction(&self, text: &str) -> TextDirection {
        let mut rtl_count = 0usize;
        let mut ltr_count = 0usize;

        for ch in text.chars() {
            if ch.is_whitespace() || ch.is_ascii_punctuation() {
                continue;
            }
            if self.is_rtl(ch) {
                rtl_count += 1;
            } else {
                ltr_count += 1;
            }
        }

        if rtl_count > ltr_count {
            TextDirection::RightToLeft
        } else {
            TextDirection::LeftToRight
        }
    }
}

/// Script classifier backed by ICU4X script properties.
pub struct ScriptClassifier {
    _private: (),
}

impl Default for ScriptClassifier {
    fn default() -> Self {
        Self::new()
    }
}

impl ScriptClassifier {
    pub fn new() -> Self {
        Self { _private: () }
    }

    /// Returns true if character belongs to CJK scripts (Han, Hiragana, Katakana, Hangul).
    pub fn is_cjk(&self, ch: char) -> bool {
        let script_map = maps::script();
        let script = script_map.get(ch);
        matches!(
            script,
            Script::Han | Script::Hiragana | Script::Katakana | Script::Hangul
        )
    }

    /// Return string name of the script.
    pub fn script_name(&self, ch: char) -> &'static str {
        let script_map = maps::script();
        match script_map.get(ch) {
            Script::Han => "Han (CJK)",
            Script::Hiragana => "Hiragana",
            Script::Katakana => "Katakana",
            Script::Hangul => "Hangul",
            Script::Arabic => "Arabic",
            Script::Hebrew => "Hebrew",
            Script::Latin => "Latin",
            Script::Cyrillic => "Cyrillic",
            Script::Greek => "Greek",
            Script::Devanagari => "Devanagari",
            _ => "Other",
        }
    }
}

/// Shaped glyph output from rustybuzz.
#[derive(Debug, Clone, PartialEq)]
pub struct ShapedGlyph {
    pub glyph_id: u32,
    pub cluster: u32,
    pub x_advance: i32,
    pub y_advance: i32,
    pub x_offset: i32,
    pub y_offset: i32,
}

/// HarfBuzz-compatible glyph shaper powered by rustybuzz.
pub struct GlyphShaper {
    _private: (),
}

impl Default for GlyphShaper {
    fn default() -> Self {
        Self::new()
    }
}

impl GlyphShaper {
    pub fn new() -> Self {
        Self { _private: () }
    }

    /// Shape text using font bytes.
    pub fn shape_text(&self, font_bytes: &[u8], text: &str) -> Result<Vec<ShapedGlyph>> {
        let face = rustybuzz::Face::from_slice(font_bytes, 0)
            .ok_or_else(|| TextEngineError::Shaping("Invalid font data or face index 0".into()))?;

        let mut buffer = rustybuzz::UnicodeBuffer::new();
        buffer.push_str(text);

        let output = rustybuzz::shape(&face, &[], buffer);
        let infos = output.glyph_infos();
        let positions = output.glyph_positions();

        let mut shaped = Vec::with_capacity(infos.len());
        for (info, pos) in infos.iter().zip(positions.iter()) {
            shaped.push(ShapedGlyph {
                glyph_id: info.glyph_id,
                cluster: info.cluster,
                x_advance: pos.x_advance,
                y_advance: pos.y_advance,
                x_offset: pos.x_offset,
                y_offset: pos.y_offset,
            });
        }

        Ok(shaped)
    }
}
