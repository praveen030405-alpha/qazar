package com.qazar.pdfviewer.ui.viewer

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qazar.pdfviewer.bridge.RedactionArea
import com.qazar.pdfviewer.bridge.SignatureInfo
import com.qazar.pdfviewer.data.AnnotationStorageManager
import com.qazar.pdfviewer.data.RealPdfLoader
import com.qazar.pdfviewer.theme.ViewingMode
import com.qazar.pdfviewer.ui.viewer.ComposeStroke
import com.qazar.pdfviewer.ui.viewer.ComposeRectAnnotation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PdfViewport(
    context: Context,
    docKey: String,
    documentPath: String?,
    listState: LazyListState,
    pagerState: androidx.compose.foundation.pager.PagerState,
    layoutMode: LayoutMode,
    loadedPages: List<PdfPageModel>,
    transformState: ViewportHardwareTransformState,
    studioState: AnnotationStudioState,
    searchHits: List<SearchHitModel>,
    currentSearchHitIndex: Int,
    searchQuery: String,
    bookmarkedPages: Set<Int>,
    activeRedactions: List<RedactionArea>,
    activeSignatures: List<SignatureInfo>,
    pageStrokes: Map<Int, List<ComposeStroke>>,
    pageRectangles: Map<Int, List<ComposeRectAnnotation>>,
    viewingMode: ViewingMode,
    topPaddingAnim: Dp,
    textSelectionState: TextSelectionState,
    ttsState: com.qazar.pdfviewer.data.tools.TtsReaderManager.TtsState = com.qazar.pdfviewer.data.tools.TtsReaderManager.TtsState(),
    isSettled: Boolean = true,
    onStrokesUpdated: (Map<Int, List<ComposeStroke>>) -> Unit,
    onRectanglesUpdated: (Map<Int, List<ComposeRectAnnotation>>) -> Unit,
    onStudioStateUpdated: (AnnotationStudioState) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onStartDrawing: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val onAutoScroll: (Float) -> Unit = { delta ->
        coroutineScope.launch {
            listState.scrollBy(delta)
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    if (layoutMode == LayoutMode.CONTINUOUS_VERTICAL) {
        LazyColumn(
            state = listState,
            userScrollEnabled = (!transformState.isZoomed && studioState.activeTool == AnnotationTool.PAN),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            contentPadding = PaddingValues(0.dp)
        ) {
            itemsIndexed(
                items = loadedPages,
                key = { _, page -> page.pageIndex },
                contentType = { _, _ -> "pdf_page" }
            ) { index, page ->
                val paddingTop = if (index == 0) topPaddingAnim else 0.dp
                val paddingBottom = if (index == loadedPages.lastIndex) 0.dp else 1.dp
                val pageAspect = if (page.widthPoints > 0f) page.heightPoints / page.widthPoints else 1.4142f
                val slotHeight = (screenWidthDp.value * pageAspect).dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(slotHeight + paddingTop + paddingBottom)
                        .padding(top = paddingTop, bottom = paddingBottom), // Exactly 1-point gap between pages, zero at end
                    contentAlignment = Alignment.Center
                ) {
                    A4PageViewWrapper(
                        context = context, docKey = docKey, documentPath = documentPath, page = page,
                        transformState = transformState, studioState = studioState, searchHits = searchHits,
                        currentSearchHitIndex = currentSearchHitIndex, searchQuery = searchQuery,
                        bookmarkedPages = bookmarkedPages, activeRedactions = activeRedactions,
                        activeSignatures = activeSignatures, pageStrokes = pageStrokes,
                        pageRectangles = pageRectangles, viewingMode = viewingMode,
                        textSelectionState = textSelectionState,
                        ttsState = ttsState,
                        isSettled = isSettled,
                        slotHeight = slotHeight,
                        onAutoScroll = onAutoScroll,
                        onStrokesUpdated = onStrokesUpdated, onRectanglesUpdated = onRectanglesUpdated,
                        onStudioStateUpdated = onStudioStateUpdated, onToggleBookmark = onToggleBookmark,
                        onStartDrawing = onStartDrawing,
                        modifier = Modifier.fillMaxWidth().height(slotHeight)
                    )
                }
            }
        }
    } else if (layoutMode == LayoutMode.SINGLE_PAGE_HORIZONTAL) {
        // SINGLE_PAGE_HORIZONTAL â€” Kindle-style 3D Page Curl Transition
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = (!transformState.isZoomed && studioState.activeTool == AnnotationTool.PAN),
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            contentPadding = PaddingValues(0.dp)
        ) { pageIndex ->
            if (pageIndex in loadedPages.indices) {
                val page = loadedPages[pageIndex]
                val pageAspect = if (page.widthPoints > 0f) page.heightPoints / page.widthPoints else 1.4142f
                val availW = screenWidthDp
                val availH = screenHeightDp - 16.dp

                val cardW = if ((availW - 16.dp) * pageAspect <= availH) {
                    availW
                } else {
                    ((availH / pageAspect) + 16.dp).coerceAtMost(availW)
                }

                // 3D Page Curl: Calculate page offset for realistic fold effect
                val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                val absOffset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Realistic camera distance for 3D perspective
                            cameraDistance = 12f * density

                            // Y-axis rotation creates the page fold/curl effect
                            rotationY = pageOffset * -30f // Negative for natural book page turning

                            // Transform origin anchored at the spine (binding edge)
                            transformOrigin = if (pageOffset < 0f) {
                                androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f) // Right edge = spine for left-swiped pages
                            } else {
                                androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) // Left edge = spine for right-swiped pages
                            }

                            // Subtle scale reduction as page folds away
                            scaleX = 1f - (absOffset * 0.08f)
                            scaleY = 1f - (absOffset * 0.05f)

                            // Fade shadow effect on the folding page
                            alpha = 1f - (absOffset * 0.3f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Fold shadow gradient on the leading edge
                    if (absOffset > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = if (pageOffset > 0f) {
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(Color(0x40000000), Color.Transparent),
                                            startX = 0f,
                                            endX = absOffset * 200f
                                        )
                                    } else {
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color(0x40000000)),
                                            endX = Float.MAX_VALUE,
                                            startX = Float.MAX_VALUE - absOffset * 200f
                                        )
                                    }
                                )
                        )
                    }

                    A4PageViewWrapper(
                        context = context, docKey = docKey, documentPath = documentPath, page = page,
                        transformState = transformState, studioState = studioState, searchHits = searchHits,
                        currentSearchHitIndex = currentSearchHitIndex, searchQuery = searchQuery,
                        bookmarkedPages = bookmarkedPages, activeRedactions = activeRedactions,
                        activeSignatures = activeSignatures, pageStrokes = pageStrokes,
                        pageRectangles = pageRectangles, viewingMode = viewingMode,
                        textSelectionState = textSelectionState,
                        ttsState = ttsState,
                        isSettled = isSettled,
                        onAutoScroll = onAutoScroll,
                        onStrokesUpdated = onStrokesUpdated, onRectanglesUpdated = onRectanglesUpdated,
                        onStudioStateUpdated = onStudioStateUpdated, onToggleBookmark = onToggleBookmark,
                        onStartDrawing = onStartDrawing,
                        modifier = Modifier.width(cardW)
                    )
                }
            }
        }
    } else {
        // DOUBLE_PAGE_SPREAD — Book-style dual-page view with 3D fold transition
        val spreadPageCount = (loadedPages.size + 1) / 2 // Ceiling division for spreads
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = (!transformState.isZoomed && studioState.activeTool == AnnotationTool.PAN),
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            contentPadding = PaddingValues(0.dp)
        ) { spreadIndex ->
            val leftPageIdx = spreadIndex * 2
            val rightPageIdx = leftPageIdx + 1

            // 3D fold transition for the spread
            val pageOffset = (pagerState.currentPage - spreadIndex) + pagerState.currentPageOffsetFraction
            val absOffset = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        cameraDistance = 12f * density
                        rotationY = pageOffset * -25f
                        transformOrigin = if (pageOffset < 0f) {
                            androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                        } else {
                            androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        scaleX = 1f - (absOffset * 0.06f)
                        scaleY = 1f - (absOffset * 0.04f)
                        alpha = 1f - (absOffset * 0.25f)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left page
                    if (leftPageIdx in loadedPages.indices) {
                        val leftPage = loadedPages[leftPageIdx]
                        val halfW = screenWidthDp / 2f
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                            A4PageViewWrapper(
                                context = context, docKey = docKey, documentPath = documentPath, page = leftPage,
                                transformState = transformState, studioState = studioState, searchHits = searchHits,
                                currentSearchHitIndex = currentSearchHitIndex, searchQuery = searchQuery,
                                bookmarkedPages = bookmarkedPages, activeRedactions = activeRedactions,
                                activeSignatures = activeSignatures, pageStrokes = pageStrokes,
                                pageRectangles = pageRectangles, viewingMode = viewingMode,
                                textSelectionState = textSelectionState,
                                ttsState = ttsState,
                                isSettled = isSettled,
                                onAutoScroll = onAutoScroll,
                                onStrokesUpdated = onStrokesUpdated, onRectanglesUpdated = onRectanglesUpdated,
                                onStudioStateUpdated = onStudioStateUpdated, onToggleBookmark = onToggleBookmark,
                                onStartDrawing = onStartDrawing,
                                modifier = Modifier.width(halfW - 2.dp)
                            )
                        }
                    }

                    // Book binding spine shadow
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    listOf(Color(0x30000000), Color(0x60000000), Color(0x30000000))
                                )
                            )
                    )

                    // Right page
                    if (rightPageIdx in loadedPages.indices) {
                        val rightPage = loadedPages[rightPageIdx]
                        val halfW = screenWidthDp / 2f
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                            A4PageViewWrapper(
                                context = context, docKey = docKey, documentPath = documentPath, page = rightPage,
                                transformState = transformState, studioState = studioState, searchHits = searchHits,
                                currentSearchHitIndex = currentSearchHitIndex, searchQuery = searchQuery,
                                bookmarkedPages = bookmarkedPages, activeRedactions = activeRedactions,
                                activeSignatures = activeSignatures, pageStrokes = pageStrokes,
                                pageRectangles = pageRectangles, viewingMode = viewingMode,
                                textSelectionState = textSelectionState,
                                ttsState = ttsState,
                                isSettled = isSettled,
                                onAutoScroll = onAutoScroll,
                                onStrokesUpdated = onStrokesUpdated, onRectanglesUpdated = onRectanglesUpdated,
                                onStudioStateUpdated = onStudioStateUpdated, onToggleBookmark = onToggleBookmark,
                                onStartDrawing = onStartDrawing,
                                modifier = Modifier.width(halfW - 2.dp)
                            )
                        }
                    } else {
                        // Empty right page placeholder (odd page count)
                        Box(modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

@Composable
private fun A4PageViewWrapper(
    context: Context, docKey: String, documentPath: String?, page: PdfPageModel,
    transformState: ViewportHardwareTransformState, studioState: AnnotationStudioState,
    searchHits: List<SearchHitModel>, currentSearchHitIndex: Int, searchQuery: String,
    bookmarkedPages: Set<Int>, activeRedactions: List<RedactionArea>, activeSignatures: List<SignatureInfo>,
    pageStrokes: Map<Int, List<ComposeStroke>>, pageRectangles: Map<Int, List<ComposeRectAnnotation>>,
    viewingMode: ViewingMode, textSelectionState: TextSelectionState,
    ttsState: com.qazar.pdfviewer.data.tools.TtsReaderManager.TtsState = com.qazar.pdfviewer.data.tools.TtsReaderManager.TtsState(),
    isSettled: Boolean = true,
    slotHeight: Dp? = null,
    onAutoScroll: (Float) -> Unit = {},
    onStartDrawing: () -> Unit = {},
    onStrokesUpdated: (Map<Int, List<ComposeStroke>>) -> Unit,
    onRectanglesUpdated: (Map<Int, List<ComposeRectAnnotation>>) -> Unit,
    onStudioStateUpdated: (AnnotationStudioState) -> Unit, onToggleBookmark: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageText by remember(page.pageIndex) { mutableStateOf<com.qazar.pdfviewer.bridge.PageTextHierarchy?>(null) }
    // Only extract text when fully settled to ensure zero CPU contention with rendering
    LaunchedEffect(page.pageIndex, isSettled) {
        if (pageText == null && isSettled) {
            kotlinx.coroutines.delay(1000)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val extracted = RealPdfLoader.getPageText(page.pageIndex)
                pageText = extracted
                if (extracted != null) {
                    com.qazar.pdfviewer.ui.viewer.text.TextExtractionEngine.registerPageText(
                        pageIndex = page.pageIndex,
                        hierarchy = extracted,
                        pageWidth = page.widthPoints,
                        pageHeight = page.heightPoints
                    )
                    textSelectionState.feedPageWords(page.pageIndex, extracted.words)
                }
            }
        }
    }

    val currentStrokesMap by rememberUpdatedState(pageStrokes)
    val currentRectsMap by rememberUpdatedState(pageRectangles)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        A4PageView(
            page = page,
            filePath = documentPath,
            settledScale = transformState.settledScale,
            isSettled = isSettled,
            isGestureActive = transformState.isGestureActive,
            searchHighlights = searchHits,
            activeSearchHitIndex = if (searchQuery.isNotEmpty()) currentSearchHitIndex else -1,
            tool = studioState.activeTool,
            currentColor = studioState.selectedColor,
            currentStrokeWidth = studioState.strokeWidth,
            strokes = pageStrokes[page.pageIndex] ?: emptyList(),
            rectangles = pageRectangles[page.pageIndex] ?: emptyList(),
            redactions = activeRedactions.filter { it.pageIndex == page.pageIndex },
            signatures = activeSignatures.filter { it.pageIndex == page.pageIndex },
            isBookmarked = bookmarkedPages.contains(page.pageIndex),
            onToggleBookmark = { onToggleBookmark(page.pageIndex) },
            viewingMode = viewingMode,
            textSelectionState = textSelectionState,
            ttsState = ttsState,
            slotHeight = slotHeight,
            onAutoScroll = onAutoScroll,
            onStrokeFinished = { stroke ->
                val currentList = currentStrokesMap[page.pageIndex] ?: emptyList()
                val updatedMap = currentStrokesMap + (page.pageIndex to (currentList + stroke))
                onStrokesUpdated(updatedMap)
                AnnotationStorageManager.saveDocAnnotations(context, docKey, updatedMap, currentRectsMap)
                onStudioStateUpdated(studioState.copy(canUndo = true, canRedo = false))

                // Also sync down to Rust CRDT engine
                val pdfPoints = stroke.points.map { com.qazar.pdfviewer.engine.AnnotationController.screenToPdf(it) }
                com.qazar.pdfviewer.engine.AnnotationController.commitAnnotation(pdfPoints)
            },
            onRectFinished = { rect ->
                val currentList = currentRectsMap[page.pageIndex] ?: emptyList()
                val updatedMap = currentRectsMap + (page.pageIndex to (currentList + rect))
                onRectanglesUpdated(updatedMap)
                AnnotationStorageManager.saveDocAnnotations(context, docKey, currentStrokesMap, updatedMap)
                onStudioStateUpdated(studioState.copy(canUndo = true, canRedo = false))
            },
            onEraseStroke = { strokeIndex ->
                val currentList = currentStrokesMap[page.pageIndex] ?: emptyList()
                if (strokeIndex in currentList.indices) {
                    val updated = currentList.toMutableList().apply { removeAt(strokeIndex) }
                    val updatedMap = currentStrokesMap + (page.pageIndex to updated)
                    onStrokesUpdated(updatedMap)
                    AnnotationStorageManager.saveDocAnnotations(context, docKey, updatedMap, currentRectsMap)
                }
            },
            onEraseRect = { rectIndex ->
                val currentList = currentRectsMap[page.pageIndex] ?: emptyList()
                if (rectIndex in currentList.indices) {
                    val updated = currentList.toMutableList().apply { removeAt(rectIndex) }
                    val updatedMap = currentRectsMap + (page.pageIndex to updated)
                    onRectanglesUpdated(updatedMap)
                    AnnotationStorageManager.saveDocAnnotations(context, docKey, currentStrokesMap, updatedMap)
                }
            },
            onUpdateStrokes = { updatedStrokesList ->
                val updatedMap = currentStrokesMap + (page.pageIndex to updatedStrokesList)
                onStrokesUpdated(updatedMap)
                AnnotationStorageManager.saveDocAnnotations(context, docKey, updatedMap, currentRectsMap)
            },
            onUpdateRectangles = { updatedRectsList ->
                val updatedMap = currentRectsMap + (page.pageIndex to updatedRectsList)
                onRectanglesUpdated(updatedMap)
                AnnotationStorageManager.saveDocAnnotations(context, docKey, currentStrokesMap, updatedMap)
            },
            onStartDrawing = onStartDrawing,
            eraserRadius = studioState.eraserRadius,
            isObjectEraser = studioState.isObjectEraser
        )
    }
}
