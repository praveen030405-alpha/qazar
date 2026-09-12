package com.qazar.pdfviewer.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * M3 Engine: PrefetchManager
 * The Ring-Buffer daemon that anticipates user scrolling and aggressively
 * queues rendering tasks for adjacent pages before they appear on screen.
 */
class PrefetchManager(
    private val filePath: String,
    private val totalPages: Int,
    private val visiblePageTracker: VisiblePageTracker,
    private val viewingMode: com.qazar.pdfviewer.theme.ViewingMode
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var observeJob: Job? = null

    // Defines the prefetch window size: e.g. center - 3 to center + 7
    private val lookBehind = 3
    private val lookAhead = 7

    fun start() {
        if (observeJob?.isActive == true) return
        
        observeJob = scope.launch {
            visiblePageTracker.dominantPage.collectLatest { center ->
                // Cancel old prefetches that are no longer relevant
                RenderPriorityQueue.clearPrefetches()

                // Queue up the new prefetch window
                val window = mutableListOf<Int>()
                for (i in (center - lookBehind)..(center + lookAhead)) {
                    if (i in 0 until totalPages && i != center && !visiblePageTracker.visiblePages.value.contains(i)) {
                        window.add(i)
                    }
                }

                // Sort by distance to center to prioritize immediately adjacent pages
                val prioritized = window.sortedBy { kotlin.math.abs(it - center) }

                for (pageIndex in prioritized) {
                    val isAdjacent = kotlin.math.abs(pageIndex - center) <= 2
                    val priority = if (isAdjacent) RenderPriority.PREFETCH_ADJACENT else RenderPriority.PREFETCH_FAR
                    
                    val cacheKey = "$filePath#$pageIndex#$viewingMode"
                    if (!MemoryBudgetManager.contains(cacheKey)) {
                        RenderPriorityQueue.enqueue(
                            RenderTask(
                                id = cacheKey,
                                filePath = filePath,
                                pageIndex = pageIndex,
                                dpi = DisplayProfileManager.fastScrollDpi,
                                viewingMode = viewingMode,
                                priority = priority,
                                isDeepZoom = false,
                                onResult = { /* Bitmap is cached automatically by Scheduler */ }
                            )
                        )
                    } else {
                        // Update tier of already cached bitmaps to prevent eviction
                        MemoryBudgetManager.updateTier(cacheKey, MemoryBudgetManager.CacheTier.PREFETCH)
                    }
                }
            }
        }
    }

    fun stop() {
        observeJob?.cancel()
        observeJob = null
    }
}
