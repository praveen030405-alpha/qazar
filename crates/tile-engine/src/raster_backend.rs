//! RasterBackend trait and caching PDFium implementation.

use parking_lot::Mutex;
use pdf_kernel::{PdfDoc, PdfKernelError, RgbaBuffer};
use std::collections::HashMap;
use std::sync::Arc;
use crate::pyramid::{MipPyramid, TileCoord, TILE_SIZE};

/// Trait abstraction for tile rasterization.
///
/// Architecture ref (Section 2, line 44 / Section 9, line 204):
/// Defines the boundary where PDFium or future GPU compute / Skia rasterizers plug in.
pub trait RasterBackend: Send + Sync {
    /// Rasterize a single 256×256 tile at its coordinate.
    fn rasterize_tile(
        &self,
        doc: &PdfDoc<'_>,
        pyramid: &MipPyramid,
        coord: &TileCoord,
    ) -> Result<RgbaBuffer, PdfKernelError>;
}

/// PDFium-backed tile rasterizer with cached page renders to avoid redundant full-page rasters.
pub struct PdfiumRasterBackend {
    page_cache: Mutex<HashMap<(usize, u8), Arc<RgbaBuffer>>>,
}

impl PdfiumRasterBackend {
    pub fn new() -> Self {
        Self {
            page_cache: Mutex::new(HashMap::new()),
        }
    }

    pub fn clear_cache(&self) {
        self.page_cache.lock().clear();
    }
}

impl Default for PdfiumRasterBackend {
    fn default() -> Self {
        Self::new()
    }
}

impl RasterBackend for PdfiumRasterBackend {
    fn rasterize_tile(
        &self,
        doc: &PdfDoc<'_>,
        pyramid: &MipPyramid,
        coord: &TileCoord,
    ) -> Result<RgbaBuffer, PdfKernelError> {
        let key = (coord.page, coord.mip_level);

        let page_buffer = {
            let mut cache = self.page_cache.lock();
            if let Some(buf) = cache.get(&key) {
                Arc::clone(buf)
            } else {
                let dpi = pyramid.dpi_for_mip(coord.mip_level);
                let rendered = doc.render_page(coord.page, dpi)?;
                let arc_buf = Arc::new(rendered);
                cache.insert(key, Arc::clone(&arc_buf));
                arc_buf
            }
        };

        let (total_w, total_h) = (page_buffer.width, page_buffer.height);
        let tile_x = (coord.col as u32) * TILE_SIZE;
        let tile_y = (coord.row as u32) * TILE_SIZE;

        let tile_w = TILE_SIZE.min(total_w.saturating_sub(tile_x));
        let tile_h = TILE_SIZE.min(total_h.saturating_sub(tile_y));

        let mut tile_data = vec![255u8; (TILE_SIZE * TILE_SIZE * 4) as usize];

        // Blit sub-rectangle of cached full page into tile buffer
        for row in 0..tile_h {
            let src_y = tile_y + row;
            if src_y >= total_h {
                break;
            }
            let src_offset = ((src_y * total_w + tile_x) * 4) as usize;
            let dst_offset = ((row * TILE_SIZE) * 4) as usize;
            let copy_bytes = (tile_w * 4) as usize;

            if src_offset + copy_bytes <= page_buffer.data.len()
                && dst_offset + copy_bytes <= tile_data.len()
            {
                tile_data[dst_offset..dst_offset + copy_bytes]
                    .copy_from_slice(&page_buffer.data[src_offset..src_offset + copy_bytes]);
            }
        }

        Ok(RgbaBuffer {
            width: TILE_SIZE,
            height: TILE_SIZE,
            data: tile_data,
        })
    }
}
