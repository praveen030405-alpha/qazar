//! # text-engine
//!
//! High-performance text extraction, ICU4X/rustybuzz shaping, sub-pixel selection,
//! and Tantivy inverted index search for the Meridian Quantum PDF Viewer.
//!
//! Architecture ref (Section 2, line 45 & Section 8, line 188):
//! - Deterministic rustybuzz + ICU4X text shaping and Unicode bidi classification.
//! - Extraction fidelity >= 99% vs reference.
//! - Tantivy search first result <= 100 ms on 10,000 pages.
//! - CJK and RTL selection geometry.

pub mod error;
pub mod extractor;
pub mod index;
pub mod search;
pub mod selection;
pub mod shaper;

pub use error::{Result, TextEngineError};
pub use extractor::{
    calculate_extraction_fidelity, ExtractedChar, ExtractedLine, ExtractedPage,
    ExtractedWord, TextExtractor,
};
pub use index::{SearchSchemaFields, TantivySearchIndex};
pub use search::{SearchEngine, SearchHit};
pub use selection::{SelectionEngine, SelectionPoint, TextSelection};
pub use shaper::{
    BidiClassifier, GlyphShaper, ScriptClassifier, ShapedGlyph, TextDirection,
};

#[cfg(test)]
mod tests {
    use super::*;
    use pdf_kernel::{GlyphQuad, PageText, TextChar};
    use std::time::Instant;

    fn make_mock_char(ch: char, left: f64, bottom: f64, width: f64, height: f64) -> TextChar {
        TextChar {
            ch,
            bounds: GlyphQuad {
                left,
                top: bottom + height,
                right: left + width,
                bottom,
            },
        }
    }

    fn make_mock_page(page_index: usize, text: &str) -> PageText {
        let mut chars = Vec::new();
        let mut x = 72.0;
        let y = 700.0;
        let char_w = 7.0;
        let char_h = 10.0;

        for ch in text.chars() {
            chars.push(make_mock_char(ch, x, y, char_w, char_h));
            x += char_w;
        }

        PageText {
            page_index,
            text: text.to_string(),
            chars,
        }
    }

    #[test]
    fn test_extraction_fidelity_criterion() {
        let reference = "Quantum PDF Engine provides zero-latency document rendering and sub-pixel text selection.";
        let extracted = "Quantum PDF Engine provides zero-latency document rendering and sub-pixel text selection.";
        
        let fidelity = calculate_extraction_fidelity(extracted, reference);
        assert!(fidelity >= 99.0, "Extraction fidelity was {}% (expected >= 99.0%)", fidelity);

        // Test with 1 minor typo/char difference
        let slightly_different = "Quantum PDF Engine provides zero-latency documxnt rendering and sub-pixel text selection.";
        let fid2 = calculate_extraction_fidelity(slightly_different, reference);
        assert!(fid2 >= 98.0, "Fidelity should handle minor differences: {}", fid2);
    }

    #[test]
    fn test_text_extractor_layout_reconstruction() {
        let raw = make_mock_page(0, "Quantum Architecture for PDF");
        let extractor = TextExtractor::new();
        let page = extractor.extract_page(&raw);

        assert_eq!(page.words.len(), 4);
        assert_eq!(page.words[0].text, "Quantum");
        assert_eq!(page.words[1].text, "Architecture");
        assert_eq!(page.words[2].text, "for");
        assert_eq!(page.words[3].text, "PDF");
        assert_eq!(page.lines.len(), 1);
    }

    #[test]
    fn test_icu4x_bidi_and_script_classification() {
        let bidi = BidiClassifier::new();
        let script = ScriptClassifier::new();

        // English LTR
        assert!(!bidi.is_rtl('A'));
        assert_eq!(bidi.detect_direction("Hello World"), TextDirection::LeftToRight);

        // Arabic / Hebrew RTL
        let arabic_char = 'م'; // Arabic meem
        let hebrew_char = 'ש'; // Hebrew shin
        assert!(bidi.is_rtl(arabic_char));
        assert!(bidi.is_rtl(hebrew_char));
        assert_eq!(bidi.detect_direction("مرحبا بك"), TextDirection::RightToLeft);

        // CJK Scripts
        let cjk_han = '漢';
        let cjk_hiragana = 'あ';
        let cjk_hangul = '한';
        assert!(script.is_cjk(cjk_han));
        assert!(script.is_cjk(cjk_hiragana));
        assert!(script.is_cjk(cjk_hangul));
        assert!(!script.is_cjk('Z'));
    }

    #[test]
    fn test_selection_hit_testing_and_quads() {
        let raw = make_mock_page(0, "Quantum Selection Test");
        let extractor = TextExtractor::new();
        let page = extractor.extract_page(&raw);

        // Test character hit test at position (75.0, 705.0) -> 'Q'
        let hit = SelectionEngine::hit_test(&page, 75.0, 705.0);
        assert!(hit.is_some());

        // Test select word 'Quantum'
        let sel_word = SelectionEngine::select_word(&page, 0).unwrap();
        let selected_str = SelectionEngine::get_selected_text(&page, &sel_word);
        assert_eq!(selected_str, "Quantum");

        // Test selection quads computation
        let quads = SelectionEngine::compute_selection_quads(&page, &sel_word).unwrap();
        assert!(!quads.is_empty());
        assert!(quads[0].right > quads[0].left);
    }

    #[test]
    fn test_tantivy_search_first_result_10k_pages_benchmark() {
        // Architecture exit criterion (Section 8, line 188):
        // "Search first-result <= 100 ms on 10k pages"
        let index = TantivySearchIndex::create_in_ram().expect("Failed to create Tantivy index in RAM");

        // Index 10,000 pages synthetically
        let batch_size = 10_000;
        let mut pages = Vec::with_capacity(batch_size);

        println!("Indexing {} pages into Tantivy in-memory index...", batch_size);
        let t_index_start = Instant::now();

        let target_page_index = 7_428;

        for i in 0..batch_size {
            let text = if i == target_page_index {
                "In page 7428 we reveal the Quantum Architecture secret protocol with Tantivy search engine."
            } else if i % 10 == 0 {
                "Standard financial report and document balance sheets for annual tax evaluation."
            } else if i % 5 == 0 {
                "Theoretical computational physics of quantum teleportation and wave function superposition."
            } else {
                "Continuous scrolling tile pipelines with GPU compositor and multi-tier memory governor."
            };

            pages.push(ExtractedPage {
                page_index: i,
                full_text: text.to_string(),
                chars: Vec::new(),
                words: Vec::new(),
                lines: Vec::new(),
            });
        }

        index.batch_index_pages(&pages, "Corpus 10K").expect("Batch index pages failed");
        index.commit().expect("Commit failed");

        let index_duration = t_index_start.elapsed();
        println!("Indexed {} pages in {:.2?}", batch_size, index_duration);

        let search_engine = SearchEngine::new(index);

        // Run search query
        let query = "secret protocol";
        let (hits, latency_ms) = search_engine.search(query, 10, &pages).expect("Search failed");

        println!("Tantivy Query '{}' on 10k pages: Latency = {:.2} ms, Hits = {}", query, latency_ms, hits.len());

        assert!(!hits.is_empty(), "Expected at least 1 hit");
        assert_eq!(hits[0].page_index, target_page_index);
        // EXIT CRITERIA: search first-result <= 100 ms on 10k pages
        assert!(
            latency_ms <= 100.0,
            "Exit criterion violated: Search latency was {:.2} ms (target <= 100.0 ms)",
            latency_ms
        );
    }
}
