package com.qazar.pdfviewer.ui.viewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.qazar.pdfviewer.bridge.ExtractedWord
import com.qazar.pdfviewer.bridge.PageTextHierarchy

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import java.util.concurrent.ConcurrentHashMap
import com.qazar.pdfviewer.ui.viewer.text.TextExtractionEngine

/**
 * Global state holding the currently active text selection across the document.
 */
class TextSelectionState {
    var isActive by mutableStateOf(false)
        private set

    // Starting word of the selection
    var startWord: SelectedWordRef? by mutableStateOf(null)
        private set

    // Ending word of the selection
    var endWord: SelectedWordRef? by mutableStateOf(null)
        private set

    // The pixel coordinates of the start and end handles in screen space
    var startHandlePosition by mutableStateOf(Offset.Zero)
    var endHandlePosition by mutableStateOf(Offset.Zero)

    // The exact words that are currently selected, grouped by page
    // This allows the Canvas on each page to render just its own highlights
    var selectedWordsByPage by mutableStateOf<Map<Int, List<ExtractedWord>>>(emptyMap())
        private set

    // Floating contextual action bar visibility & screen coordinates
    var showActionBar by mutableStateOf(false)
    var activePageForMenu by mutableStateOf(-1)
    var selectionScreenBounds by mutableStateOf<Rect?>(null)

    // Set to true when user is holding down to select text or dragging selection handles.
    // When true, hardware viewport panning / scrolling yields completely to text selection.
    var isInteracting by mutableStateOf(false)

    class PageLayoutInfo(
        val pageIndex: Int,
        val coords: LayoutCoordinates,
        val pageWidthPoints: Float,
        val pageHeightPoints: Float
    )

    private val pageLayouts = ConcurrentHashMap<Int, PageLayoutInfo>()

    fun registerPageLayout(pageIndex: Int, coords: LayoutCoordinates, pageWidthPoints: Float, pageHeightPoints: Float) {
        pageLayouts[pageIndex] = PageLayoutInfo(pageIndex, coords, pageWidthPoints, pageHeightPoints)
    }

    fun unregisterPageLayout(pageIndex: Int) {
        pageLayouts.remove(pageIndex)
    }

    /**
     * Hit tests across all visible registered pages in root window space.
     * Returns (pageIndex, (word, wordIndex))
     */
    fun hitTestGlobal(rootPos: Offset): Pair<Int, Pair<ExtractedWord, Int>>? {
        val attachedLayouts = pageLayouts.values.filter { it.coords.isAttached }
        if (attachedLayouts.isEmpty()) return null

        // 1. Direct hit on an attached page bounds
        for (layout in attachedLayouts) {
            val bounds = layout.coords.boundsInRoot()
            if (rootPos.y >= bounds.top && rootPos.y <= bounds.bottom) {
                val localPos = layout.coords.windowToLocal(rootPos)
                val w = layout.coords.size.width.toFloat()
                val h = layout.coords.size.height.toFloat()
                if (w > 0f && h > 0f) {
                    val pdfX = (localPos.x / w).coerceIn(0f, 1f) * layout.pageWidthPoints
                    val pdfY = layout.pageHeightPoints - (localPos.y / h).coerceIn(0f, 1f) * layout.pageHeightPoints
                    val hit = TextExtractionEngine.hitTestNearest(layout.pageIndex, pdfX, pdfY, maxDistance = 48f)
                    if (hit != null) {
                        return layout.pageIndex to hit
                    }
                }
            }
        }

        // 2. Fallback: if dragging below/above visible page, find nearest page
        val sorted = attachedLayouts.sortedBy { it.coords.boundsInRoot().top }
        val topLayout = sorted.firstOrNull()
        val bottomLayout = sorted.lastOrNull()

        if (topLayout != null && rootPos.y < topLayout.coords.boundsInRoot().top) {
            val words = documentWordsByPage[topLayout.pageIndex]
            if (!words.isNullOrEmpty()) {
                return topLayout.pageIndex to (words.first() to 0)
            }
        } else if (bottomLayout != null && rootPos.y > bottomLayout.coords.boundsInRoot().bottom) {
            val words = documentWordsByPage[bottomLayout.pageIndex]
            if (!words.isNullOrEmpty()) {
                return bottomLayout.pageIndex to (words.last() to words.lastIndex)
            }
        }

        return null
    }

    /**
     * Begins a selection drag from a specific word on a specific page.
     */
    fun beginSelection(pageIndex: Int, word: ExtractedWord, wordIndex: Int, handlePos: Offset) {
        isActive = true
        showActionBar = true
        activePageForMenu = pageIndex
        val ref = SelectedWordRef(pageIndex, wordIndex, word)
        startWord = ref
        endWord = ref
        startHandlePosition = handlePos
        endHandlePosition = handlePos
        updateSelectedWords()
    }

    /**
     * Updates the start point of the selection during start-handle drag.
     */
    fun updateSelectionStart(pageIndex: Int, word: ExtractedWord, wordIndex: Int, handlePos: Offset) {
        if (!isActive) return
        startWord = SelectedWordRef(pageIndex, wordIndex, word)
        startHandlePosition = handlePos
        activePageForMenu = pageIndex
        updateSelectedWords()
    }

    /**
     * Updates the end point of the selection during a drag or end-handle drag.
     */
    fun updateSelectionEnd(pageIndex: Int, word: ExtractedWord, wordIndex: Int, handlePos: Offset) {
        if (!isActive) return
        endWord = SelectedWordRef(pageIndex, wordIndex, word)
        endHandlePosition = handlePos
        activePageForMenu = pageIndex
        updateSelectedWords()
    }

    /**
     * Selects all extracted words on the given page.
     */
    fun selectAllOnPage(pageIndex: Int) {
        var words = documentWordsByPage[pageIndex]
        if (words == null) {
            val extracted = com.qazar.pdfviewer.data.RealPdfLoader.getPageText(pageIndex)
            if (extracted != null) {
                feedPageWords(pageIndex, extracted.words)
                words = extracted.words
            }
        }
        if (words.isNullOrEmpty()) return
        isActive = true
        activePageForMenu = pageIndex
        startWord = SelectedWordRef(pageIndex, 0, words.first())
        endWord = SelectedWordRef(pageIndex, words.lastIndex, words.last())
        showActionBar = true
        updateSelectedWords()
    }

    /**
     * Selects all extracted words across the entire document (all pages).
     */
    fun selectAllInDocument(loadedPages: List<PdfPageModel>) {
        if (loadedPages.isEmpty()) return

        for (page in loadedPages) {
            if (documentWordsByPage[page.pageIndex] == null) {
                val extracted = com.qazar.pdfviewer.data.RealPdfLoader.getPageText(page.pageIndex)
                if (extracted != null) {
                    feedPageWords(page.pageIndex, extracted.words)
                }
            }
        }

        val pagesWithWords = loadedPages.mapNotNull { page ->
            val words = documentWordsByPage[page.pageIndex]
            if (!words.isNullOrEmpty()) page.pageIndex to words else null
        }

        if (pagesWithWords.isEmpty()) return

        val (firstPageIdx, firstWords) = pagesWithWords.first()
        val (lastPageIdx, lastWords) = pagesWithWords.last()

        isActive = true
        activePageForMenu = if (activePageForMenu in loadedPages.indices) activePageForMenu else firstPageIdx
        startWord = SelectedWordRef(firstPageIdx, 0, firstWords.first())
        endWord = SelectedWordRef(lastPageIdx, lastWords.lastIndex, lastWords.last())
        showActionBar = true
        updateSelectedWords()
    }

    /**
     * Concatenates all currently selected words in reading order across all pages.
     */
    fun getSelectedText(): String {
        if (!isActive || selectedWordsByPage.isEmpty()) return ""
        return selectedWordsByPage.entries
            .sortedBy { it.key }
            .joinToString(separator = "\n\n") { entry ->
                entry.value.joinToString(separator = " ") { it.text }
            }
    }

    /**
     * Copies the currently selected text to the system clipboard.
     */
    fun copyToClipboard(context: android.content.Context) {
        val text = getSelectedText()
        if (text.isNotBlank()) {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("Meridian PDF Text", text)
            clipboard?.setPrimaryClip(clip)
        }
    }

    fun dismissActionBar() {
        showActionBar = false
    }

    /**
     * Clears the current selection.
     */
    fun clearSelection() {
        isActive = false
        isInteracting = false
        showActionBar = false
        selectionScreenBounds = null
        startWord = null
        endWord = null
        selectedWordsByPage = emptyMap()
    }

    /**
     * Called by the gesture engine when words are loaded for a page.
     * We need all words to determine what sits between `startWord` and `endWord`.
     */
    private var documentWordsByPage = mutableMapOf<Int, List<ExtractedWord>>()

    fun feedPageWords(pageIndex: Int, words: List<ExtractedWord>) {
        documentWordsByPage[pageIndex] = words
    }

    fun getPageWords(pageIndex: Int): List<ExtractedWord>? {
        return documentWordsByPage[pageIndex]
    }

    private fun updateSelectedWords() {
        val start = startWord ?: return
        val end = endWord ?: return

        // Ensure start comes before end chronologically
        val (first, last) = if (
            start.pageIndex < end.pageIndex || 
            (start.pageIndex == end.pageIndex && start.wordIndex <= end.wordIndex)
        ) {
            start to end
        } else {
            end to start
        }

        val newSelection = mutableMapOf<Int, List<ExtractedWord>>()

        for (pageIdx in first.pageIndex..last.pageIndex) {
            val wordsOnPage = documentWordsByPage[pageIdx] ?: continue

            val startIndex = if (pageIdx == first.pageIndex) first.wordIndex else 0
            val endIndex = if (pageIdx == last.pageIndex) last.wordIndex else wordsOnPage.lastIndex

            if (startIndex <= endIndex && startIndex >= 0 && endIndex < wordsOnPage.size) {
                newSelection[pageIdx] = wordsOnPage.subList(startIndex, endIndex + 1)
            }
        }

        selectedWordsByPage = newSelection
    }
}

data class SelectedWordRef(
    val pageIndex: Int,
    val wordIndex: Int,
    val word: ExtractedWord
)
