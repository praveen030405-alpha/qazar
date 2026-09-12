use crate::error::Result;
use crate::types::{DocumentInfo, PageDimensions, RgbaBuffer};

/// The trait boundary between the rendering engine and the rest of
/// the system.
///
/// Architecture ref: Section 2, line 42 — PDFium sits behind an
/// internal `PdfKernel` trait so it can be replaced incrementally
/// by Rust components without touching the rest of the system.
///
/// Every implementation must be `Send + Sync` so the tile scheduler
/// can hand work to a thread pool.
pub trait PdfKernel: Send + Sync {
    /// Open a PDF document from an in-memory byte slice.
    ///
    /// Returns an opaque handle that the caller passes back into
    /// every subsequent method.  The handle is valid until dropped.
    fn open_document(
        &self,
        data: &[u8],
        password: Option<&str>,
    ) -> Result<Box<dyn DocumentHandle>>;

    /// Open a PDF document from a file path.
    ///
    /// Default implementation reads the file into memory and delegates
    /// to `open_document`.  Implementations may override for memory-
    /// mapped I/O or streaming.
    fn open_file(
        &self,
        path: &std::path::Path,
        password: Option<&str>,
    ) -> Result<Box<dyn DocumentHandle>> {
        let data = std::fs::read(path).map_err(|e| {
            crate::error::PdfKernelError::ParseFailed(format!(
                "failed to read '{}': {}",
                path.display(),
                e
            ))
        })?;
        self.open_document(&data, password)
    }
}

/// An opaque handle to an open PDF document.
///
/// Implementations own whatever native resources the engine needs
/// (e.g. PDFium's `FPDF_DOCUMENT`).  Dropping the handle releases
/// them.
///
/// The handle is `Send` so documents can be passed between threads,
/// but NOT `Sync` — concurrent page renders from the same handle
/// must go through the tile scheduler's work queue, which serialises
/// access per-document.
pub trait DocumentHandle: Send {
    /// Basic metadata about the document.
    fn info(&self) -> DocumentInfo;

    /// Number of pages.
    fn page_count(&self) -> usize;

    /// Dimensions of a single page in PDF points.
    fn page_dimensions(&self, page_index: usize) -> Result<PageDimensions>;

    /// Render a full page to an RGBA bitmap at the requested DPI.
    ///
    /// This is the Phase 0 workhorse.  In Phase 1 the tile engine
    /// will call a sub-region variant instead, but the full-page
    /// path remains useful for thumbnails and golden-image tests.
    fn render_page(&self, page_index: usize, dpi: f32) -> Result<RgbaBuffer>;

    /// Render a rectangular sub-region of a page to an RGBA bitmap.
    ///
    /// Coordinates are in PDF points (origin = bottom-left of page).
    /// The output bitmap is `pixel_width × pixel_height`.
    /// Used by the tile engine to rasterise individual 256×256 tiles.
    fn render_page_region(
        &self,
        page_index: usize,
        x_points: f64,
        y_points: f64,
        width_points: f64,
        height_points: f64,
        pixel_width: u32,
        pixel_height: u32,
    ) -> Result<RgbaBuffer>;
}
