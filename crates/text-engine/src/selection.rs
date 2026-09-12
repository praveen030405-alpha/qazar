//! Precise spatial hit-testing and multi-line text selection geometry.
//!
//! Converts pointer coordinates into character offsets and generates
//! pixel-accurate selection highlight quadrilaterals across LTR, RTL, and CJK text.

use pdf_kernel::GlyphQuad;
use crate::error::{Result, TextEngineError};
use crate::extractor::ExtractedPage;

/// A selection boundary point.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SelectionPoint {
    pub page_index: usize,
    pub char_index: usize,
}

/// Active text selection spanning from anchor (start click) to focus (drag head).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct TextSelection {
    pub anchor: SelectionPoint,
    pub focus: SelectionPoint,
}

impl TextSelection {
    pub fn new(page_index: usize, start: usize, end: usize) -> Self {
        Self {
            anchor: SelectionPoint { page_index, char_index: start },
            focus: SelectionPoint { page_index, char_index: end },
        }
    }

    /// Normalized range (start <= end).
    pub fn range(&self) -> (usize, usize) {
        let a = self.anchor.char_index;
        let f = self.focus.char_index;
        if a <= f { (a, f) } else { (f, a) }
    }

    /// Whether selection is a collapsed caret (0 length).
    pub fn is_collapsed(&self) -> bool {
        self.anchor.char_index == self.focus.char_index
    }
}

/// Text selector engine.
pub struct SelectionEngine;

impl SelectionEngine {
    /// Hit-test pointer coordinates `(x, y)` in page space to find the closest character index.
    pub fn hit_test(page: &ExtractedPage, x: f64, y: f64) -> Option<usize> {
        if page.chars.is_empty() {
            return None;
        }

        // First check exact quad containment
        for (i, ch) in page.chars.iter().enumerate() {
            let b = &ch.bounds;
            if x >= b.left && x <= b.right && y >= b.bottom && y <= b.top {
                // Determine whether pointer is closer to left or right half of glyph
                let mid_x = (b.left + b.right) / 2.0;
                return if ch.is_rtl {
                    if x > mid_x { Some(i) } else { Some(i + 1) }
                } else {
                    if x < mid_x { Some(i) } else { Some(i + 1) }
                };
            }
        }

        // Otherwise find geometrically nearest character
        let mut min_dist_sq = f64::MAX;
        let mut closest_idx = 0;

        for (i, ch) in page.chars.iter().enumerate() {
            let b = &ch.bounds;
            let cx = (b.left + b.right) / 2.0;
            let cy = (b.bottom + b.top) / 2.0;
            let dx = x - cx;
            let dy = y - cy;
            let dist_sq = dx * dx + dy * dy;

            if dist_sq < min_dist_sq {
                min_dist_sq = dist_sq;
                closest_idx = i;
            }
        }

        Some(closest_idx)
    }

    /// Select entire word enclosing the given character index (for double-click).
    pub fn select_word(page: &ExtractedPage, char_idx: usize) -> Result<TextSelection> {
        if char_idx >= page.chars.len() {
            return Err(TextEngineError::InvalidCharIndex {
                index: char_idx,
                total: page.chars.len(),
            });
        }

        // Check if index falls inside a word
        for word in &page.words {
            if char_idx >= word.char_range.0 && char_idx < word.char_range.1 {
                return Ok(TextSelection::new(
                    page.page_index,
                    word.char_range.0,
                    word.char_range.1,
                ));
            }
        }

        // Fallback: 1-character selection
        Ok(TextSelection::new(page.page_index, char_idx, char_idx + 1))
    }

    /// Select entire line enclosing the given character index (for triple-click).
    pub fn select_line(page: &ExtractedPage, char_idx: usize) -> Result<TextSelection> {
        if char_idx >= page.chars.len() {
            return Err(TextEngineError::InvalidCharIndex {
                index: char_idx,
                total: page.chars.len(),
            });
        }

        for line in &page.lines {
            if char_idx >= line.char_range.0 && char_idx < line.char_range.1 {
                return Ok(TextSelection::new(
                    page.page_index,
                    line.char_range.0,
                    line.char_range.1,
                ));
            }
        }

        Ok(TextSelection::new(page.page_index, char_idx, char_idx + 1))
    }

    /// Extract continuous selection highlight boxes for rendering.
    pub fn compute_selection_quads(page: &ExtractedPage, selection: &TextSelection) -> Result<Vec<GlyphQuad>> {
        let (start, end) = selection.range();
        page.get_range_bounds(start, end)
    }

    /// Extract selected string text from the page.
    pub fn get_selected_text(page: &ExtractedPage, selection: &TextSelection) -> String {
        let (start, end) = selection.range();
        let end_clamped = end.min(page.chars.len());
        let start_clamped = start.min(end_clamped);

        page.chars[start_clamped..end_clamped]
            .iter()
            .map(|c| c.ch)
            .collect()
    }
}
