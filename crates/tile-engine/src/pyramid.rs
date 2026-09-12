//! Tile Coordinates and Mip Pyramid geometry for 256×256 px tiles.

use pdf_kernel::PageDimensions;

/// Standard tile dimension in device pixels.
pub const TILE_SIZE: u32 = 256;

/// Unique coordinate for a single tile across the entire document pyramid.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct TileCoord {
    /// 0-indexed page number.
    pub page: usize,
    /// Mip level:
    /// - 0: Full fidelity base (150 DPI baseline)
    /// - 1: Half resolution (75 DPI)
    /// - 2: Quarter resolution (37.5 DPI)
    /// - 3: Thumbnail resolution (18.75 DPI)
    pub mip_level: u8,
    /// Column in the page tile grid.
    pub col: u16,
    /// Row in the page tile grid.
    pub row: u16,
}

impl TileCoord {
    pub fn new(page: usize, mip_level: u8, col: u16, row: u16) -> Self {
        Self {
            page,
            mip_level,
            col,
            row,
        }
    }
}

/// Computes tile grid dimensions and pixel coverage per mip level.
#[derive(Debug, Clone)]
pub struct MipPyramid {
    pub page_index: usize,
    pub dimensions: PageDimensions,
    pub base_dpi: f32,
}

impl MipPyramid {
    pub fn new(page_index: usize, dimensions: PageDimensions, base_dpi: f32) -> Self {
        Self {
            page_index,
            dimensions,
            base_dpi,
        }
    }

    /// DPI for a given mip level. Mip 0 = base_dpi, Mip 1 = base_dpi / 2, etc.
    pub fn dpi_for_mip(&self, mip: u8) -> f32 {
        self.base_dpi / (1 << mip) as f32
    }

    /// Full pixel size of the page at this mip level.
    pub fn pixel_dimensions(&self, mip: u8) -> (u32, u32) {
        let dpi = self.dpi_for_mip(mip);
        self.dimensions.to_pixels(dpi)
    }

    /// Grid size (columns, rows) in 256×256 tiles at this mip level.
    pub fn tile_grid_dimensions(&self, mip: u8) -> (u16, u16) {
        let (px_w, px_h) = self.pixel_dimensions(mip);
        let cols = (px_w + TILE_SIZE - 1) / TILE_SIZE;
        let rows = (px_h + TILE_SIZE - 1) / TILE_SIZE;
        (cols as u16, rows as u16)
    }

    /// Rectangular region of the page covered by this tile, in PDF points.
    /// (x_pt, y_pt, w_pt, h_pt)
    pub fn tile_rect_points(&self, coord: &TileCoord) -> (f64, f64, f64, f64) {
        let (total_px_w, total_px_h) = self.pixel_dimensions(coord.mip_level);
        let tile_x_px = (coord.col as u32) * TILE_SIZE;
        let tile_y_px = (coord.row as u32) * TILE_SIZE;

        let w_px = TILE_SIZE.min(total_px_w.saturating_sub(tile_x_px));
        let h_px = TILE_SIZE.min(total_px_h.saturating_sub(tile_y_px));

        let pt_per_px_x = self.dimensions.width_points / (total_px_w as f64);
        let pt_per_px_y = self.dimensions.height_points / (total_px_h as f64);

        let x_pt = (tile_x_px as f64) * pt_per_px_x;
        let y_pt = (tile_y_px as f64) * pt_per_px_y;
        let w_pt = (w_px as f64) * pt_per_px_x;
        let h_pt = (h_px as f64) * pt_per_px_y;

        (x_pt, y_pt, w_pt, h_pt)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_mip_pyramid_grid() {
        // Standard Letter: 612 × 792 pt
        let dims = PageDimensions {
            width_points: 612.0,
            height_points: 792.0,
        };
        let pyramid = MipPyramid::new(0, dims, 150.0);

        // At 150 DPI: (612 * 150 / 72) = 1275 px, (792 * 150 / 72) = 1650 px
        let (cols0, rows0) = pyramid.tile_grid_dimensions(0);
        // ceil(1275 / 256) = 5 cols, ceil(1650 / 256) = 7 rows
        assert_eq!(cols0, 5);
        assert_eq!(rows0, 7);

        // At Mip 1 (75 DPI): half pixels -> ~638 × 825 px
        let (cols1, rows1) = pyramid.tile_grid_dimensions(1);
        assert_eq!(cols1, 3);
        assert_eq!(rows1, 4);
    }
}
