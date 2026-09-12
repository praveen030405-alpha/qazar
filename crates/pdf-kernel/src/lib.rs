//! # pdf-kernel
//!
//! The PDF parsing and rendering layer for Meridian.
//!
//! Architecture ref (Section 2, line 42): PDFium sits behind an
//! internal abstraction so it can be replaced incrementally by Rust
//! components without touching the rest of the system.
//!
//! ## Phase 0 Spike
//!
//! In Phase 0 we provide a direct, high-performance `PdfEngine` API
//! to profile cold open and page rasterization latency against the 150ms budget.

pub mod engine;
pub mod error;
pub mod types;

pub use engine::{PdfDoc, PdfEngine};
pub use error::{PdfKernelError, Result};
pub use types::{DocumentInfo, GlyphQuad, PageDimensions, PageText, RgbaBuffer, TextChar, TextSpan};
