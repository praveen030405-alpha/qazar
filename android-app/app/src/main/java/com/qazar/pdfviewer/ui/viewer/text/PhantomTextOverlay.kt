package com.qazar.pdfviewer.ui.viewer.text

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.qazar.pdfviewer.bridge.ExtractedWord
import com.qazar.pdfviewer.ui.viewer.AnnotationTool
import com.qazar.pdfviewer.ui.viewer.PdfPageModel
import com.qazar.pdfviewer.ui.viewer.TextSelectionState
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration

// Selection styling constants
private val SelectionHighlightColor = Color(0x440055FF) // Standard Android translucent blue
private val SelectionHighlightBorder = Color(0x660055FF) // Subtle highlight edge
private val HandleAccentColor = Color(0xFFEF4444) // Custom app red accent (0xFFEF4444)
private val HandleHaloColor = Color(0x28EF4444) // Subtle touch halo
private val HandleWhite = Color.White

/**
 * PhantomTextOverlay:
 * Renders continuous, gapless line-span highlights and custom red accent handles.
 * Houses the 80ms trajectory analysis gesture detector for zero-conflict touch selection.
 * Reports real-time root screen coordinates for the floating action bar.
 */
@Composable
fun PhantomTextOverlay(
    page: PdfPageModel,
    textSelectionState: TextSelectionState?,
    tool: AnnotationTool = AnnotationTool.PAN,
    isGestureActive: Boolean = false,
    onAutoScroll: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (textSelectionState == null) return

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    // Cached canvas positions of start and end handles on this page
    var startHandlePos by remember { mutableStateOf(Offset.Zero) }
    var endHandlePos by remember { mutableStateOf(Offset.Zero) }
    var hasStartHandle by remember { mutableStateOf(false) }
    var hasEndHandle by remember { mutableStateOf(false) }

    // Track layout coordinates for accurate root screen space positioning
    var pageLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    DisposableEffect(page.pageIndex) {
        onDispose {
            textSelectionState.unregisterPageLayout(page.pageIndex)
        }
    }

    val viewConfiguration = androidx.compose.ui.platform.LocalViewConfiguration.current
    val handleRadiusPx = with(density) { 9.dp.toPx() }
    val handleTouchHitPx = with(density) { 44.dp.toPx() }
    val handleStemHeightPx = with(density) { 14.dp.toPx() }
    val gestureSlopPx = viewConfiguration.touchSlop

    val selectedWords = textSelectionState.selectedWordsByPage[page.pageIndex] ?: emptyList()
    val isSelectionActive = textSelectionState.isActive && selectedWords.isNotEmpty()

    val isFirstPage = textSelectionState.startWord?.pageIndex == page.pageIndex
    val isLastPage = textSelectionState.endWord?.pageIndex == page.pageIndex

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                pageLayoutCoordinates = coords
                textSelectionState.registerPageLayout(
                    pageIndex = page.pageIndex,
                    coords = coords,
                    pageWidthPoints = page.widthPoints,
                    pageHeightPoints = page.heightPoints
                )
            }
            .pointerInput(page.pageIndex, tool, isGestureActive) {
                if (tool != AnnotationTool.PAN || isGestureActive) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val downPos = down.position

                    // 1. Check if user is touching an existing edge handle (Drag to select words)
                    var isDraggingStartHandle = false
                    var isDraggingEndHandle = false

                    if (textSelectionState.isActive) {
                        if (hasStartHandle && (downPos - startHandlePos).getDistance() <= handleTouchHitPx) {
                            isDraggingStartHandle = true
                        } else if (hasEndHandle && (downPos - endHandlePos).getDistance() <= handleTouchHitPx) {
                            isDraggingEndHandle = true
                        }
                    }

                    if (isDraggingStartHandle || isDraggingEndHandle) {
                        down.consume()
                        textSelectionState.isInteracting = true
                        textSelectionState.showActionBar = false
                        var lastWordIdx = if (isDraggingStartHandle) {
                            textSelectionState.startWord?.wordIndex ?: -1
                        } else {
                            textSelectionState.endWord?.wordIndex ?: -1
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()

                                val coords = pageLayoutCoordinates
                                val rootPos = if (coords != null && coords.isAttached) {
                                    coords.localToWindow(change.position)
                                } else {
                                    change.position
                                }

                                // Auto-scroll when dragging near screen edges
                                val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }
                                val bottomThreshold = screenHeightPx - with(density) { 120.dp.toPx() }
                                val topThreshold = with(density) { 120.dp.toPx() }

                                if (rootPos.y > bottomThreshold) {
                                    val factor = ((rootPos.y - bottomThreshold) / with(density) { 60.dp.toPx() }).coerceIn(0.2f, 1.8f)
                                    onAutoScroll(20f * factor)
                                } else if (rootPos.y < topThreshold) {
                                    val factor = ((topThreshold - rootPos.y) / with(density) { 60.dp.toPx() }).coerceIn(0.2f, 1.8f)
                                    onAutoScroll(-20f * factor)
                                }

                                // Hit test word under finger
                                val pdfX = (change.position.x / size.width) * page.widthPoints
                                val pdfY = page.heightPoints - (change.position.y / size.height) * page.heightPoints
                                val hit = TextExtractionEngine.hitTestNearest(page.pageIndex, pdfX, pdfY)

                                if (hit != null && change.position.y >= -10f && change.position.y <= size.height + 10f) {
                                    val (word, wordIdx) = hit
                                    if (wordIdx != lastWordIdx) {
                                        lastWordIdx = wordIdx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (isDraggingStartHandle) {
                                            textSelectionState.updateSelectionStart(page.pageIndex, word, wordIdx, change.position)
                                        } else {
                                            textSelectionState.updateSelectionEnd(page.pageIndex, word, wordIdx, change.position)
                                        }
                                    }
                                } else {
                                    // Cross-page dragging: hit test globally across all pages
                                    val globalHit = textSelectionState.hitTestGlobal(rootPos)
                                    if (globalHit != null) {
                                        val (targetPage, wordPair) = globalHit
                                        val (word, wordIdx) = wordPair
                                        if (isDraggingStartHandle) {
                                            textSelectionState.updateSelectionStart(targetPage, word, wordIdx, change.position)
                                        } else {
                                            textSelectionState.updateSelectionEnd(targetPage, word, wordIdx, change.position)
                                        }
                                    }
                                }
                            }
                        } finally {
                            textSelectionState.isInteracting = false
                            textSelectionState.showActionBar = true
                        }
                        return@awaitEachGesture
                    }

                    // 2. Check if user touched inside currently selected words
                    val isInsideSelectedWords = if (textSelectionState.isActive) {
                        val wordsOnPage = textSelectionState.selectedWordsByPage[page.pageIndex]
                        wordsOnPage?.any { word ->
                            val left = (word.bounds.left / page.widthPoints) * size.width - 10f
                            val right = (word.bounds.right / page.widthPoints) * size.width + 10f
                            val top = (1f - (maxOf(word.bounds.top, word.bounds.bottom) / page.heightPoints)) * size.height - 10f
                            val bottom = (1f - (minOf(word.bounds.top, word.bounds.bottom) / page.heightPoints)) * size.height + 10f
                            downPos.x in left..right && downPos.y in top..bottom
                        } ?: false
                    } else false

                    if (textSelectionState.isActive && isInsideSelectedWords) {
                        // Tapping on already selected words keeps selection and re-shows action bar
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) break
                            if (change.isConsumed || (change.position - downPos).getDistance() > gestureSlopPx || event.changes.size > 1) {
                                moved = true
                                break
                            }
                        }
                        if (!moved) {
                            textSelectionState.showActionBar = true
                        }
                        return@awaitEachGesture
                    }

                    // 3. User is NOT dragging a handle: Wait for stationary Long Press (500ms)
                    // If scrolling, moving, multi-touch, or gesture consumed by scroll viewport -> abort immediately!
                    var movedPastSlop = false
                    var isMulti = false
                    var lifted = false
                    val startTime = System.currentTimeMillis()
                    val longPressDurationMs = 500L

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        if (event.changes.size > 1) {
                            isMulti = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            lifted = true
                            break
                        }
                        if (change.isConsumed || (change.position - downPos).getDistance() > gestureSlopPx) {
                            movedPastSlop = true
                            break
                        }
                        if (System.currentTimeMillis() - startTime >= longPressDurationMs) {
                            break
                        }
                    }

                    if (isMulti || movedPastSlop) {
                        // User is scrolling, panning, or pinch-zooming -> NEVER trigger selection!
                        return@awaitEachGesture
                    }

                    if (lifted) {
                        // Clean quick tap lifted before 500ms
                        if (textSelectionState.isActive && !isInsideSelectedWords) {
                            // Tapping outside selected text dismisses selection!
                            textSelectionState.clearSelection()
                        }
                        return@awaitEachGesture
                    }

                    // 4. Exactly 500ms elapsed and finger remained strictly stationary -> Intentional LONG PRESS!
                    if (TextExtractionEngine.getWords(page.pageIndex) == null) {
                        val extracted = com.qazar.pdfviewer.data.RealPdfLoader.getPageText(page.pageIndex)
                        if (extracted != null) {
                            TextExtractionEngine.registerPageText(
                                pageIndex = page.pageIndex,
                                hierarchy = extracted,
                                pageWidth = page.widthPoints,
                                pageHeight = page.heightPoints
                            )
                            textSelectionState.feedPageWords(page.pageIndex, extracted.words)
                        }
                    }

                    val pdfX = (downPos.x / size.width) * page.widthPoints
                    val pdfY = page.heightPoints - (downPos.y / size.height) * page.heightPoints
                    val hit = TextExtractionEngine.hitTestNearest(page.pageIndex, pdfX, pdfY, maxDistance = 24f)

                    if (hit == null) {
                        // No word here, ignore
                        return@awaitEachGesture
                    }

                    // Word found: Select ONLY this particular word! (No dragging loop here!)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    textSelectionState.beginSelection(page.pageIndex, hit.first, hit.second, downPos)
                    textSelectionState.showActionBar = true
                    textSelectionState.isInteracting = false

                    // Consume remaining hold events until user lifts finger (WITHOUT DRAGGING OR EXPANDING!)
                    try {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                        }
                    } finally {
                        textSelectionState.isInteracting = false
                        textSelectionState.showActionBar = true
                    }
                }
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height
        if (canvasW <= 0f || canvasH <= 0f || page.widthPoints <= 0f || page.heightPoints <= 0f) return@Canvas

        if (!isSelectionActive) {
            hasStartHandle = false
            hasEndHandle = false
            return@Canvas
        }

        // Group words into lines to draw continuous highlights without breaks/spaces between words
        val lineSpans = groupWordsIntoContinuousLineSpans(
            words = selectedWords,
            pageWidthPoints = page.widthPoints,
            pageHeightPoints = page.heightPoints,
            canvasW = canvasW,
            canvasH = canvasH
        )

        // Draw each continuous line highlight
        lineSpans.forEach { span ->
            drawRoundRect(
                color = SelectionHighlightColor,
                topLeft = Offset(span.normLeft, span.normTop),
                size = Size(span.normWidth, span.normHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )

            drawRoundRect(
                color = SelectionHighlightBorder,
                topLeft = Offset(span.normLeft, span.normTop),
                size = Size(span.normWidth, span.normHeight),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = 0.75.dp.toPx())
            )
        }

        // Render Start Handle (Teardrop / Pin) on first selected word
        val firstSpan = lineSpans.firstOrNull()
        if (isFirstPage && firstSpan != null) {
            val stemStartX = firstSpan.firstWordLeft
            val stemStartY = firstSpan.normTop
            val stemEndY = firstSpan.normTop + firstSpan.normHeight
            val bulbCenter = Offset(firstSpan.firstWordLeft - 2.dp.toPx(), stemEndY + handleStemHeightPx)

            drawSelectionHandle(
                bulbCenter = bulbCenter,
                stemStart = Offset(stemStartX, stemStartY),
                stemEnd = Offset(stemStartX, stemEndY),
                radius = handleRadiusPx,
                isStart = true
            )
            startHandlePos = bulbCenter
            hasStartHandle = true
        } else {
            hasStartHandle = false
        }

        // Render End Handle (Teardrop / Pin) on last selected word
        val lastSpan = lineSpans.lastOrNull()
        if (isLastPage && lastSpan != null) {
            val stemStartX = lastSpan.lastWordRight
            val stemStartY = lastSpan.normTop
            val stemEndY = lastSpan.normTop + lastSpan.normHeight
            val bulbCenter = Offset(lastSpan.lastWordRight + 2.dp.toPx(), stemEndY + handleStemHeightPx)

            drawSelectionHandle(
                bulbCenter = bulbCenter,
                stemStart = Offset(stemStartX, stemStartY),
                stemEnd = Offset(stemStartX, stemEndY),
                radius = handleRadiusPx,
                isStart = false
            )
            endHandlePos = bulbCenter
            hasEndHandle = true
        } else {
            hasEndHandle = false
        }
    }

    // Continuously update root screen bounds for the Floating Action Bar
    LaunchedEffect(selectedWords, isSelectionActive, pageLayoutCoordinates) {
        val coords = pageLayoutCoordinates
        if (coords != null && coords.isAttached && isSelectionActive && selectedWords.isNotEmpty()) {
            val rootBounds = coords.boundsInRoot()
            val canvasW = coords.size.width.toFloat()
            val canvasH = coords.size.height.toFloat()

            if (canvasW > 0f && canvasH > 0f && page.widthPoints > 0f && page.heightPoints > 0f) {
                val lineSpans = groupWordsIntoContinuousLineSpans(
                    words = selectedWords,
                    pageWidthPoints = page.widthPoints,
                    pageHeightPoints = page.heightPoints,
                    canvasW = canvasW,
                    canvasH = canvasH
                )

                if (lineSpans.isNotEmpty()) {
                    val minNormLeft = lineSpans.minOf { it.normLeft }
                    val maxNormRight = lineSpans.maxOf { it.normLeft + it.normWidth }
                    val minNormTop = lineSpans.minOf { it.normTop }
                    val maxNormBottom = lineSpans.maxOf { it.normTop + it.normHeight }

                    val rootLeft = rootBounds.left + (minNormLeft / canvasW) * rootBounds.width
                    val rootRight = rootBounds.left + (maxNormRight / canvasW) * rootBounds.width
                    val rootTop = rootBounds.top + (minNormTop / canvasH) * rootBounds.height
                    val rootBottom = rootBounds.top + (maxNormBottom / canvasH) * rootBounds.height

                    textSelectionState.selectionScreenBounds = Rect(rootLeft, rootTop, rootRight, rootBottom)
                }
            }
        }
    }
}

/**
 * Groups consecutive words into continuous visual line spans.
 * This ensures that spaces between words on the same line are highlighted without any gaps.
 */
private fun groupWordsIntoContinuousLineSpans(
    words: List<ExtractedWord>,
    pageWidthPoints: Float,
    pageHeightPoints: Float,
    canvasW: Float,
    canvasH: Float
): List<LineHighlightSpan> {
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
            val h1 = y1Top - y1Bottom
            val h2 = y2Top - y2Bottom
            val avgH = maxOf(1f, (h1 + h2) / 2f)
            val yCenter1 = (y1Top + y1Bottom) / 2f
            val yCenter2 = (y2Top + y2Bottom) / 2f

            // Consider same visual line if vertical centers are within 45% of line height
            if (abs(yCenter1 - yCenter2) < avgH * 0.45f) {
                currentLine.add(word)
            } else {
                lineGroups.add(mutableListOf(word))
            }
        }
    }

    return lineGroups.map { lineWords ->
        val minX = lineWords.minOf { minOf(it.bounds.left, it.bounds.right) }
        val maxX = lineWords.maxOf { maxOf(it.bounds.left, it.bounds.right) }
        val maxY = lineWords.maxOf { maxOf(it.bounds.top, it.bounds.bottom) }
        val minY = lineWords.minOf { minOf(it.bounds.top, it.bounds.bottom) }

        val firstWord = lineWords.first()
        val lastWord = lineWords.last()

        val firstMinX = minOf(firstWord.bounds.left, firstWord.bounds.right)
        val lastMaxX = maxOf(lastWord.bounds.left, lastWord.bounds.right)

        val normLeft = (minX / pageWidthPoints) * canvasW
        val normTop = ((pageHeightPoints - maxY) / pageHeightPoints) * canvasH
        val normWidth = ((maxX - minX) / pageWidthPoints) * canvasW
        val normHeight = ((maxY - minY) / pageHeightPoints) * canvasH

        val firstWordLeft = (firstMinX / pageWidthPoints) * canvasW
        val lastWordRight = (lastMaxX / pageWidthPoints) * canvasW

        LineHighlightSpan(
            normLeft = normLeft,
            normTop = normTop,
            normWidth = normWidth,
            normHeight = normHeight,
            firstWordLeft = firstWordLeft,
            lastWordRight = lastWordRight
        )
    }
}

private data class LineHighlightSpan(
    val normLeft: Float,
    val normTop: Float,
    val normWidth: Float,
    val normHeight: Float,
    val firstWordLeft: Float,
    val lastWordRight: Float
)

/**
 * Draws the custom Red Accent (0xFFEF4444) teardrop selection handle.
 */
private fun DrawScope.drawSelectionHandle(
    bulbCenter: Offset,
    stemStart: Offset,
    stemEnd: Offset,
    radius: Float,
    isStart: Boolean
) {
    // 1. Vertical cursor line
    drawLine(
        color = HandleAccentColor,
        start = stemStart,
        end = stemEnd,
        strokeWidth = 2.2.dp.toPx()
    )

    // 2. Halo for touch affordance
    drawCircle(
        color = HandleHaloColor,
        radius = radius * 1.6f,
        center = bulbCenter
    )

    // 3. Red Accent Handle Bulb
    drawCircle(
        color = HandleAccentColor,
        radius = radius,
        center = bulbCenter
    )

    // 4. White Center Pip
    drawCircle(
        color = HandleWhite,
        radius = radius * 0.38f,
        center = bulbCenter
    )

    // 5. Connecting stem to cursor line
    drawLine(
        color = HandleAccentColor,
        start = stemEnd,
        end = bulbCenter,
        strokeWidth = 1.8.dp.toPx()
    )
}
