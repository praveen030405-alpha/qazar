package com.qazar.pdfviewer.data

import android.util.Log
import androidx.compose.ui.geometry.Rect
import com.qazar.pdfviewer.bridge.ExtractedWord
import com.qazar.pdfviewer.bridge.PageTextHierarchy
import com.qazar.pdfviewer.ui.viewer.PdfPageModel
import com.qazar.pdfviewer.ui.viewer.SearchHitModel
import com.qazar.pdfviewer.ui.viewer.text.TextExtractionEngine
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/**
 * Ultra-accurate, high-performance PDF text search and extraction engine.
 * - Identifies text with sub-pixel word precision using true PDFium layout coordinates.
 * - Matches words identically to Text Selection (PhantomTextOverlay).
 * - Groups consecutive matching words into seamless continuous line spans.
 * - Multi-tier caching ensures zero-latency subsequent searches.
 */
object DocumentSearchEngine {
    private const val TAG = "DocumentSearchEngine"

    // Caches extracted hierarchies across page searches
    private val pageHierarchyCache = ConcurrentHashMap<String, PageTextHierarchy>()

    data class TextRun(
        val text: String,
        val x: Float,
        val y: Float,
        val fontSize: Float,
        val pageIndex: Int
    )

    private val cachedRuns = ConcurrentHashMap<String, List<TextRun>>()

    /**
     * Executes accurate full-text search across all loaded document pages.
     */
    fun search(
        filePath: String?,
        pages: List<PdfPageModel>,
        query: String
    ): List<SearchHitModel> {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || pages.isEmpty()) return emptyList()

        val hits = mutableListOf<SearchHitModel>()
        val docId = filePath ?: "in_memory_${pages.size}"

        // Tier 1: High-Accuracy Word-Level Search using exact PDF layout coordinates (Like Text Selection)
        try {
            for (page in pages) {
                val cacheKey = "${docId}_p${page.pageIndex}"
                var hierarchy = pageHierarchyCache[cacheKey] ?: TextExtractionEngine.getHierarchy(page.pageIndex)

                if (hierarchy == null) {
                    try {
                        hierarchy = RealPdfLoader.getPageText(page.pageIndex)
                        if (hierarchy != null) {
                            pageHierarchyCache[cacheKey] = hierarchy
                            TextExtractionEngine.registerPageText(
                                pageIndex = page.pageIndex,
                                hierarchy = hierarchy,
                                pageWidth = page.widthPoints,
                                pageHeight = page.heightPoints
                            )
                        }
                    } catch (t: Throwable) {
                        // In mock/test environments or if native bridge is unavailable
                    }
                }

                if (hierarchy != null && hierarchy.words.isNotEmpty()) {
                    val pageHits = findAccurateWordMatches(page, hierarchy, trimmed)
                    hits.addAll(pageHits)
                }
            }

            if (hits.isNotEmpty()) {
                Log.d(TAG, "Accurate word search found ${hits.size} hits across ${pages.size} pages")
                return hits
            }
        } catch (e: Exception) {
            Log.w(TAG, "Word search encountered error, falling back to stream search", e)
        }

        // Tier 2: Universal in-memory text stream search fallback
        try {
            val runs = cachedRuns.getOrPut(docId) {
                extractTextRuns(filePath, pages)
            }

            if (runs.isNotEmpty()) {
                val streamHits = searchInStreamRuns(runs, pages, trimmed)
                if (streamHits.isNotEmpty()) {
                    Log.d(TAG, "Stream parser fallback found ${streamHits.size} hits")
                    return streamHits
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream parser search failed", e)
        }

        return hits
    }

    /**
     * Identifies text matches with sub-pixel word precision matching Text Selection.
     */
    private fun findAccurateWordMatches(
        page: PdfPageModel,
        hierarchy: PageTextHierarchy,
        query: String
    ): List<SearchHitModel> {
        val hits = mutableListOf<SearchHitModel>()
        val qLower = query.lowercase()
        val fullText = hierarchy.fullText
        val fullTextLower = fullText.lowercase()

        // Method A: Full-text substring search mapped to ExtractedWord bounding boxes
        if (fullText.isNotEmpty() && fullTextLower.contains(qLower)) {
            var searchStart = 0
            while (searchStart < fullTextLower.length) {
                val foundIdx = fullTextLower.indexOf(qLower, searchStart)
                if (foundIdx == -1) break

                val matchStart = foundIdx
                val matchEnd = matchStart + qLower.length

                // Find all words overlapping [matchStart, matchEnd]
                val matchedWords = hierarchy.words.filter { w ->
                    w.charRangeStart < matchEnd && w.charRangeEnd > matchStart
                }

                if (matchedWords.isNotEmpty()) {
                    val highlightBounds = groupWordsIntoLineSpans(matchedWords, matchStart, matchEnd)
                    if (highlightBounds.isNotEmpty()) {
                        val sStart = (matchStart - 35).coerceAtLeast(0)
                        val sEnd = (matchEnd + 35).coerceAtMost(fullText.length)
                        val rawSnippet = fullText.substring(sStart, sEnd).replace("\n", " ").trim()
                        val snippet = "...$rawSnippet..."

                        hits.add(
                            SearchHitModel(
                                pageIndex = page.pageIndex,
                                snippet = snippet,
                                highlightBounds = highlightBounds
                            )
                        )
                    }
                }

                searchStart = foundIdx + qLower.length.coerceAtLeast(1)
            }
        }

        // Method B: Direct word-token matching fallback (in case fullText has whitespace or ligature discrepancies)
        if (hits.isEmpty()) {
            val qTokens = qLower.split(Pattern.compile("\\s+")).filter { it.isNotEmpty() }
            if (qTokens.size == 1) {
                val targetToken = qTokens[0]
                for ((wIdx, word) in hierarchy.words.withIndex()) {
                    val wTextLower = word.text.lowercase()
                    var subIdx = 0
                    while (subIdx < wTextLower.length) {
                        val matchInWord = wTextLower.indexOf(targetToken, subIdx)
                        if (matchInWord == -1) break

                        val wLeft = minOf(word.bounds.left, word.bounds.right)
                        val wRight = maxOf(word.bounds.left, word.bounds.right)
                        val wTop = maxOf(word.bounds.top, word.bounds.bottom)
                        val wBottom = minOf(word.bounds.top, word.bounds.bottom)
                        val totalChars = word.text.length.coerceAtLeast(1)
                        val charW = (wRight - wLeft) / totalChars

                        val subLeft = wLeft + (matchInWord * charW)
                        val subRight = subLeft + (targetToken.length * charW)

                        val rect = Rect(
                            left = subLeft.coerceIn(0f, page.widthPoints),
                            top = wTop.coerceIn(0f, page.heightPoints),
                            right = subRight.coerceIn(0f, page.widthPoints),
                            bottom = wBottom.coerceIn(0f, page.heightPoints)
                        )

                        val startW = (wIdx - 4).coerceAtLeast(0)
                        val endW = (wIdx + 5).coerceAtMost(hierarchy.words.size)
                        val snippetWords = hierarchy.words.subList(startW, endW).joinToString(" ") { it.text }

                        hits.add(
                            SearchHitModel(
                                pageIndex = page.pageIndex,
                                snippet = "...$snippetWords...",
                                highlightBounds = listOf(rect)
                            )
                        )

                        subIdx = matchInWord + targetToken.length.coerceAtLeast(1)
                    }
                }
            } else if (qTokens.size > 1 && hierarchy.words.size >= qTokens.size) {
                // Multi-word sequence match
                for (i in 0..(hierarchy.words.size - qTokens.size)) {
                    val matches = qTokens.indices.all { k ->
                        hierarchy.words[i + k].text.lowercase().contains(qTokens[k])
                    }
                    if (matches) {
                        val matchedSlice = hierarchy.words.subList(i, i + qTokens.size)
                        val bounds = groupWordsIntoLineSpans(matchedSlice, -1, -1)
                        if (bounds.isNotEmpty()) {
                            val startW = (i - 3).coerceAtLeast(0)
                            val endW = (i + qTokens.size + 3).coerceAtMost(hierarchy.words.size)
                            val snippetWords = hierarchy.words.subList(startW, endW).joinToString(" ") { it.text }

                            hits.add(
                                SearchHitModel(
                                    pageIndex = page.pageIndex,
                                    snippet = "...$snippetWords...",
                                    highlightBounds = bounds
                                )
                            )
                        }
                    }
                }
            }
        }

        return hits
    }

    /**
     * Groups consecutive words on the same visual line into gapless, continuous highlight bounds.
     * Accurately adjusts the start and end of partial words down to the character level.
     */
    private fun groupWordsIntoLineSpans(
        words: List<ExtractedWord>,
        matchStart: Int,
        matchEnd: Int
    ): List<Rect> {
        if (words.isEmpty()) return emptyList()

        val lineGroups = mutableListOf<MutableList<ExtractedWord>>()
        for (word in words) {
            if (lineGroups.isEmpty()) {
                lineGroups.add(mutableListOf(word))
            } else {
                val currentLine = lineGroups.last()
                val prevWord = currentLine.last()
                val y1Top = maxOf(prevWord.bounds.top, prevWord.bounds.bottom)
                val y1Bottom = minOf(prevWord.bounds.top, prevWord.bounds.bottom)
                val y2Top = maxOf(word.bounds.top, word.bounds.bottom)
                val y2Bottom = minOf(word.bounds.top, word.bounds.bottom)
                val avgH = maxOf(1f, ((y1Top - y1Bottom) + (y2Top - y2Bottom)) / 2f)
                val yCenter1 = (y1Top + y1Bottom) / 2f
                val yCenter2 = (y2Top + y2Bottom) / 2f

                // Words within 45% baseline alignment are on the same visual line
                if (abs(yCenter1 - yCenter2) < avgH * 0.45f) {
                    currentLine.add(word)
                } else {
                    lineGroups.add(mutableListOf(word))
                }
            }
        }

        return lineGroups.map { lineWords ->
            val firstWord = lineWords.first()
            val lastWord = lineWords.last()

            val minX = lineWords.minOf { minOf(it.bounds.left, it.bounds.right) }
            val maxX = lineWords.maxOf { maxOf(it.bounds.left, it.bounds.right) }
            val maxY = lineWords.maxOf { maxOf(it.bounds.top, it.bounds.bottom) }
            val minY = lineWords.minOf { minOf(it.bounds.top, it.bounds.bottom) }

            // Sub-word boundary precision for first word if partial match
            val spanLeft = if (matchStart > firstWord.charRangeStart && firstWord.text.isNotEmpty()) {
                val wLeft = minOf(firstWord.bounds.left, firstWord.bounds.right)
                val wRight = maxOf(firstWord.bounds.left, firstWord.bounds.right)
                val offset = (matchStart - firstWord.charRangeStart).coerceIn(0, firstWord.text.length)
                val charW = (wRight - wLeft) / firstWord.text.length
                wLeft + (offset * charW)
            } else {
                minX
            }

            // Sub-word boundary precision for last word if partial match
            val spanRight = if (matchEnd > 0 && matchEnd < lastWord.charRangeEnd && lastWord.text.isNotEmpty()) {
                val wLeft = minOf(lastWord.bounds.left, lastWord.bounds.right)
                val wRight = maxOf(lastWord.bounds.left, lastWord.bounds.right)
                val offset = (matchEnd - lastWord.charRangeStart).coerceIn(0, lastWord.text.length)
                val charW = (wRight - wLeft) / lastWord.text.length
                wLeft + (offset * charW)
            } else {
                maxX
            }

            Rect(
                left = minOf(spanLeft, spanRight),
                top = maxY,
                right = maxOf(spanLeft, spanRight),
                bottom = minY
            )
        }
    }

    /**
     * Fallback search in raw decompressed PDF text stream runs.
     */
    private fun searchInStreamRuns(
        runs: List<TextRun>,
        pages: List<PdfPageModel>,
        trimmed: String
    ): List<SearchHitModel> {
        val hits = mutableListOf<SearchHitModel>()
        val groupedByPage = runs.groupBy { it.pageIndex }

        for ((pageIdx, pageRuns) in groupedByPage) {
            val pageModel = pages.getOrNull(pageIdx)
            val pageH = pageModel?.heightPoints ?: 842f
            val pageW = pageModel?.widthPoints ?: 595f

            val matchedRects = mutableListOf<Rect>()
            var bestSnippet = ""

            for (run in pageRuns) {
                var searchStart = 0
                val runLower = run.text.lowercase()
                val qLower = trimmed.lowercase()

                while (searchStart < runLower.length) {
                    val idx = runLower.indexOf(qLower, searchStart)
                    if (idx == -1) break

                    val estCharW = (run.fontSize * 0.54f).coerceIn(4f, 24f)
                    val matchLeft = run.x + (idx * estCharW)
                    val matchRight = matchLeft + (trimmed.length * estCharW)
                    val matchTop = run.y + (run.fontSize * 1.05f)
                    val matchBottom = run.y - (run.fontSize * 0.15f)

                    matchedRects.add(
                        Rect(
                            left = matchLeft.coerceIn(0f, pageW),
                            top = matchTop.coerceIn(0f, pageH),
                            right = matchRight.coerceIn(0f, pageW),
                            bottom = matchBottom.coerceIn(0f, pageH)
                        )
                    )

                    if (bestSnippet.isEmpty()) {
                        val s = (idx - 20).coerceAtLeast(0)
                        val e = (idx + trimmed.length + 20).coerceAtMost(run.text.length)
                        bestSnippet = run.text.substring(s, e).trim()
                    }

                    searchStart = idx + trimmed.length
                }
            }

            if (matchedRects.isNotEmpty()) {
                hits.add(
                    SearchHitModel(
                        pageIndex = pageIdx,
                        snippet = if (bestSnippet.isNotEmpty()) "...$bestSnippet..." else "Match on page ${pageIdx + 1}",
                        highlightBounds = matchedRects
                    )
                )
            }
        }
        return hits
    }

    private fun extractTextRuns(filePath: String?, pages: List<PdfPageModel>): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        if (filePath == null) return runs
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) return runs

        try {
            val bytes = file.readBytes()
            val contentStreams = extractAllStreams(bytes)

            for ((streamIdx, streamBytes) in contentStreams.withIndex()) {
                val pageIdx = streamIdx.coerceAtMost(pages.size - 1)
                runs.addAll(parseStreamText(streamBytes, pageIdx))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse PDF text streams from $filePath", e)
        }

        return runs
    }

    private fun extractAllStreams(bytes: ByteArray): List<ByteArray> {
        val streams = mutableListOf<ByteArray>()
        val strPattern = Pattern.compile("stream[\\r\\n]+([\\s\\S]*?)[\\r\\n]+endstream")
        val text = String(bytes, Charsets.ISO_8859_1)
        val matcher = strPattern.matcher(text)

        while (matcher.find()) {
            val start = matcher.start(1)
            val end = matcher.end(1)
            val raw = bytes.copyOfRange(start, end)
            try {
                val decompressed = InflaterInputStream(ByteArrayInputStream(raw)).readBytes()
                if (decompressed.isNotEmpty()) {
                    streams.add(decompressed)
                }
            } catch (e: Exception) {
                streams.add(raw)
            }
        }
        return streams
    }

    private fun parseStreamText(streamBytes: ByteArray, pageIndex: Int): List<TextRun> {
        val runs = mutableListOf<TextRun>()
        val content = String(streamBytes, Charsets.ISO_8859_1)

        val btPattern = Pattern.compile("BT([\\s\\S]*?)ET")
        val btMatcher = btPattern.matcher(content)

        while (btMatcher.find()) {
            val block = btMatcher.group(1) ?: continue
            var curX = 72f
            var curY = 700f
            var curFontSize = 14f

            val lines = block.split("\r\n", "\n", "\r")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.endsWith("Tm")) {
                    val parts = trimmed.split(" ").filter { it.isNotBlank() }
                    if (parts.size >= 6) {
                        curX = parts[4].toFloatOrNull() ?: curX
                        curY = parts[5].toFloatOrNull() ?: curY
                    }
                } else if (trimmed.endsWith("Td") || trimmed.endsWith("TD")) {
                    val parts = trimmed.split(" ").filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        curX += (parts[0].toFloatOrNull() ?: 0f)
                        curY += (parts[1].toFloatOrNull() ?: 0f)
                    }
                } else if (trimmed.endsWith("Tf")) {
                    val parts = trimmed.split(" ").filter { it.isNotBlank() }
                    if (parts.size >= 2) {
                        curFontSize = parts[parts.size - 2].toFloatOrNull() ?: curFontSize
                    }
                }

                if (trimmed.endsWith("Tj") || trimmed.endsWith("TJ")) {
                    val hexPattern = Pattern.compile("<([0-9a-fA-F]+)>")
                    val hexMatcher = hexPattern.matcher(trimmed)
                    val textBuilder = StringBuilder()

                    while (hexMatcher.find()) {
                        val hex = hexMatcher.group(1) ?: continue
                        textBuilder.append(decodeHex(hex))
                    }

                    val litPattern = Pattern.compile("\\(([^)]+)\\)")
                    val litMatcher = litPattern.matcher(trimmed)
                    while (litMatcher.find()) {
                        textBuilder.append(litMatcher.group(1) ?: "")
                    }

                    val runText = textBuilder.toString().trim()
                    if (runText.isNotEmpty()) {
                        runs.add(
                            TextRun(
                                text = runText,
                                x = curX,
                                y = curY,
                                fontSize = curFontSize,
                                pageIndex = pageIndex
                            )
                        )
                    }
                }
            }
        }

        return runs
    }

    private fun decodeHex(hex: String): String {
        return try {
            val sb = StringBuilder()
            var i = 0
            while (i < hex.length - 1) {
                val byteVal = hex.substring(i, i + 2).toInt(16)
                sb.append(byteVal.toChar())
                i += 2
            }
            sb.toString()
        } catch (e: Exception) {
            ""
        }
    }

    fun clearCache() {
        pageHierarchyCache.clear()
        cachedRuns.clear()
    }
}
