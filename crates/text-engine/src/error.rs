//! Error types for the Meridian Text Engine.

use thiserror::Error;

#[derive(Error, Debug)]
pub enum TextEngineError {
    #[error("Pdf kernel error: {0}")]
    PdfKernel(#[from] pdf_kernel::PdfKernelError),

    #[error("Tantivy search error: {0}")]
    Tantivy(#[from] tantivy::TantivyError),

    #[error("Tantivy query parser error: {0}")]
    QueryParser(#[from] tantivy::query::QueryParserError),

    #[error("Font shaping error: {0}")]
    Shaping(String),

    #[error("Invalid character index: {index} (total {total})")]
    InvalidCharIndex { index: usize, total: usize },

    #[error("Empty document or page")]
    EmptyPage,
}

pub type Result<T> = std::result::Result<T, TextEngineError>;
