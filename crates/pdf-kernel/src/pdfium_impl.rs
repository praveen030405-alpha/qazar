//! Concrete `PdfKernel` implementation backed by Chromium's PDFium.
//!
//! Architecture ref: Section 2, line 42 — "PDFium (via audited Rust
//! FFI bindings, sandboxed process)".  This module wraps `pdfium-render`
//! and exposes it through the `PdfKernel` / `DocumentHandle` traits
//! so nothing else in the system touches PDFium directly.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use pdfium_render::prelude::*;

use crate::error::{PdfKernelError, Result};
use crate::traits::{DocumentHandle, PdfKernel};
use crate::types::{DocumentInfo, PageDimensions, RgbaBuffer};

// ---------------------------------------------------------------------------
// PdfiumKernel — the engine singleton
// ---------------------------------------------------------------------------

/// A `PdfKernel` backed by Chromium's PDFium via `pdfium-render`.
///
/// Construct one per process, point it at the directory containing
/// `pdfium.dll` / `libpdfium.so`, and hand it to anything that
/// needs to open PDFs.
pub struct PdfiumKernel {
    /// The library binding.  `Arc` because `Pdfium` is not `Clone`
    /// but we need to share it with every open document.
    pdfium: Arc<Pdfium>,
}

impl PdfiumKernel {
    /// Create a new kernel, loading PDFium from `lib_dir`.
    ///
    /// `lib_dir` should contain `pdfium.dll` (Windows) or
    /// `libpdfium.so` (Linux) / `libpdfium.dylib` (macOS).
    pub fn new(lib_dir: &Path) -> Result<Self> {
        let lib_path = Self::find_library(lib_dir)?;
        let pdfium = Pdfium::new(
            Pdfium::bind_to_library(lib_path.to_str().unwrap_or_default())
                .map_err(|e| PdfKernelError::EngineLoadFailed {
                    path: lib_path.display().to_string(),
                    reason: e.to_string(),
                })?,
        );
        log::info!(
            "PDFium engine loaded from {}",
            lib_path.display()
        );
        Ok(Self {
            pdfium: Arc::new(pdfium),
        })
    }

    /// Locate the platform-appropriate PDFium shared library inside `dir`.
    fn find_library(dir: &Path) -> Result<PathBuf> {
        if dir.is_file() {
            return Ok(dir.to_path_buf());
        }
        let candidates: &[&str] = if cfg!(target_os = "windows") {
            &["pdfium.dll"]
        } else if cfg!(target_os = "macos") {
            &["libpdfium.dylib"]
        } else {
            &["libpdfium.so"]
        };

        for name in candidates {
            let p = dir.join(name);
            if p.exists() {
                return Ok(p);
            }
        }

        Err(PdfKernelError::EngineLoadFailed {
            path: dir.display().to_string(),
            reason: format!("none of {:?} found in directory", candidates),
        })
    }
}

impl PdfKernel for PdfiumKernel {
    fn open_document(
        &self,
        data: &[u8],
        password: Option<&str>,
    ) -> Result<Box<dyn DocumentHandle>> {
        let doc = self
            .pdfium
            .load_pdf_from_byte_slice(data, password)
            .map_err(|e| {
                let msg = e.to_string();
                if msg.contains("password") || msg.contains("Password") {
                    PdfKernelError::PasswordRequired
                } else {
                    PdfKernelError::ParseFailed(msg)
                }
            })?;

        Ok(Box::new(PdfiumDocument { doc }))
    }

    fn open_file(
        &self,
        path: &Path,
        password: Option<&str>,
    ) -> Result<Box<dyn DocumentHandle>> {
        let data = std::fs::read(path).map_err(|e| {
            PdfKernelError::ParseFailed(format!(
                "failed to read '{}': {}",
                path.display(),
                e
            ))
        })?;
        self.open_document(&data, password)
    }
}

// ---------------------------------------------------------------------------
// PdfiumDocument — an open document handle
// ---------------------------------------------------------------------------

/// A single open PDF document backed by PDFium.
///
/// Owns the `PdfDocument` from `pdfium-render`.  Dropping this
/// releases all native PDFium resources for this document.
struct PdfiumDocument<'a> {
    doc: PdfDocument<'a>,
}

// Safety: PdfiumDocument wraps pdfium-render's PdfDocument which
// manages its own internal state.  We serialise access through the
// tile scheduler in Phase 1.
unsafe impl Send for PdfiumDocument<'_> {}

impl DocumentHandle for PdfiumDocument<'_> {
    fn info(&self) -> DocumentInfo {
        let meta = self.doc.metadata();
        DocumentInfo {
            page_count: self.doc.pages().len() as usize,
            title: meta.title(),
            author: meta.author(),
        }
    }

    fn page_count(&self) -> usize {
        self.doc.pages().len() as usize
    }

    fn page_dimensions(&self, page_index: usize) -> Result<PageDimensions> {
        let pages = self.doc.pages();
        let count = pages.len() as usize;
        if page_index >= count {
            return Err(PdfKernelError::InvalidPage {
                index: page_index,
                count,
            });
        }
        let page = pages.get(page_index as u16).map_err(|e| {
            PdfKernelError::RenderFailed {
                page: page_index,
                reason: e.to_string(),
            }
        })?;
        Ok(PageDimensions {
            width_points: page.width().value as f64,
            height_points: page.height().value as f64,
        })
    }

    fn render_page(&self, page_index: usize, dpi: f32) -> Result<RgbaBuffer> {
        let pages = self.doc.pages();
        let count = pages.len() as usize;
        if page_index >= count {
            return Err(PdfKernelError::InvalidPage {
                index: page_index,
                count,
            });
        }

        let page = pages.get(page_index as u16).map_err(|e| {
            PdfKernelError::RenderFailed {
                page: page_index,
                reason: e.to_string(),
            }
        })?;

        // Compute pixel dimensions from page points at requested DPI.
        let scale = dpi / 72.0;
        let w = (page.width().value * scale) as u32;
        let h = (page.height().value * scale) as u32;

        // Render to an image::DynamicImage via pdfium-render with Pure Grayscale SSAA anti-aliasing
        // (LCD subpixel color fringing disabled to ensure razor-sharp ink on mobile OLED/AMOLED/LCD panels).
        let render_config = PdfRenderConfig::new()
            .set_target_width(w as i32)
            .set_maximum_height(h as i32)
            .render_form_data(true)
            .render_annotations(false)
            .use_lcd_text_rendering(false)
            .set_text_smoothing(true)
            .set_path_smoothing(true)
            .set_image_smoothing(true)
            .force_half_tone(false)
            .use_print_quality(true);

        let bitmap = page.render_with_config(&render_config).map_err(|e| {
            PdfKernelError::RenderFailed {
                page: page_index,
                reason: e.to_string(),
            }
        })?;

        let img = bitmap.as_image();
        let rgba = img.to_rgba8();

        Ok(RgbaBuffer {
            width: rgba.width(),
            height: rgba.height(),
            data: rgba.into_raw(),
        })
    }

    fn render_page_region(
        &self,
        page_index: usize,
        _x_points: f64,
        _y_points: f64,
        _width_points: f64,
        _height_points: f64,
        pixel_width: u32,
        pixel_height: u32,
    ) -> Result<RgbaBuffer> {
        // Phase 0: full-page render then crop.
        // Phase 1 will replace this with a direct sub-region render
        // for proper tile-level efficiency.
        let full = self.render_page(page_index, 150.0)?;
        // For now, return a placeholder buffer at the requested size.
        // The tile engine will call this with real coordinates in Phase 1.
        Ok(RgbaBuffer::new(pixel_width, pixel_height))
    }
}
