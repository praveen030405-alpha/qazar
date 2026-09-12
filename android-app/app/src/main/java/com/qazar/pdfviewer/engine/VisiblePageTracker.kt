package com.qazar.pdfviewer.engine

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import com.qazar.pdfviewer.ui.viewer.LayoutMode

/**
 * M3 Engine: VisiblePageTracker
 * Observes the Compose UI layout state and determines which pages are currently
 * on-screen (visible) and which page is the "dominant" (center) page.
 */
class VisiblePageTracker(
    private val listState: LazyListState,
    private val pagerState: PagerState,
    private val totalPages: () -> Int
) {

    private val _visiblePages = MutableStateFlow<List<Int>>(emptyList())
    val visiblePages: StateFlow<List<Int>> = _visiblePages.asStateFlow()

    private val _dominantPage = MutableStateFlow(0)
    val dominantPage: StateFlow<Int> = _dominantPage.asStateFlow()

    fun update(layoutMode: LayoutMode) {
        val count = totalPages()
        if (count == 0) return
        
        if (layoutMode == LayoutMode.SINGLE_PAGE_HORIZONTAL) {
            val center = pagerState.currentPage.coerceIn(0, count - 1)
            if (_dominantPage.value != center) _dominantPage.value = center
            
            // In Pager mode, usually 1 or 2 pages are visible during swipe
            val visible = mutableListOf(center)
            val offset = pagerState.currentPageOffsetFraction
            if (offset > 0 && center + 1 < count) visible.add(center + 1)
            if (offset < 0 && center - 1 >= 0) visible.add(center - 1)
            
            if (_visiblePages.value != visible) {
                _visiblePages.value = visible
            }
            return
        }

        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo

        if (visibleItems.isEmpty()) return

        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val viewportCenter = (viewportStart + viewportEnd) / 2

        val currentVisible = mutableListOf<Int>()
        var closestToCenter = visibleItems.first()
        var minDistanceToCenter = Int.MAX_VALUE

        for (item in visibleItems) {
            val pageIndex = item.index
            if (pageIndex in 0 until count) {
                currentVisible.add(pageIndex)

                val itemCenter = item.offset + (item.size / 2)
                val distance = abs(itemCenter - viewportCenter)
                if (distance < minDistanceToCenter) {
                    minDistanceToCenter = distance
                    closestToCenter = item
                }
            }
        }

        if (_visiblePages.value != currentVisible) {
            _visiblePages.value = currentVisible
        }
        
        val dominantIndex = closestToCenter.index.coerceIn(0, (count - 1).coerceAtLeast(0))
        if (_dominantPage.value != dominantIndex) {
            _dominantPage.value = dominantIndex
        }
    }
}
