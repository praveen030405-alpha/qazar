package com.qazar.pdfviewer.ui.viewer.text

import com.qazar.pdfviewer.bridge.ExtractedWord
import com.qazar.pdfviewer.bridge.PageTextHierarchy
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe high-performance caching orchestrator for page text hierarchies and spatial indexes.
 */
object TextExtractionEngine {
    private val hierarchyCache = ConcurrentHashMap<Int, PageTextHierarchy>()
    private val indexCache = ConcurrentHashMap<Int, SpatialTextIndex>()

    /**
     * Registers extracted text for a page and eagerly builds the O(1) spatial index.
     */
    fun registerPageText(pageIndex: Int, hierarchy: PageTextHierarchy, pageWidth: Float, pageHeight: Float) {
        hierarchyCache[pageIndex] = hierarchy
        if (hierarchy.words.isNotEmpty()) {
            indexCache[pageIndex] = SpatialTextIndex(
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                words = hierarchy.words
            )
        }
    }

    fun getHierarchy(pageIndex: Int): PageTextHierarchy? = hierarchyCache[pageIndex]

    fun getWords(pageIndex: Int): List<ExtractedWord>? = hierarchyCache[pageIndex]?.words

    /**
     * Constant-time exact hit test for a point in PDF coordinate space.
     */
    fun hitTest(pageIndex: Int, pdfX: Float, pdfY: Float): Pair<ExtractedWord, Int>? {
        return indexCache[pageIndex]?.hitTest(pdfX, pdfY)
    }

    /**
     * Constant-time nearest-word hit test with tolerance for finger touches.
     */
    fun hitTestNearest(pageIndex: Int, pdfX: Float, pdfY: Float, maxDistance: Float = 20f): Pair<ExtractedWord, Int>? {
        return indexCache[pageIndex]?.hitTestNearest(pdfX, pdfY, maxDistance)
    }

    /**
     * Clears all cached text hierarchies and indexes (e.g. when opening a new document).
     */
    fun clear() {
        hierarchyCache.clear()
        indexCache.clear()
    }
}
