package com.qazar.pdfviewer.ui.viewer.text

import androidx.compose.ui.geometry.Rect
import com.qazar.pdfviewer.bridge.ExtractedWord
import kotlin.math.hypot

/**
 * High-performance grid-based spatial index for O(1) word hit-testing on a PDF page.
 * Divides the page into cols x rows cells.
 */
class SpatialTextIndex(
    val pageWidth: Float,
    val pageHeight: Float,
    val words: List<ExtractedWord>,
    private val cols: Int = 20,
    private val rows: Int = 28
) {
    private val cellWidth = if (pageWidth > 0f) pageWidth / cols else 1f
    private val cellHeight = if (pageHeight > 0f) pageHeight / rows else 1f

    // For each cell [row][col], store list of word indices
    private val grid: Array<Array<IntArrayList>> = Array(rows) {
        Array(cols) { IntArrayList() }
    }

    init {
        buildIndex()
    }

    private fun buildIndex() {
        for (wIdx in words.indices) {
            val word = words[wIdx]
            val minX = minOf(word.bounds.left, word.bounds.right)
            val maxX = maxOf(word.bounds.left, word.bounds.right)
            val minY = minOf(word.bounds.top, word.bounds.bottom)
            val maxY = maxOf(word.bounds.top, word.bounds.bottom)

            val startCol = ((minX / cellWidth).toInt()).coerceIn(0, cols - 1)
            val endCol = ((maxX / cellWidth).toInt()).coerceIn(0, cols - 1)
            val startRow = ((minY / cellHeight).toInt()).coerceIn(0, rows - 1)
            val endRow = ((maxY / cellHeight).toInt()).coerceIn(0, rows - 1)

            for (r in startRow..endRow) {
                for (c in startCol..endCol) {
                    grid[r][c].add(wIdx)
                }
            }
        }
    }

    /**
     * Hit tests an exact point (pdfX, pdfY). Returns (ExtractedWord, wordIndex) or null.
     */
    fun hitTest(pdfX: Float, pdfY: Float): Pair<ExtractedWord, Int>? {
        if (pdfX < 0f || pdfX > pageWidth || pdfY < 0f || pdfY > pageHeight) {
            return null
        }
        val col = ((pdfX / cellWidth).toInt()).coerceIn(0, cols - 1)
        val row = ((pdfY / cellHeight).toInt()).coerceIn(0, rows - 1)

        val cell = grid[row][col]
        for (i in 0 until cell.size) {
            val wIdx = cell.get(i)
            val word = words[wIdx]
            val minX = minOf(word.bounds.left, word.bounds.right)
            val maxX = maxOf(word.bounds.left, word.bounds.right)
            val minY = minOf(word.bounds.top, word.bounds.bottom)
            val maxY = maxOf(word.bounds.top, word.bounds.bottom)

            if (pdfX in minX..maxX && pdfY in minY..maxY) {
                return word to wIdx
            }
        }
        return null
    }

    /**
     * Finds the nearest word to (pdfX, pdfY) within maxDistance points.
     * Searches the cell and its 8 immediate neighbors.
     */
    fun hitTestNearest(pdfX: Float, pdfY: Float, maxDistance: Float = 20f): Pair<ExtractedWord, Int>? {
        val exact = hitTest(pdfX, pdfY)
        if (exact != null) return exact

        val centerCol = ((pdfX / cellWidth).toInt()).coerceIn(0, cols - 1)
        val centerRow = ((pdfY / cellHeight).toInt()).coerceIn(0, rows - 1)

        var closestWord: ExtractedWord? = null
        var closestIndex = -1
        var minDistance = maxDistance

        for (r in (centerRow - 2).coerceAtLeast(0)..(centerRow + 2).coerceAtMost(rows - 1)) {
            for (c in (centerCol - 2).coerceAtLeast(0)..(centerCol + 2).coerceAtMost(cols - 1)) {
                val cell = grid[r][c]
                for (i in 0 until cell.size) {
                    val wIdx = cell.get(i)
                    val word = words[wIdx]
                    val dist = distanceToRect(pdfX, pdfY, word.bounds)
                    if (dist < minDistance) {
                        minDistance = dist
                        closestWord = word
                        closestIndex = wIdx
                    }
                }
            }
        }

        return if (closestWord != null) closestWord to closestIndex else null
    }

    private fun distanceToRect(x: Float, y: Float, rect: Rect): Float {
        val minX = minOf(rect.left, rect.right)
        val maxX = maxOf(rect.left, rect.right)
        val minY = minOf(rect.top, rect.bottom)
        val maxY = maxOf(rect.top, rect.bottom)

        val dx = when {
            x < minX -> minX - x
            x > maxX -> x - maxX
            else -> 0f
        }
        val dy = when {
            y < minY -> minY - y
            y > maxY -> y - maxY
            else -> 0f
        }
        return hypot(dx, dy)
    }
}

/**
 * Lightweight primitive IntArrayList to avoid Java Integer boxing overhead in hot loops.
 */
class IntArrayList(initialCapacity: Int = 8) {
    private var data = IntArray(initialCapacity)
    var size: Int = 0
        private set

    fun add(value: Int) {
        if (size == data.size) {
            data = data.copyOf(data.size * 2)
        }
        data[size++] = value
    }

    fun get(index: Int): Int = data[index]
}
