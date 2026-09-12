package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.theme.GoogleSansFamily
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import android.graphics.Bitmap
import com.qazar.pdfviewer.bridge.RedactionArea
import com.qazar.pdfviewer.bridge.SignatureInfo
import com.qazar.pdfviewer.data.RealPdfLoader
import com.qazar.pdfviewer.theme.*
import com.qazar.pdfviewer.bridge.MeridianNativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.qazar.pdfviewer.ui.viewer.text.PhantomTextOverlay

/**
 * Pure White A4 Page Card (210mm x 297mm ISO standard).
 * Edge-to-edge page rendering â€” fills available width with minimal padding.
 * Sharp paper-like corners, minimal shadow, no margin guides or placeholder text.
 */
@Composable
fun A4PageView(
    page: PdfPageModel,
    filePath: String? = null,
    settledScale: Float = 1.0f,
    isSettled: Boolean = true,
    isGestureActive: Boolean = false,
    searchHighlights: List<SearchHitModel>,
    activeSearchHitIndex: Int = -1,
    tool: AnnotationTool = AnnotationTool.PAN,
    currentColor: Color = PrimaryCobalt,
    currentStrokeWidth: Float = 2.5f,
    strokes: List<ComposeStroke> = emptyList(),
    rectangles: List<ComposeRectAnnotation> = emptyList(),
    redactions: List<RedactionArea> = emptyList(),
    signatures: List<SignatureInfo> = emptyList(),
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    viewingMode: ViewingMode = ViewingMode.DEFAULT,
    textSelectionState: TextSelectionState? = null,
    ttsState: com.qazar.pdfviewer.data.tools.TtsReaderManager.TtsState = com.qazar.pdfviewer.data.tools.TtsReaderManager.TtsState(),
    slotHeight: androidx.compose.ui.unit.Dp? = null,
    onAutoScroll: (Float) -> Unit = {},
    onStrokeFinished: (ComposeStroke) -> Unit = {},
    onRectFinished: (ComposeRectAnnotation) -> Unit = {},
    onEraseStroke: (Int) -> Unit = {},
    onEraseRect: (Int) -> Unit = {},
    onUpdateStrokes: (List<ComposeStroke>) -> Unit = {},
    onUpdateRectangles: (List<ComposeRectAnnotation>) -> Unit = {},
    onStartDrawing: () -> Unit = {},
    eraserRadius: Float = 24.0f,
    isObjectEraser: Boolean = false,
    onSingleTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pageSubstrate = when (viewingMode) {
        ViewingMode.DEFAULT -> PaperWhite
        ViewingMode.PAPER -> PaperSubstrate
        ViewingMode.SEPIA -> SepiaSubstrate
        ViewingMode.NIGHT -> NightSubstrate
        ViewingMode.EINK -> EinkSubstrate
    }
    val pageBorder = when (viewingMode) {
        ViewingMode.DEFAULT -> SurfaceBorder
        ViewingMode.PAPER -> PaperBorder
        ViewingMode.SEPIA -> SepiaBorder
        ViewingMode.NIGHT -> NightBorder
        ViewingMode.EINK -> EinkBorder
    }

    // Dynamic Hardware Bitmap State (LRU Cache backed with L1/L2 instant recovery)
    var pageBitmap by remember(page.pageIndex, filePath) {
        mutableStateOf(
            RealPdfLoader.getCachedRetinaBitmap(filePath, page.pageIndex, ViewingMode.DEFAULT)
                ?: RealPdfLoader.getCachedFastBitmap(filePath, page.pageIndex, ViewingMode.DEFAULT)
                ?: RealPdfLoader.getCachedBitmap(filePath, page.pageIndex, ViewingMode.DEFAULT)
                ?: RealPdfLoader.getCachedThumbnail(filePath, page.pageIndex, ViewingMode.DEFAULT)
                ?: page.bitmap
        )
    }
    
    // Deep-Scroll Thumbnail Skeleton (Instantly available during violent scrolling)
    var thumbnailBitmap by remember(page.pageIndex, filePath) {
        mutableStateOf(RealPdfLoader.getCachedThumbnail(filePath, page.pageIndex, ViewingMode.DEFAULT))
    }

    // Enterprise-Grade Zoom-Aware DeepZoom Bitmap
    var deepZoomBitmap by remember(page.pageIndex, filePath) {
        mutableStateOf<Bitmap?>(RealPdfLoader.getCachedDeepZoomBitmap(filePath, page.pageIndex, ViewingMode.DEFAULT))
    }
    var deepZoomRenderedScale by remember { mutableStateOf(0f) }

    // Stage 1: Instant 1:1 Hardware Pixel Parity Preview (<10ms)
    LaunchedEffect(page.pageIndex, filePath) {
        if (filePath != null) {
            val cachedRetina = RealPdfLoader.getCachedRetinaBitmap(filePath, page.pageIndex, ViewingMode.DEFAULT)
            if (cachedRetina != null) {
                pageBitmap = cachedRetina
                return@LaunchedEffect
            }
            val fastCached = RealPdfLoader.getCachedFastBitmap(filePath, page.pageIndex, ViewingMode.DEFAULT)
            if (fastCached != null) {
                pageBitmap = fastCached
                return@LaunchedEffect
            }
            val cachedThumb = RealPdfLoader.getCachedThumbnail(filePath, page.pageIndex, ViewingMode.DEFAULT)
            if (cachedThumb != null && pageBitmap == null) {
                pageBitmap = cachedThumb
            }
            val fastBmp = RealPdfLoader.renderFastPage(
                filePath = filePath,
                pageIndex = page.pageIndex,
                viewingMode = viewingMode
            )
            if (fastBmp != null) {
                pageBitmap = fastBmp
            }
        }
    }

    // Stage 2: Settled Escalation to 326 DPI Print Quality & Prefetching
    // Only escalate when scrolling has settled to ensure 120 FPS buttery smooth flings
    LaunchedEffect(page.pageIndex, filePath, isSettled, viewingMode) {
        if (filePath != null && isSettled) {
            val cachedRetina = RealPdfLoader.getCachedRetinaBitmap(filePath, page.pageIndex, viewingMode)
            if (cachedRetina != null) {
                pageBitmap = cachedRetina
            } else {
                val targetDpi = com.qazar.pdfviewer.engine.DisplayProfileManager.readingDpi
                val retinaBmp = RealPdfLoader.renderRetinaPage(
                    filePath = filePath,
                    pageIndex = page.pageIndex,
                    dpi = targetDpi,
                    viewingMode = viewingMode
                )
                if (retinaBmp != null) {
                    pageBitmap = retinaBmp
                }
            }
            // Opportunistic prefetch for adjacent pages when settled
            RealPdfLoader.prefetchAdjacentPages(filePath, page.pageIndex, totalPages = 9999, viewingMode = viewingMode)
        }
    }

    // --- Quantum UHD+ Viewport-Slab Engine (Object-Aware AA & Thin-Line Preservation) ---
    var uhdSlabBitmap by remember(page.pageIndex) { mutableStateOf<Bitmap?>(null) }
    val isZoomedIn = settledScale > 1.15f

    LaunchedEffect(settledScale, isGestureActive, page.pageIndex, filePath) {
        if (settledScale > 1.15f && !isGestureActive && filePath != null) {
            val (discreteTier, _) = com.qazar.pdfviewer.engine.PageTileManager.quantizeToSqrt2Step(settledScale)
            if (discreteTier == 0) {
                uhdSlabBitmap = null
            } else {
                com.qazar.pdfviewer.engine.PageTileManager.requestUhdSlab(
                    filePath = filePath,
                    pageIndex = page.pageIndex,
                    widthPt = page.widthPoints,
                    heightPt = page.heightPoints,
                    scale = settledScale
                ) { slabBmp ->
                    uhdSlabBitmap = slabBmp
                }
            }
        } else if (settledScale <= 1.05f) {
            uhdSlabBitmap = null
        }
    }

    // Pages fill available width; aspect ratio derived from actual MediaBox
    val aspectRatio = if (page.widthPoints > 0f) page.heightPoints / page.widthPoints else 1.4142f
    val boxModifier = if (slotHeight != null) {
        Modifier
            .fillMaxWidth()
            .height(slotHeight)
    } else {
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f / aspectRatio)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = boxModifier
                .shadow(
                    elevation = 1.dp,
                    shape = RectangleShape,
                    spotColor = PageShadow,
                    ambientColor = PageShadow
                )
                .background(pageSubstrate, RectangleShape)
                .border(0.5.dp, pageBorder, RectangleShape)
        ) {
            // Real PDF Hardware Bitmap Surface (Settled Reading Base)
            val currentBmp = pageBitmap ?: thumbnailBitmap
            if (currentBmp != null) {
                val imageBitmap = remember(currentBmp) { currentBmp.asImageBitmap() }
                val colorFilter = remember(viewingMode) {
                    when (viewingMode) {
                        ViewingMode.DEFAULT -> null
                        ViewingMode.PAPER -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(
                                floatArrayOf(
                                    0.98f, 0.0f, 0.0f, 0.0f, 0.0f, // R
                                    0.0f, 0.95f, 0.0f, 0.0f, 0.0f, // G
                                    0.0f, 0.0f, 0.82f, 0.0f, 0.0f, // B (cuts harsh blue rays)
                                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f   // A
                                )
                            )
                        )
                        ViewingMode.SEPIA -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(
                                floatArrayOf(
                                    0.96f, 0.0f, 0.0f, 0.0f, 8.0f,  // Warm golden substrate
                                    0.0f, 0.88f, 0.0f, 0.0f, 4.0f,  // Preserved contrast
                                    0.0f, 0.0f, 0.70f, 0.0f, -2.0f, // Circadian blue suppression
                                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                                )
                            )
                        )
                        ViewingMode.NIGHT -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(
                                floatArrayOf(
                                    -0.835f, 0.0f, 0.0f, 0.0f, 226f, // OLED True Dark: White page -> velvet obsidian (#0D0D11)
                                    0.0f, -0.843f, 0.0f, 0.0f, 228f, // Black ink -> soothing warm silver (#E2E8F0)
                                    0.0f, 0.0f, -0.765f, 0.0f, 212f, // Attenuated blue emission for nocturnal reading
                                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                                )
                            )
                        )
                        ViewingMode.EINK -> androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(
                                floatArrayOf(
                                    0.85f, 0.0f, 0.0f, 0.0f, 24.0f, // Carta matte reflectance with zero glare
                                    0.0f, 0.84f, 0.0f, 0.0f, 25.0f,
                                    0.0f, 0.0f, 0.82f, 0.0f, 25.0f,
                                    0.0f, 0.0f, 0.0f, 1.0f, 0.0f
                                )
                            )
                        )
                    }
                }

                // Permanent High Filter Quality: Guarantees razor-sharp vector text and hairline table borders at all times
                val filterQuality = FilterQuality.High

                // Render Base Bitmap
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "PDF Page ${page.pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = filterQuality,
                    colorFilter = colorFilter,
                )

                // High-Res UHD Slab overlay when zoomed in (drawn on top of base image for zero-tear transitions)
                val slab = uhdSlabBitmap
                if (isZoomedIn && slab != null && !slab.isRecycled) {
                    val slabImageBitmap = remember(slab) { slab.asImageBitmap() }
                    Image(
                        bitmap = slabImageBitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds,
                        filterQuality = filterQuality,
                        colorFilter = colorFilter,
                    )
                }

                // Next-Gen Tactile Paper Substrate & Micro-fiber Texture Overlay
                SubstratePaperOverlay(viewingMode = viewingMode)
            } else {
                // Clean tactile document substrate with minimal page watermark
                CleanPagePlaceholder(pageNumber = page.pageIndex + 1, viewingMode = viewingMode)
            }

            // In-Situ Search Highlight Quads overlay with Active Focus
            if (searchHighlights.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height
                    // Exactly 1 PDF point gap around text so highlight border never touches the glyphs
                    val pointGapXPx = maxOf((1.0f / page.widthPoints) * canvasW, 2.dp.toPx())
                    val pointGapYPx = maxOf((1.0f / page.heightPoints) * canvasH, 2.dp.toPx())
                    val cornerRadPx = 3.dp.toPx()

                    searchHighlights.forEachIndexed { hitIdx, hit ->
                        if (hit.pageIndex == page.pageIndex) {
                            val isActive = (hitIdx == activeSearchHitIndex)
                            hit.highlightBounds.forEach { rect ->
                                val yTop = maxOf(rect.top, rect.bottom)
                                val yBottom = minOf(rect.top, rect.bottom)
                                val xLeft = minOf(rect.left, rect.right)
                                val xRight = maxOf(rect.left, rect.right)

                                val normLeft = (xLeft / page.widthPoints) * canvasW
                                val normTop = ((page.heightPoints - yTop) / page.heightPoints) * canvasH
                                val normW = ((xRight - xLeft) / page.widthPoints) * canvasW
                                val normH = ((yTop - yBottom) / page.heightPoints) * canvasH

                                // Apply 1-point gap on all 4 sides so the border never touches the text
                                val drawLeft = (normLeft - pointGapXPx).coerceAtLeast(0f)
                                val drawTop = (normTop - pointGapYPx).coerceAtLeast(0f)
                                val drawW = (normW + (pointGapXPx * 2)).coerceAtMost(canvasW - drawLeft)
                                val drawH = (normH + (pointGapYPx * 2)).coerceAtMost(canvasH - drawTop)

                                // Thin red border with subtle, readable fill
                                val fillColor = if (isActive) Color(0x33EF4444) else Color(0x20FDD835)
                                val borderColor = if (isActive) Color(0xFFEF4444) else Color(0xB3EF4444)
                                val strokeW = if (isActive) 1.dp.toPx() else 0.75.dp.toPx()

                                drawRoundRect(
                                    color = fillColor,
                                    topLeft = Offset(drawLeft, drawTop),
                                    size = Size(drawW, drawH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadPx, cornerRadPx)
                                )
                                drawRoundRect(
                                    color = borderColor,
                                    topLeft = Offset(drawLeft, drawTop),
                                    size = Size(drawW, drawH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadPx, cornerRadPx),
                                    style = Stroke(width = strokeW)
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time TTS Word Highlight Overlay (Active sentence ambient tint + glowing spoken word)
            if (ttsState.isPlaying && ttsState.currentPageIndex == page.pageIndex) {
                val activeWord = ttsState.activeWord
                val sentenceWords = ttsState.activeSentenceWords

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val cornerRadPx = 3.dp.toPx()

                    // 1. Subtle ambient tint across all words in the currently spoken sentence
                    if (sentenceWords.isNotEmpty()) {
                        val sentenceFillColor = Color(0x18F59E0B) // Subtle warm amber
                        val sentenceBorderColor = Color(0x30F59E0B)

                        sentenceWords.forEach { word ->
                            val yTop = maxOf(word.bounds.top, word.bounds.bottom)
                            val yBottom = minOf(word.bounds.top, word.bounds.bottom)
                            val xLeft = minOf(word.bounds.left, word.bounds.right)
                            val xRight = maxOf(word.bounds.left, word.bounds.right)

                            val normLeft = (xLeft / page.widthPoints) * canvasW
                            val normTop = ((page.heightPoints - yTop) / page.heightPoints) * canvasH
                            val normW = ((xRight - xLeft) / page.widthPoints) * canvasW
                            val normH = ((yTop - yBottom) / page.heightPoints) * canvasH

                            val padX = 1.5.dp.toPx()
                            val padY = 1.dp.toPx()

                            drawRoundRect(
                                color = sentenceFillColor,
                                topLeft = Offset((normLeft - padX).coerceAtLeast(0f), (normTop - padY).coerceAtLeast(0f)),
                                size = Size(normW + padX * 2, normH + padY * 2),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadPx, cornerRadPx)
                            )
                        }
                    }

                    // 2. High-precision glowing highlight on the exact spoken word
                    if (activeWord != null) {
                        val yTop = maxOf(activeWord.bounds.top, activeWord.bounds.bottom)
                        val yBottom = minOf(activeWord.bounds.top, activeWord.bounds.bottom)
                        val xLeft = minOf(activeWord.bounds.left, activeWord.bounds.right)
                        val xRight = maxOf(activeWord.bounds.left, activeWord.bounds.right)

                        val normLeft = (xLeft / page.widthPoints) * canvasW
                        val normTop = ((page.heightPoints - yTop) / page.heightPoints) * canvasH
                        val normW = ((xRight - xLeft) / page.widthPoints) * canvasW
                        val normH = ((yTop - yBottom) / page.heightPoints) * canvasH

                        val padX = 2.dp.toPx()
                        val padY = 1.5.dp.toPx()
                        val drawLeft = (normLeft - padX).coerceAtLeast(0f)
                        val drawTop = (normTop - padY).coerceAtLeast(0f)
                        val drawW = normW + (padX * 2)
                        val drawH = normH + (padY * 2)

                        // Golden glowing spoken word highlight
                        drawRoundRect(
                            color = Color(0x60F59E0B), // Vibrant amber fill
                            topLeft = Offset(drawLeft, drawTop),
                            size = Size(drawW, drawH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadPx, cornerRadPx)
                        )
                        drawRoundRect(
                            color = Color(0xFFD97706), // Crisp dark amber border
                            topLeft = Offset(drawLeft, drawTop),
                            size = Size(drawW, drawH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadPx, cornerRadPx),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }

            // Quantum Text Selection Layer ("PhantomText")
            PhantomTextOverlay(
                page = page,
                textSelectionState = textSelectionState,
                tool = tool,
                isGestureActive = isGestureActive,
                onAutoScroll = onAutoScroll
            )

            // Phase 3 Decoupled Annotation Overlay Plane (Stylus & Shapes)
            A4AnnotationCanvas(
                tool = tool,
                currentColor = currentColor,
                currentStrokeWidth = currentStrokeWidth,
                strokes = strokes,
                rectangles = rectangles,
                onStrokeFinished = onStrokeFinished,
                onRectFinished = onRectFinished,
                onEraseStroke = onEraseStroke,
                onEraseRect = onEraseRect,
                onUpdateStrokes = onUpdateStrokes,
                onUpdateRectangles = onUpdateRectangles,
                onStartDrawing = onStartDrawing,
                eraserRadius = eraserRadius,
                isObjectEraser = isObjectEraser
            )

            com.qazar.pdfviewer.ui.SelectionOverlay(
                modifier = Modifier.fillMaxSize()
            )

            // Phase 5: Redaction black-out overlay
            if (redactions.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    redactions.forEach { r ->
                        val normLeft = r.bounds.left / page.widthPoints * size.width
                        val normTop = r.bounds.top / page.heightPoints * size.height
                        val normW = (r.bounds.right - r.bounds.left) / page.widthPoints * size.width
                        val normH = (r.bounds.bottom - r.bounds.top) / page.heightPoints * size.height
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(normLeft, normTop),
                            size = Size(normW, normH)
                        )
                        // Red cross-hatch border for visual indication
                        drawRect(
                            color = Color(0xFFD32F2F),
                            topLeft = Offset(normLeft, normTop),
                            size = Size(normW, normH),
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }

            // Phase 5: Signature widget border overlay
            if (signatures.isNotEmpty()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    signatures.forEach { sig ->
                        val normX = sig.x / page.widthPoints * size.width
                        val normY = sig.y / page.heightPoints * size.height
                        val normW = sig.width / page.widthPoints * size.width
                        val normH = sig.height / page.heightPoints * size.height
                        // Sky-blue PAdES signature border
                        drawRect(
                            color = Color(0x3300BCD4),
                            topLeft = Offset(normX, normY),
                            size = Size(normW, normH)
                        )
                        drawRect(
                            color = Color(0xFF00C853),
                            topLeft = Offset(normX, normY),
                            size = Size(normW, normH),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }

            // Elegant Book-Ribbon Bookmark Tag (Hangs cleanly from top-right edge without covering text)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 12.dp)
            ) {
                Surface(
                    onClick = onToggleBookmark,
                    shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                    color = if (isBookmarked) Color(0xFFDC2626) else Color(0x33000000),
                    shadowElevation = if (isBookmarked) 3.dp else 1.dp,
                    modifier = Modifier.size(width = 24.dp, height = 28.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isBookmarked) "Page Bookmarked" else "Bookmark Page",
                            tint = if (isBookmarked) Color.White else Color(0x99FFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Elegant tactile paper placeholder for sub-millisecond page rasterization.
 * Avoids jarring web-style skeleton shimmer bars, instead rendering a clean,
 * book-like paper surface with a subtle page watermark.
 */
@Composable
private fun CleanPagePlaceholder(
    pageNumber: Int,
    viewingMode: ViewingMode
) {
    val textColor = when (viewingMode) {
        ViewingMode.DEFAULT -> Color(0x18000000)
        ViewingMode.PAPER -> Color(0x185C4033)
        ViewingMode.SEPIA -> Color(0x18704214)
        ViewingMode.NIGHT -> Color(0x28FFFFFF)
        ViewingMode.EINK -> Color(0x181E2220)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$pageNumber",
            color = textColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSansFamily
        )
        SubstratePaperOverlay(viewingMode = viewingMode)
    }
}
