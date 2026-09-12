use thiserror::Error;

/// Every error that can escape the PDF kernel boundary.
///
/// Designed so callers never need to know whether PDFium or a future
/// Rust-native parser lives underneath — they match on semantics,
/// not implementation artifacts.
#[derive(Debug, Error)]
pub enum PdfKernelError {
    /// The engine's native library couldn't be located or loaded.
    #[error("failed to load PDF engine from '{path}': {reason}")]
    EngineLoadFailed { path: String, reason: String },

    /// The byte stream isn't a valid PDF, or xref recovery failed.
    #[error("failed to parse document: {0}")]
    ParseFailed(String),

    /// The document is encrypted and no password (or the wrong one) was supplied.
    #[error("document is password-protected")]
    PasswordRequired,

    /// Requested page index is out of range.
    #[error("page index {index} is out of range (document has {count} pages)")]
    InvalidPage { index: usize, count: usize },

    /// Rasterization of a page region failed.
    #[error("render failed for page {page}: {reason}")]
    RenderFailed { page: usize, reason: String },

    /// Catch-all for unexpected internal failures.
    #[error("internal kernel error: {0}")]
    Internal(String),
}

pub type Result<T> = std::result::Result<T, PdfKernelError>;
