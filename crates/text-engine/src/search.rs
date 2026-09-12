//! Search execution engine and visual hit-highlight quad generation.
//!
//! Evaluates queries through Tantivy and resolves character-level
//! bounding boxes for instant UI highlight rendering.

use tantivy::{
    collector::TopDocs,
    query::QueryParser,
    schema::Value,
    TantivyDocument,
};
use std::time::Instant;
use pdf_kernel::GlyphQuad;
use crate::error::Result;
use crate::extractor::ExtractedPage;
use crate::index::TantivySearchIndex;


/// A single search result hit with page position and highlight quads.
#[derive(Debug, Clone)]
pub struct SearchHit {
    pub page_index: usize,
    pub score: f32,
    pub snippet: String,
    pub highlight_quads: Vec<GlyphQuad>,
}

/// Search execution engine.
pub struct SearchEngine {
    index: TantivySearchIndex,
}

impl SearchEngine {
    pub fn new(index: TantivySearchIndex) -> Self {
        Self { index }
    }

    /// Access the underlying Tantivy index.
    pub fn index(&self) -> &TantivySearchIndex {
        &self.index
    }

    /// Execute a search query and map matches against provided extracted pages.
    pub fn search(
        &self,
        query_str: &str,
        limit: usize,
        pages: &[ExtractedPage],
    ) -> Result<(Vec<SearchHit>, f64)> {
        let t0 = Instant::now();
        let fields = self.index.fields();
        let reader = self.index.reader();
        let searcher = reader.searcher();

        let query_parser = QueryParser::for_index(self.index.index(), vec![fields.body]);
        let query = query_parser.parse_query(query_str)?;

        let top_docs = searcher.search(&query, &TopDocs::with_limit(limit))?;
        let latency_ms = t0.elapsed().as_secs_f64() * 1000.0;

        let query_lower = query_str.to_lowercase();
        let mut hits = Vec::with_capacity(top_docs.len());

        for (score, doc_addr) in top_docs {
            let doc: TantivyDocument = searcher.doc(doc_addr)?;
            let page_val = doc.get_first(fields.page)
                .and_then(|v| v.as_u64())
                .unwrap_or(0) as usize;

            let body_val = doc.get_first(fields.body)
                .and_then(|v| v.as_str())
                .unwrap_or("");

            // Find corresponding page to extract exact highlight quads
            let mut highlight_quads = Vec::new();
            if let Some(page) = pages.iter().find(|p| p.page_index == page_val) {
                // Find all substring occurrences of query in page text
                let text_lower = page.full_text.to_lowercase();
                let mut start_search = 0;
                while let Some(pos) = text_lower[start_search..].find(&query_lower) {
                    let match_start = start_search + pos;
                    let match_end = match_start + query_lower.len();
                    if let Ok(quads) = page.get_range_bounds(match_start, match_end) {
                        highlight_quads.extend(quads);
                    }
                    start_search = match_end;
                }
            }

            // Create snippet around match
            let snippet = if let Some(idx) = body_val.to_lowercase().find(&query_lower) {
                let s = idx.saturating_sub(40);
                let e = (idx + query_str.len() + 40).min(body_val.len());
                format!("...{}...", &body_val[s..e])
            } else {
                body_val.chars().take(80).collect()
            };

            hits.push(SearchHit {
                page_index: page_val,
                score,
                snippet,
                highlight_quads,
            });
        }

        Ok((hits, latency_ms))
    }
}
