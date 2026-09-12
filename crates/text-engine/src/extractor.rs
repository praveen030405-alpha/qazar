//! High-fidelity text and glyph bounding box extraction.
//!
//! Reconstructs words, lines, and normalized visual bounding quads from
//! individual character glyphs extracted by the PDF kernel.

use pdf_kernel::{GlyphQuad, PageText};
use crate::error::{Result, TextEngineError};
use crate::shaper::{BidiClassifier, ScriptClassifier};


/// A single extracted character with positioning and script classification.
#[derive(Debug, Clone, PartialEq)]
pub struct ExtractedChar {
    pub ch: char,
    pub bounds: GlyphQuad,
    pub is_whitespace: bool,
    pub is_rtl: bool,
    pub is_cjk: bool,
}

/// A word bounded by whitespace or script boundaries with continuous bounding quad.
#[derive(Debug, Clone, PartialEq)]
pub struct ExtractedWord {
    pub text: String,
    pub bounds: GlyphQuad,
    pub char_range: (usize, usize), // [start, end) index into ExtractedPage.chars
}

/// A line of text reconstructed from characters with matching baseline coordinates.
#[derive(Debug, Clone, PartialEq)]
pub struct ExtractedLine {
    pub text: String,
    pub bounds: GlyphQuad,
    pub words: Vec<ExtractedWord>,
    pub char_range: (usize, usize),
}

/// Fully extracted and structured page text layer.
#[derive(Debug, Clone)]
pub struct ExtractedPage {
    pub page_index: usize,
    pub full_text: String,
    pub chars: Vec<ExtractedChar>,
    pub words: Vec<ExtractedWord>,
    pub lines: Vec<ExtractedLine>,
}

impl ExtractedPage {
    /// Total number of extracted glyph characters.
    pub fn len(&self) -> usize {
        self.chars.len()
    }

    /// Whether the page text is empty.
    pub fn is_empty(&self) -> bool {
        self.chars.is_empty()
    }

    /// Retrieve the bounding quad for a character range [start, end).
    pub fn get_range_bounds(&self, start: usize, end: usize) -> Result<Vec<GlyphQuad>> {
        if start > end || end > self.chars.len() {
            return Err(TextEngineError::InvalidCharIndex {
                index: end,
                total: self.chars.len(),
            });
        }
        if start == end {
            return Ok(Vec::new());
        }

        // Group into lines to produce merged selection boxes
        let mut quads = Vec::new();
        let mut current_quad: Option<GlyphQuad> = None;

        for i in start..end {
            let ch = &self.chars[i];
            if ch.is_whitespace {
                continue;
            }

            match &mut current_quad {
                None => {
                    current_quad = Some(ch.bounds);
                }
                Some(q) => {
                    // Check if on same horizontal baseline (within 3pt)
                    let baseline_diff = (q.bottom - ch.bounds.bottom).abs();
                    if baseline_diff < 3.0 {
                        // Merge horizontally
                        q.left = q.left.min(ch.bounds.left);
                        q.right = q.right.max(ch.bounds.right);
                        q.top = q.top.max(ch.bounds.top);
                        q.bottom = q.bottom.min(ch.bounds.bottom);
                    } else {
                        quads.push(*q);
                        current_quad = Some(ch.bounds);
                    }
                }
            }
        }

        if let Some(q) = current_quad {
            quads.push(q);
        }

        Ok(quads)
    }
}

/// Text extractor service.
pub struct TextExtractor {
    bidi: BidiClassifier,
    script: ScriptClassifier,
}

impl Default for TextExtractor {
    fn default() -> Self {
        Self::new()
    }
}

impl TextExtractor {
    pub fn new() -> Self {
        Self {
            bidi: BidiClassifier::new(),
            script: ScriptClassifier::new(),
        }
    }

    /// Extract structured page text from raw `pdf_kernel::PageText`.
    pub fn extract_page(&self, raw: &PageText) -> ExtractedPage {
        let mut chars = Vec::with_capacity(raw.chars.len());
        let mut full_text = String::with_capacity(raw.text.len());

        for c in &raw.chars {
            let is_ws = c.ch.is_whitespace();
            let is_rtl = self.bidi.is_rtl(c.ch);
            let is_cjk = self.script.is_cjk(c.ch);

            chars.push(ExtractedChar {
                ch: c.ch,
                bounds: c.bounds,
                is_whitespace: is_ws,
                is_rtl,
                is_cjk,
            });
            full_text.push(c.ch);
        }

        // Reconstruct words and lines
        let (words, lines) = self.reconstruct_layout(&chars);

        ExtractedPage {
            page_index: raw.page_index,
            full_text,
            chars,
            words,
            lines,
        }
    }

    /// Layout analysis reconstructing words and lines from character streams.
    fn reconstruct_layout(&self, chars: &[ExtractedChar]) -> (Vec<ExtractedWord>, Vec<ExtractedLine>) {
        if chars.is_empty() {
            return (Vec::new(), Vec::new());
        }

        let mut words = Vec::new();
        let mut current_word_text = String::new();
        let mut current_word_bounds: Option<GlyphQuad> = None;
        let mut current_word_start = 0;

        for (i, c) in chars.iter().enumerate() {
            if c.is_whitespace {
                if !current_word_text.is_empty() {
                    if let Some(bounds) = current_word_bounds.take() {
                        words.push(ExtractedWord {
                            text: std::mem::take(&mut current_word_text),
                            bounds,
                            char_range: (current_word_start, i),
                        });
                    }
                }
                current_word_start = i + 1;
            } else {
                current_word_text.push(c.ch);
                match &mut current_word_bounds {
                    None => {
                        current_word_bounds = Some(c.bounds);
                    }
                    Some(b) => {
                        b.left = b.left.min(c.bounds.left);
                        b.right = b.right.max(c.bounds.right);
                        b.top = b.top.max(c.bounds.top);
                        b.bottom = b.bottom.min(c.bounds.bottom);
                    }
                }
            }
        }

        if !current_word_text.is_empty() {
            if let Some(bounds) = current_word_bounds {
                words.push(ExtractedWord {
                    text: current_word_text,
                    bounds,
                    char_range: (current_word_start, chars.len()),
                });
            }
        }

        // Reconstruct lines from words
        let mut lines = Vec::new();
        let mut current_line_words: Vec<ExtractedWord> = Vec::new();

        for word in words.iter().cloned() {
            if let Some(last_word) = current_line_words.last() {
                // Check if on the same horizontal line
                let baseline_diff = (last_word.bounds.bottom - word.bounds.bottom).abs();
                if baseline_diff > 4.0 {
                    // New line
                    if let Some(line) = Self::build_line_from_words(std::mem::take(&mut current_line_words)) {
                        lines.push(line);
                    }
                }
            }
            current_line_words.push(word);
        }

        if !current_line_words.is_empty() {
            if let Some(line) = Self::build_line_from_words(current_line_words) {
                lines.push(line);
            }
        }

        (words, lines)
    }

    fn build_line_from_words(words: Vec<ExtractedWord>) -> Option<ExtractedLine> {
        if words.is_empty() {
            return None;
        }

        let start = words.first()?.char_range.0;
        let end = words.last()?.char_range.1;

        let mut line_text = String::new();
        let mut bounds = words[0].bounds;

        for (i, w) in words.iter().enumerate() {
            if i > 0 {
                line_text.push(' ');
            }
            line_text.push_str(&w.text);
            bounds.left = bounds.left.min(w.bounds.left);
            bounds.right = bounds.right.max(w.bounds.right);
            bounds.top = bounds.top.max(w.bounds.top);
            bounds.bottom = bounds.bottom.min(w.bounds.bottom);
        }

        Some(ExtractedLine {
            text: line_text,
            bounds,
            words,
            char_range: (start, end),
        })
    }
}

/// Calculate extraction fidelity metric between extracted and reference string.
/// Returns percentage in range [0.0, 100.0].
/// Architecture exit criterion: Fidelity >= 99%.
pub fn calculate_extraction_fidelity(extracted: &str, reference: &str) -> f64 {
    let ex_clean: String = extracted.chars().filter(|c| !c.is_whitespace()).collect();
    let ref_clean: String = reference.chars().filter(|c| !c.is_whitespace()).collect();

    if ref_clean.is_empty() {
        return if ex_clean.is_empty() { 100.0 } else { 0.0 };
    }

    // Levenshtein distance calculation
    let ex_chars: Vec<char> = ex_clean.chars().collect();
    let ref_chars: Vec<char> = ref_clean.chars().collect();

    let m = ex_chars.len();
    let n = ref_chars.len();

    let mut dp = vec![vec![0usize; n + 1]; m + 1];

    for i in 0..=m {
        dp[i][0] = i;
    }
    for j in 0..=n {
        dp[0][j] = j;
    }

    for i in 1..=m {
        for j in 1..=n {
            let cost = if ex_chars[i - 1] == ref_chars[j - 1] { 0 } else { 1 };
            dp[i][j] = (dp[i - 1][j] + 1)
                .min(dp[i][j - 1] + 1)
                .min(dp[i - 1][j - 1] + cost);
        }
    }

    let edit_dist = dp[m][n];
    let max_len = m.max(n) as f64;
    let fidelity = ((max_len - edit_dist as f64) / max_len) * 100.0;
    fidelity.max(0.0)
}
