//! Continuous Vertical Page Layout Engine.
//!
//! Arranges pages vertically with configurable inter-page gap spacing.
//! Computes which pages and which specific 256×256 tiles intersect the current viewport.

use pdf_kernel::PageDimensions;
use tile_engine::{MipPyramid, TileCoord, TILE_SIZE};

/// Gap between consecutive pages in layout points.
pub const DEFAULT_PAGE_GAP: f64 = 16.0;

/// Position of a page in continuous layout coordinates.
#[derive(Debug, Clone)]
pub struct LayoutPage {
    pub page_index: usize,
    pub dimensions: PageDimensions,
    pub y_offset: f64,
    pub height: f64,
    pub width: f64,
}

/// Description of a page currently intersecting the viewport.
#[derive(Debug, Clone)]
pub struct VisiblePage {
    pub page_index: usize,
    pub layout_y: f64,
    pub width_pt: f64,
    pub height_pt: f64,
    /// Fraction of page height visible in the viewport [0.0, 1.0].
    pub visible_fraction: f64,
}

/// Description of a specific tile intersecting the viewport.
#[derive(Debug, Clone, PartialEq)]
pub struct VisibleTile {
    pub coord: TileCoord,
    /// Screen-space destination rect in logical pixels: (x, y, w, h).
    pub screen_rect: (f32, f32, f32, f32),
    /// Distance in pixels to viewport edge (used for prioritizing rasterization).
    pub distance_to_viewport: f64,
}

/// Manages continuous vertical document flow.
pub struct ContinuousPageLayout {
    pages: Vec<LayoutPage>,
    total_height: f64,
    max_width: f64,
    page_gap: f64,
}

impl ContinuousPageLayout {
    /// Build continuous layout from page dimensions list.
    pub fn new(page_dims: &[PageDimensions], page_gap: f64) -> Self {
        let mut pages = Vec::with_capacity(page_dims.len());
        let mut current_y = page_gap;
        let mut max_w = 0.0f64;

        for (i, dims) in page_dims.iter().enumerate() {
            max_w = max_w.max(dims.width_points);
            pages.push(LayoutPage {
                page_index: i,
                dimensions: *dims,
                y_offset: current_y,
                width: dims.width_points,
                height: dims.height_points,
            });
            current_y += dims.height_points + page_gap;
        }

        Self {
            pages,
            total_height: current_y,
            max_width: max_w,
            page_gap,
        }
    }

    pub fn total_height(&self) -> f64 {
        self.total_height
    }

    pub fn max_width(&self) -> f64 {
        self.max_width
    }

    pub fn page_gap(&self) -> f64 {
        self.page_gap
    }

    pub fn page_count(&self) -> usize {
        self.pages.len()
    }

    pub fn get_page(&self, index: usize) -> Option<&LayoutPage> {
        self.pages.get(index)
    }

    /// Query which pages intersect the visible vertical range [scroll_y, scroll_y + viewport_height].
    pub fn visible_pages(&self, scroll_y: f64, viewport_height: f64) -> Vec<VisiblePage> {
        let mut visible = Vec::new();
        let view_top = scroll_y;
        let view_bottom = scroll_y + viewport_height;

        for page in &self.pages {
            let page_top = page.y_offset;
            let page_bottom = page.y_offset + page.height;

            if page_bottom >= view_top && page_top <= view_bottom {
                let overlap_top = page_top.max(view_top);
                let overlap_bottom = page_bottom.min(view_bottom);
                let overlap_h = (overlap_bottom - overlap_top).max(0.0);
                let frac = if page.height > 0.0 {
                    overlap_h / page.height
                } else {
                    1.0
                };

                visible.push(VisiblePage {
                    page_index: page.page_index,
                    layout_y: page.y_offset,
                    width_pt: page.width,
                    height_pt: page.height,
                    visible_fraction: frac,
                });
            }
        }

        visible
    }

    /// Query which individual 256×256 tiles intersect the viewport at specified zoom and mip level.
    pub fn visible_tiles(
        &self,
        scroll_x: f64,
        scroll_y: f64,
        viewport_w: f64,
        viewport_h: f64,
        zoom: f32,
        mip_level: u8,
        base_dpi: f32,
    ) -> Vec<VisibleTile> {
        let mut result = Vec::new();
        let vis_pages = self.visible_pages(scroll_y, viewport_h);

        for vp in vis_pages {
            let pyramid = MipPyramid::new(vp.page_index, self.pages[vp.page_index].dimensions, base_dpi);
            let (cols, rows) = pyramid.tile_grid_dimensions(mip_level);
            let (total_px_w, total_px_h) = pyramid.pixel_dimensions(mip_level);

            // Scale from points to viewport pixels including zoom
            let _scale = (base_dpi as f64 / 72.0) * (zoom as f64) / (1 << mip_level) as f64;

            // Center page horizontally if narrower than viewport
            let page_pixel_w = (vp.width_pt * (base_dpi as f64 / 72.0) * zoom as f64) as f64;
            let page_origin_x = if page_pixel_w < viewport_w {
                (viewport_w - page_pixel_w) * 0.5 - scroll_x
            } else {
                -scroll_x
            };
            let page_origin_y = (vp.layout_y - scroll_y) * (zoom as f64 * (base_dpi as f64 / 72.0));

            for r in 0..rows {
                for c in 0..cols {
                    let tile_left = page_origin_x + (c as u32 * TILE_SIZE) as f64;
                    let tile_top = page_origin_y + (r as u32 * TILE_SIZE) as f64;

                    let tile_w = TILE_SIZE.min(total_px_w.saturating_sub(c as u32 * TILE_SIZE)) as f64;
                    let tile_h = TILE_SIZE.min(total_px_h.saturating_sub(r as u32 * TILE_SIZE)) as f64;

                    let tile_right = tile_left + tile_w;
                    let tile_bottom = tile_top + tile_h;

                    // Check intersection with viewport [0, 0, viewport_w, viewport_h]
                    if tile_right >= 0.0
                        && tile_left <= viewport_w
                        && tile_bottom >= 0.0
                        && tile_top <= viewport_h
                    {
                        let dist = if tile_left >= 0.0 && tile_right <= viewport_w && tile_top >= 0.0 && tile_bottom <= viewport_h {
                            0.0
                        } else {
                            10.0
                        };

                        result.push(VisibleTile {
                            coord: TileCoord::new(vp.page_index, mip_level, c, r),
                            screen_rect: (
                                tile_left as f32,
                                tile_top as f32,
                                tile_w as f32,
                                tile_h as f32,
                            ),
                            distance_to_viewport: dist,
                        });
                    }
                }
            }
        }

        result
    }
    /// Create continuous layout where all pages initially use Page 0 dimensions as an estimate.
    /// Used for instant O(1) cold-open of multi-thousand page documents.
    pub fn new_estimated(page_count: usize, p0_dims: PageDimensions, page_gap: f64) -> Self {
        let mut pages = Vec::with_capacity(page_count);
        let mut current_y = page_gap;

        for i in 0..page_count {
            pages.push(LayoutPage {
                page_index: i,
                dimensions: p0_dims,
                y_offset: current_y,
                width: p0_dims.width_points,
                height: p0_dims.height_points,
            });
            current_y += p0_dims.height_points + page_gap;
        }

        Self {
            pages,
            total_height: current_y,
            max_width: p0_dims.width_points,
            page_gap,
        }
    }

    /// Update a page's dimensions and shift subsequent pages accordingly.
    pub fn update_page_dimension(&mut self, page_index: usize, new_dims: PageDimensions) {
        if page_index >= self.pages.len() {
            return;
        }
        let old_height = self.pages[page_index].height;
        let delta_h = new_dims.height_points - old_height;

        self.pages[page_index].dimensions = new_dims;
        self.pages[page_index].width = new_dims.width_points;
        self.pages[page_index].height = new_dims.height_points;
        self.max_width = self.max_width.max(new_dims.width_points);

        if delta_h.abs() > 1e-4 {
            for page in &mut self.pages[(page_index + 1)..] {
                page.y_offset += delta_h;
            }
            self.total_height += delta_h;
        }
    }
}

/// Priority and chunked coordinator for incremental MediaBox resolution.
/// Ensures large documents (e.g. 20,000+ pages) do not lock worker threads,
/// while providing immediate prioritization when users jump or fling to distant pages.
#[derive(Debug, Clone)]
pub struct ChunkedLayoutResolver {
    pub page_count: usize,
    pub chunk_size: usize,
    pub resolved: Vec<bool>,
    pub sequential_chunk_idx: usize,
    pub priority_queue: std::collections::VecDeque<usize>, // Chunk indices requested by viewport jumps
}

impl ChunkedLayoutResolver {
    pub fn new(page_count: usize, chunk_size: usize) -> Self {
        let mut resolved = vec![false; page_count];
        if page_count > 0 {
            // Page 0 is resolved during cold-open
            resolved[0] = true;
        }
        Self {
            page_count,
            chunk_size: chunk_size.max(1),
            resolved,
            sequential_chunk_idx: 0,
            priority_queue: std::collections::VecDeque::new(),
        }
    }

    pub fn total_chunks(&self) -> usize {
        (self.page_count + self.chunk_size - 1) / self.chunk_size
    }

    /// User jumped (e.g. via scrollbar thumb) or high-velocity flung to target_page.
    /// Intercepts the low-priority sequential walk and prioritizes the target chunk.
    pub fn request_priority_jump(&mut self, target_page: usize) {
        if target_page >= self.page_count {
            return;
        }
        let chunk_idx = target_page / self.chunk_size;
        // Preempt: push target chunk to front of priority queue if not already resolved
        if !self.is_chunk_resolved(chunk_idx) && !self.priority_queue.contains(&chunk_idx) {
            self.priority_queue.push_front(chunk_idx);
        }
        // Also lookahead prefetch the adjacent forward chunk if not resolved
        let next_chunk = chunk_idx + 1;
        if next_chunk < self.total_chunks() && !self.is_chunk_resolved(next_chunk) && !self.priority_queue.contains(&next_chunk) {
            self.priority_queue.push_back(next_chunk);
        }
    }

    /// Fetches the next range of pages `(start_page, end_page, is_priority)` to resolve.
    /// Priority requests (jumps/flings) are served before resuming the sequential walk.
    pub fn next_chunk(&mut self) -> Option<(usize, usize, bool)> {
        // 1. High Priority Queue (Interceptions)
        while let Some(chunk_idx) = self.priority_queue.pop_front() {
            if !self.is_chunk_resolved(chunk_idx) {
                let start = chunk_idx * self.chunk_size;
                let end = (start + self.chunk_size).min(self.page_count);
                return Some((start, end, true /* is_priority */));
            }
        }

        // 2. Low Priority Sequential Walk
        let total = self.total_chunks();
        while self.sequential_chunk_idx < total {
            let chunk_idx = self.sequential_chunk_idx;
            self.sequential_chunk_idx += 1;
            if !self.is_chunk_resolved(chunk_idx) {
                let start = chunk_idx * self.chunk_size;
                let end = (start + self.chunk_size).min(self.page_count);
                return Some((start, end, false /* is_priority */));
            }
        }

        None
    }

    /// Mark pages in the resolved range as complete.
    pub fn mark_resolved(&mut self, start_page: usize, end_page: usize) {
        let end = end_page.min(self.page_count);
        for p in start_page..end {
            self.resolved[p] = true;
        }
    }

    pub fn is_chunk_resolved(&self, chunk_idx: usize) -> bool {
        let start = chunk_idx * self.chunk_size;
        let end = (start + self.chunk_size).min(self.page_count);
        if start >= self.page_count {
            return true;
        }
        (start..end).all(|p| self.resolved[p])
    }

    pub fn is_page_resolved(&self, page: usize) -> bool {
        if page >= self.page_count {
            return false;
        }
        self.resolved[page]
    }

    pub fn resolved_count(&self) -> usize {
        self.resolved.iter().filter(|&&r| r).count()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_continuous_layout_visibility() {
        let p1 = PageDimensions {
            width_points: 600.0,
            height_points: 800.0,
        };
        let p2 = PageDimensions {
            width_points: 600.0,
            height_points: 800.0,
        };

        let layout = ContinuousPageLayout::new(&[p1, p2], 20.0);
        // Total: 20 + 800 + 20 + 800 + 20 = 1660
        assert_eq!(layout.total_height(), 1660.0);

        // Viewport at top 400 pt
        let vis = layout.visible_pages(0.0, 400.0);
        assert_eq!(vis.len(), 1);
        assert_eq!(vis[0].page_index, 0);

        // Viewport crossing both pages around y = 810
        let vis_both = layout.visible_pages(700.0, 300.0);
        assert_eq!(vis_both.len(), 2);
    }

    #[test]
    fn test_scrollbar_jump_to_page_2400_deprioritizes_sequential_walk() {
        let page_count = 20_000;
        let chunk_size = 64;
        let mut resolver = ChunkedLayoutResolver::new(page_count, chunk_size);

        // Cold open resolves Page 0
        assert!(resolver.is_page_resolved(0));
        assert_eq!(resolver.resolved_count(), 1);

        // Step 1: Normal sequential background resolution begins at Chunk 0
        let first_chunk = resolver.next_chunk().expect("Should get Chunk 0");
        assert_eq!(first_chunk, (0, 64, false)); // Non-priority
        resolver.mark_resolved(first_chunk.0, first_chunk.1);
        assert!(resolver.is_chunk_resolved(0));

        // Step 2: While sequential walk is at chunk 1 (pages 64..128),
        // user suddenly drags scrollbar thumb directly to Page 2400!
        resolver.request_priority_jump(2400);

        // Step 3: Verify the next chunk yielded is Chunk 37 (containing Page 2400),
        // NOT sequential chunk 1 (64..128)!
        let jump_chunk = resolver.next_chunk().expect("Should yield priority chunk");
        assert_eq!(jump_chunk.2, true); // Verified: is_priority = true
        assert!(2400 >= jump_chunk.0 && 2400 < jump_chunk.1); // Page 2400 is inside this chunk!
        assert_eq!(jump_chunk.0, 37 * 64); // 2368
        assert_eq!(jump_chunk.1, 38 * 64); // 2432

        // Mark Page 2400's chunk resolved
        resolver.mark_resolved(jump_chunk.0, jump_chunk.1);
        assert!(resolver.is_page_resolved(2400));

        // Step 4: Next is the forward lookahead chunk for Page 2400 (pages 2432..2496)
        let lookahead_chunk = resolver.next_chunk().expect("Should yield lookahead chunk");
        assert_eq!(lookahead_chunk.2, true);
        assert_eq!(lookahead_chunk.0, 2432);
        resolver.mark_resolved(lookahead_chunk.0, lookahead_chunk.1);

        // Step 5: Once priority queue is satisfied, worker resumes sequential walk at chunk 1
        let resumed_chunk = resolver.next_chunk().expect("Should resume sequential");
        assert_eq!(resumed_chunk.2, false); // Resumed sequential walk
        assert_eq!(resumed_chunk.0, 64);
        assert_eq!(resumed_chunk.1, 128);
    }
}
