package com.qazar.pdfviewer.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.*

data class ComposeStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isHighlighter: Boolean = false,
    val isNormalized: Boolean = true,
    val toolName: String = AnnotationTool.PEN.name
)

data class ComposeRectAnnotation(
    val topLeft: Offset,
    val size: Size,
    val color: Color,
    val strokeWidth: Float,
    val isNormalized: Boolean = true,
    val toolName: String = AnnotationTool.RECTANGLE.name
)

data class TimedPoint(
    val offset: Offset,
    val timeMs: Long
)

/**
 * Flagship A4 Canvas Drawing & Annotation Layer:
 * - Single-finger draws with Catmull-Rom / Bezier smoothing & velocity dynamics
 * - Two-finger gestures are passed through to enable seamless 2-finger panning & scrolling
 * - Automatic Shape Recognition & Snapping (straight lines, clean rectangles, smooth circles)
 * - Real proximity-based eraser
 * - 37+ specialized precision vector rendering modes for every AnnotationTool
 */
@Composable
fun A4AnnotationCanvas(
    tool: AnnotationTool,
    currentColor: Color,
    currentStrokeWidth: Float,
    strokes: List<ComposeStroke>,
    rectangles: List<ComposeRectAnnotation>,
    onStrokeFinished: (ComposeStroke) -> Unit,
    onRectFinished: (ComposeRectAnnotation) -> Unit,
    onEraseStroke: (Int) -> Unit = {},
    onEraseRect: (Int) -> Unit = {},
    onStartDrawing: () -> Unit = {},
    eraserRadius: Float = 36f,
    isObjectEraser: Boolean = false,
    onUpdateStrokes: (List<ComposeStroke>) -> Unit = {},
    onUpdateRectangles: (List<ComposeRectAnnotation>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val updatedOnStrokeFinished by rememberUpdatedState(onStrokeFinished)
    val updatedOnRectFinished by rememberUpdatedState(onRectFinished)
    val updatedOnEraseStroke by rememberUpdatedState(onEraseStroke)
    val updatedOnEraseRect by rememberUpdatedState(onEraseRect)
    val updatedOnStartDrawing by rememberUpdatedState(onStartDrawing)
    val updatedOnUpdateStrokes by rememberUpdatedState(onUpdateStrokes)
    val updatedOnUpdateRectangles by rememberUpdatedState(onUpdateRectangles)
    val updatedStrokes by rememberUpdatedState(strokes)
    val updatedRectangles by rememberUpdatedState(rectangles)
    val updatedColor by rememberUpdatedState(currentColor)
    val updatedWidth by rememberUpdatedState(currentStrokeWidth)
    val updatedTool by rememberUpdatedState(tool)
    val updatedEraserRadius by rememberUpdatedState(eraserRadius)
    val updatedIsObjectEraser by rememberUpdatedState(isObjectEraser)

    val haptic = LocalHapticFeedback.current
    var liveSnappedShape by remember { mutableStateOf<RecognizedShape?>(null) }
    var eraserCursorPos by remember { mutableStateOf<Offset?>(null) }

    // Selection & Move Tool state
    var selectedStrokeIndex by remember { mutableStateOf<Int?>(null) }
    var selectedRectIndex by remember { mutableStateOf<Int?>(null) }
    var liveMoveOffset by remember { mutableStateOf(Offset.Zero) }
    var isDraggingSelection by remember { mutableStateOf(false) }
    var selectPointerDownPos by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(tool) {
        if (tool != AnnotationTool.LASSO_SELECT) {
            selectedStrokeIndex = null
            selectedRectIndex = null
            liveMoveOffset = Offset.Zero
            isDraggingSelection = false
            selectPointerDownPos = null
        }
    }

    var currentTimedPoints by remember { mutableStateOf<List<TimedPoint>>(emptyList()) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    // Multi-finger cooldown timer: eliminates accidental pen dots when fingers lift after 2-finger scroll
    var lastMultiFingerTime by remember { mutableLongStateOf(0L) }

    // Authentic Apple Keynote Laser: Stays visible while writing words/sentences, dissolves 0.5s after inactivity
    var activeLaserStrokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentLaserStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var lastLaserActivityTime by remember { mutableLongStateOf(0L) }
    var isLaserDrawingNow by remember { mutableStateOf(false) }
    var laserDissolveAlpha by remember { mutableFloatStateOf(1f) }

    // 0.5-second inactivity dissolution timer for Apple Laser
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30L)
            val now = System.currentTimeMillis()
            if (!isLaserDrawingNow && activeLaserStrokes.isNotEmpty() && lastLaserActivityTime > 0L) {
                val elapsed = now - lastLaserActivityTime
                if (elapsed >= 500L) {
                    val fadeStart = System.currentTimeMillis()
                    while (true) {
                        val fadeElapsed = System.currentTimeMillis() - fadeStart
                        val progress = (fadeElapsed / 180f).coerceIn(0f, 1f)
                        laserDissolveAlpha = 1f - progress
                        if (progress >= 1f || isLaserDrawingNow) break
                        kotlinx.coroutines.delay(16L)
                    }
                    if (!isLaserDrawingNow) {
                        activeLaserStrokes = emptyList()
                        currentLaserStroke = emptyList()
                        laserDissolveAlpha = 1f
                        lastLaserActivityTime = 0L
                    }
                }
            }
        }
    }

    val isLinearTool = tool in listOf(
        AnnotationTool.LINE,
        AnnotationTool.ARROW,
        AnnotationTool.DOUBLE_ARROW,
        AnnotationTool.RULER,
        AnnotationTool.ANGLE
    )


    val isBoxOrAreaTool = tool in listOf(
        AnnotationTool.RECTANGLE,
        AnnotationTool.ROUNDED_RECT,
        AnnotationTool.CIRCLE,
        AnnotationTool.POLYGON,
        AnnotationTool.CLOUD,
        AnnotationTool.TEXT_BOX,
        AnnotationTool.STICKY_NOTE,
        AnnotationTool.CALLOUT,
        AnnotationTool.COMMENT_BUBBLE,
        AnnotationTool.STAMP,
        AnnotationTool.REDACTION,
        AnnotationTool.WATERMARK,
        AnnotationTool.CERTIFICATE_ID,
        AnnotationTool.AREA_MEASURE,
        AnnotationTool.PERIMETER,
        AnnotationTool.TEXT_HIGHLIGHT,
        AnnotationTool.STRIKETHROUGH,
        AnnotationTool.UNDERLINE,
        AnnotationTool.SQUIGGLY,
        AnnotationTool.CARET
    )

    val isFreehandTool = tool in listOf(
        AnnotationTool.PEN,
        AnnotationTool.HIGHLIGHTER,
        AnnotationTool.MARKER,
        AnnotationTool.FOUNTAIN_PEN,
        AnnotationTool.PENCIL,
        AnnotationTool.LASER_POINTER,
        AnnotationTool.POLYLINE,
        AnnotationTool.SIGNATURE
    )

    val isEraserEnabled = tool.isEraser
    val isSelectTool = tool == AnnotationTool.LASSO_SELECT
    val isGestureActive = isLinearTool || isBoxOrAreaTool || isFreehandTool || isEraserEnabled || isSelectTool

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                canvasSize = Size(it.width.toFloat(), it.height.toFloat())
            }
            .then(
                if (isGestureActive) {
                    Modifier.pointerInput(tool) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                            val downTime = System.currentTimeMillis()

                            // Multi-finger cooldown check: if fingers were scrolling within 240ms, ignore trailing finger release!
                            if (downTime - lastMultiFingerTime < 240L) {
                                down.consume()
                                return@awaitEachGesture
                            }

                            val cW = if (canvasSize.width > 0f) canvasSize.width else 1f
                            val cH = if (canvasSize.height > 0f) canvasSize.height else 1f
                            
                            // Immediately shrink the toolbar into a ball upon ANY tool touch down
                            updatedOnStartDrawing()

                            if (isSelectTool) {
                                selectPointerDownPos = down.position
                                liveMoveOffset = Offset.Zero
                                val p = down.position

                                // 1. Check if tapping already selected item to move it
                                val isOverCurrentStroke = (selectedStrokeIndex != null && selectedStrokeIndex!! in updatedStrokes.indices &&
                                        getStrokeBoundsPx(updatedStrokes[selectedStrokeIndex!!], cW, cH).contains(p))
                                val isOverCurrentRect = (selectedRectIndex != null && selectedRectIndex!! in updatedRectangles.indices &&
                                        getRectBoundsPx(updatedRectangles[selectedRectIndex!!], cW, cH).contains(p))

                                if (isOverCurrentStroke || isOverCurrentRect) {
                                    isDraggingSelection = true
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else {
                                    // 2. Hit-test strokes from top to bottom
                                    var foundStrokeIdx: Int? = null
                                    for (i in updatedStrokes.indices.reversed()) {
                                        val s = updatedStrokes[i]
                                        val bounds = getStrokeBoundsPx(s, cW, cH)
                                        if (bounds.contains(p)) {
                                            foundStrokeIdx = i
                                            break
                                        }
                                    }

                                    if (foundStrokeIdx != null) {
                                        selectedStrokeIndex = foundStrokeIdx
                                        selectedRectIndex = null
                                        isDraggingSelection = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    } else {
                                        // 3. Hit-test rectangles from top to bottom
                                        var foundRectIdx: Int? = null
                                        for (i in updatedRectangles.indices.reversed()) {
                                            val r = updatedRectangles[i]
                                            val bounds = getRectBoundsPx(r, cW, cH)
                                            if (bounds.contains(p)) {
                                                foundRectIdx = i
                                                break
                                            }
                                        }

                                        if (foundRectIdx != null) {
                                            selectedRectIndex = foundRectIdx
                                            selectedStrokeIndex = null
                                            isDraggingSelection = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else {
                                            // Deselect when tapping blank canvas
                                            selectedStrokeIndex = null
                                            selectedRectIndex = null
                                            isDraggingSelection = false
                                        }
                                    }
                                }
                            } else if (isEraserEnabled) {
                                eraserCursorPos = down.position
                                eraseNearby(down.position, updatedStrokes, updatedRectangles, cW, cH, updatedEraserRadius, updatedIsObjectEraser, updatedOnEraseStroke, updatedOnEraseRect)
                            } else if (isLinearTool || isBoxOrAreaTool) {
                                dragStart = down.position
                                dragCurrent = down.position
                            } else {
                                currentTimedPoints = listOf(TimedPoint(down.position, downTime))
                                liveSnappedShape = null
                            }

                            var isMultiFinger = false
                            val strokePoints = mutableListOf<TimedPoint>()
                            strokePoints.add(TimedPoint(down.position, downTime))

                            var isSnapped = false
                            var snappedShape: RecognizedShape? = null
                            var stationaryStartTime = downTime
                            var lastStationaryPos = down.position
                            val holdDurationMs = 1000L
                            var isPointerDown = true

                            if (updatedTool == AnnotationTool.LASER_POINTER) {
                                isLaserDrawingNow = true
                                laserDissolveAlpha = 1f
                                lastLaserActivityTime = downTime
                                currentLaserStroke = listOf(down.position)
                            }

                            do {
                                val now = System.currentTimeMillis()
                                val stationaryElapsed = now - stationaryStartTime
                                val waitTime = if (!isSnapped && isFreehandTool) {
                                    (holdDurationMs - stationaryElapsed).coerceIn(10L, holdDurationMs)
                                } else {
                                    3000L
                                }

                                val event = withTimeoutOrNull(waitTime) {
                                    awaitPointerEvent(pass = PointerEventPass.Main)
                                }

                                if (event == null) {
                                    // Stationary hold timeout reached: snap without lifting!
                                    if (!isSnapped && isFreehandTool && strokePoints.size >= 5) {
                                        val detected = detectShape(strokePoints.map { it.offset })
                                        if (detected !is RecognizedShape.None) {
                                            isSnapped = true
                                            snappedShape = detected
                                            liveSnappedShape = detected
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                    continue
                                }

                                if (event.changes.none { it.pressed }) {
                                    isPointerDown = false
                                    break
                                }

                                val activePointers = event.changes.filter { it.pressed }

                                // 2-Finger Gestures: Cancel drawing stroke immediately and yield to parent scroll/zoom!
                                if (activePointers.size >= 2) {
                                    lastMultiFingerTime = System.currentTimeMillis()
                                    isMultiFinger = true
                                    currentTimedPoints = emptyList()
                                    liveSnappedShape = null
                                    eraserCursorPos = null
                                    dragStart = null
                                    dragCurrent = null
                                    if (isSelectTool) {
                                        liveMoveOffset = Offset.Zero
                                        isDraggingSelection = false
                                        selectPointerDownPos = null
                                    }
                                    if (updatedTool == AnnotationTool.LASER_POINTER) {
                                        isLaserDrawingNow = false
                                        currentLaserStroke = emptyList()
                                    }
                                    break
                                }

                                if (activePointers.size == 1) {
                                    val pointer = activePointers.first()
                                    pointer.consume()
                                    val pPos = pointer.position

                                    if (isSelectTool) {
                                        if (isDraggingSelection && selectPointerDownPos != null) {
                                            liveMoveOffset = pPos - selectPointerDownPos!!
                                        }
                                    } else if (isEraserEnabled) {
                                        eraserCursorPos = pPos
                                        eraseNearby(pPos, updatedStrokes, updatedRectangles, cW, cH, updatedEraserRadius, updatedIsObjectEraser, updatedOnEraseStroke, updatedOnEraseRect)
                                    } else if (isLinearTool || isBoxOrAreaTool) {
                                        dragCurrent = pPos
                                    } else {
                                        // Freehand Tool
                                        if (strokePoints.size == 2) {
                                            // User started marking: auto-shrink toolbar into Annotation Ball!
                                            updatedOnStartDrawing()
                                        }

                                        if (isSnapped && snappedShape != null) {
                                            // User drags after snapped: dynamically resize/rotate the shape with finger!
                                            val updated = updateSnappedShapeWithDrag(snappedShape!!, pPos)
                                            snappedShape = updated
                                            liveSnappedShape = updated
                                        } else {
                                            val moveDist = sqrt((pPos.x - lastStationaryPos.x).pow(2) + (pPos.y - lastStationaryPos.y).pow(2))
                                            if (moveDist > 18f) {
                                                lastStationaryPos = pPos
                                                stationaryStartTime = System.currentTimeMillis()
                                            } else {
                                                if (!isSnapped && (System.currentTimeMillis() - stationaryStartTime) >= holdDurationMs && strokePoints.size >= 5) {
                                                    val detected = detectShape(strokePoints.map { it.offset })
                                                    if (detected !is RecognizedShape.None) {
                                                        isSnapped = true
                                                        snappedShape = detected
                                                        liveSnappedShape = detected
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                }
                                            }
                                            strokePoints.add(TimedPoint(pPos, System.currentTimeMillis()))
                                            if (updatedTool == AnnotationTool.LASER_POINTER) {
                                                isLaserDrawingNow = true
                                                laserDissolveAlpha = 1f
                                                lastLaserActivityTime = System.currentTimeMillis()
                                                currentLaserStroke = currentLaserStroke + pPos
                                            } else {
                                                currentTimedPoints = strokePoints.toList()
                                            }
                                        }
                                    }
                                }
                            } while (isPointerDown)

                            // Handle Completion on Finger Lift
                            if (!isMultiFinger && (System.currentTimeMillis() - lastMultiFingerTime >= 240L)) {
                                if (isSelectTool) {
                                    if (isDraggingSelection && liveMoveOffset != Offset.Zero && (liveMoveOffset.getDistance() > 2f)) {
                                        if (selectedStrokeIndex != null && selectedStrokeIndex!! in updatedStrokes.indices) {
                                            val stroke = updatedStrokes[selectedStrokeIndex!!]
                                            val normDx = liveMoveOffset.x / cW
                                            val normDy = liveMoveOffset.y / cH
                                            val shiftedPts = stroke.points.map { Offset(it.x + normDx, it.y + normDy) }
                                            val updatedList = updatedStrokes.toMutableList().apply {
                                                set(selectedStrokeIndex!!, stroke.copy(points = shiftedPts))
                                            }
                                            updatedOnUpdateStrokes(updatedList)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (selectedRectIndex != null && selectedRectIndex!! in updatedRectangles.indices) {
                                            val rect = updatedRectangles[selectedRectIndex!!]
                                            val normDx = liveMoveOffset.x / cW
                                            val normDy = liveMoveOffset.y / cH
                                            val newTopLeft = Offset(rect.topLeft.x + normDx, rect.topLeft.y + normDy)
                                            val updatedList = updatedRectangles.toMutableList().apply {
                                                set(selectedRectIndex!!, rect.copy(topLeft = newTopLeft))
                                            }
                                            updatedOnUpdateRectangles(updatedList)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    liveMoveOffset = Offset.Zero
                                    isDraggingSelection = false
                                    selectPointerDownPos = null
                                }

                                val start = dragStart
                                val cur = dragCurrent

                                if (isLinearTool && start != null && cur != null) {
                                    val dist = sqrt((cur.x - start.x).pow(2) + (cur.y - start.y).pow(2))
                                    if (dist > 6f) {
                                        val normP0 = Offset(start.x / cW, start.y / cH)
                                        val normP1 = Offset(cur.x / cW, cur.y / cH)
                                        updatedOnStrokeFinished(
                                            ComposeStroke(
                                                points = listOf(normP0, normP1),
                                                color = updatedColor,
                                                strokeWidth = updatedWidth,
                                                isHighlighter = false,
                                                isNormalized = true,
                                                toolName = updatedTool.name
                                            )
                                        )
                                    }
                                    dragStart = null
                                    dragCurrent = null
                                } else if (isBoxOrAreaTool && start != null && cur != null) {
                                    val x = minOf(start.x, cur.x)
                                    val y = minOf(start.y, cur.y)
                                    val w = abs(cur.x - start.x)
                                    val h = abs(cur.y - start.y)
                                    if (w > 6f && h > 6f) {
                                        updatedOnRectFinished(
                                            ComposeRectAnnotation(
                                                topLeft = Offset(x / cW, y / cH),
                                                size = Size(w / cW, h / cH),
                                                color = updatedColor,
                                                strokeWidth = updatedWidth,
                                                isNormalized = true,
                                                toolName = updatedTool.name
                                            )
                                        )
                                    }
                                    dragStart = null
                                    dragCurrent = null
                                } else if (isFreehandTool) {
                                    if (updatedTool == AnnotationTool.LASER_POINTER) {
                                        // Apple Laser: Lift finger preserves stroke in buffer, resets 0.5s inactivity countdown!
                                        if (currentLaserStroke.size >= 2) {
                                            activeLaserStrokes = activeLaserStrokes + listOf(currentLaserStroke)
                                        }
                                        currentLaserStroke = emptyList()
                                        isLaserDrawingNow = false
                                        lastLaserActivityTime = System.currentTimeMillis()
                                    } else if (isSnapped && snappedShape != null) {
                                        // Shape was snapped via hold gesture and sized: commit perfected vector shape
                                        commitSnappedShape(snappedShape!!, cW, cH, updatedColor, updatedWidth, updatedTool, updatedOnStrokeFinished, updatedOnRectFinished)
                                    } else if (strokePoints.size > 1) {
                                        // PURE NATURAL HANDWRITING: smooth with Catmull-Rom, require min distance > 8px
                                        val rawPoints = strokePoints.map { it.offset }
                                        var totalDist = 0f
                                        for (idx in 1 until rawPoints.size) {
                                            totalDist += (rawPoints[idx] - rawPoints[idx - 1]).getDistance()
                                        }
                                        if (totalDist > 8f) {
                                            val smoothed = smoothPointsCatmullRom(rawPoints)
                                            val velocity = calculateAverageVelocity(strokePoints)
                                            val dynamicWidth = when (updatedTool) {
                                                AnnotationTool.HIGHLIGHTER -> updatedWidth * 3.2f
                                                AnnotationTool.MARKER -> updatedWidth * 1.8f
                                                AnnotationTool.PENCIL -> updatedWidth * 0.75f
                                                AnnotationTool.FOUNTAIN_PEN -> {
                                                    val factor = (1.4f - 0.5f * (velocity / 1800f).coerceIn(0f, 1f))
                                                    updatedWidth * factor
                                                }
                                                else -> updatedWidth
                                            }

                                            val normPoints = smoothed.map { Offset(it.x / cW, it.y / cH) }
                                            updatedOnStrokeFinished(
                                                ComposeStroke(
                                                    points = normPoints,
                                                    color = if (updatedTool == AnnotationTool.HIGHLIGHTER) updatedColor.copy(alpha = 0.35f) else updatedColor,
                                                    strokeWidth = dynamicWidth,
                                                    isHighlighter = updatedTool == AnnotationTool.HIGHLIGHTER,
                                                    isNormalized = true,
                                                    toolName = updatedTool.name
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            currentTimedPoints = emptyList()
                            liveSnappedShape = null
                            eraserCursorPos = null
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val cW = size.width
        val cH = size.height

        // 1. Draw committed strokes with specific tool styles
        val normMoveOffset = if (liveMoveOffset != Offset.Zero) Offset(liveMoveOffset.x / cW, liveMoveOffset.y / cH) else Offset.Zero
        strokes.forEachIndexed { index, stroke ->
            val strokeToDraw = if (index == selectedStrokeIndex && normMoveOffset != Offset.Zero) {
                stroke.copy(points = stroke.points.map { it + normMoveOffset })
            } else stroke
            drawStrokeAnnotation(strokeToDraw, cW, cH)
        }

        // 2. Draw committed shapes & rect annotations with specific tool styles
        rectangles.forEachIndexed { index, rect ->
            val rectToDraw = if (index == selectedRectIndex && normMoveOffset != Offset.Zero) {
                rect.copy(topLeft = rect.topLeft + normMoveOffset)
            } else rect
            drawShapeAnnotation(rectToDraw, cW, cH)
        }

        // 2b. Draw Apple PencilKit Selection Box if Select tool has an item selected
        if (isSelectTool) {
            val sIdx = selectedStrokeIndex
            val rIdx = selectedRectIndex
            if (sIdx != null && sIdx in strokes.indices) {
                val s = strokes[sIdx]
                val shiftedStroke = if (normMoveOffset != Offset.Zero) s.copy(points = s.points.map { it + normMoveOffset }) else s
                val bounds = getStrokeBoundsPx(shiftedStroke, cW, cH)
                drawAppleSelectionBox(bounds, isDragging = isDraggingSelection)
            } else if (rIdx != null && rIdx in rectangles.indices) {
                val r = rectangles[rIdx]
                val shiftedRect = if (normMoveOffset != Offset.Zero) r.copy(topLeft = r.topLeft + normMoveOffset) else r
                val bounds = getRectBoundsPx(shiftedRect, cW, cH)
                drawAppleSelectionBox(bounds, isDragging = isDraggingSelection)
            }
        }

        // 3. Live active in-progress preview (Eraser cursor, Linear, Box, Snapped Shape, or Freehand)
        if (isEraserEnabled && eraserCursorPos != null) {
            val ep = eraserCursorPos!!
            val r = updatedEraserRadius
            if (isObjectEraser) {
                // Apple Object Eraser Reticle: precision outer target ring with crosshair ticks
                drawCircle(
                    color = Color(0x22F43F5E),
                    radius = r * 1.25f,
                    center = ep
                )
                drawCircle(
                    color = Color(0xFFF43F5E),
                    radius = r * 1.25f,
                    center = ep,
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                )
                drawCircle(
                    color = Color(0xFFF43F5E),
                    radius = 3.5f,
                    center = ep
                )
            } else {
                // Apple Pixel Eraser dual-ring frosted reticle
                drawCircle(
                    color = Color(0x33EF4444),
                    radius = r,
                    center = ep
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = r,
                    center = ep,
                    style = Stroke(width = 2.5f)
                )
                drawCircle(
                    color = Color(0xFFEF4444),
                    radius = r - 1.5f,
                    center = ep,
                    style = Stroke(width = 1.2f)
                )
            }
        } else if (isLinearTool && dragStart != null && dragCurrent != null) {
            val s = dragStart!!
            val e = dragCurrent!!
            drawLinearTool(updatedTool, s, e, updatedColor, updatedWidth)
        } else if (isBoxOrAreaTool && dragStart != null && dragCurrent != null) {
            val s = dragStart!!
            val c = dragCurrent!!
            val x = minOf(s.x, c.x)
            val y = minOf(s.y, c.y)
            val w = abs(c.x - s.x)
            val h = abs(c.y - s.y)
            drawBoxTool(updatedTool, Offset(x, y), Size(w, h), updatedColor, updatedWidth)
        } else if (liveSnappedShape != null) {
            val isHighlighter = updatedTool == AnnotationTool.HIGHLIGHTER
            val snapColor = if (isHighlighter) {
                if (updatedColor.alpha <= 0.4f) updatedColor else updatedColor.copy(alpha = 0.35f)
            } else updatedColor
            val snapWidth = if (isHighlighter) updatedWidth * 3.2f else updatedWidth

            when (val s = liveSnappedShape) {
                is RecognizedShape.StraightLine -> {
                    drawLine(
                        color = snapColor,
                        start = s.start,
                        end = s.end,
                        strokeWidth = snapWidth,
                        cap = if (isHighlighter) StrokeCap.Square else StrokeCap.Round,
                        blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
                    )
                }
                is RecognizedShape.CurvedLine -> {
                    val curvePath = Path().apply {
                        moveTo(s.start.x, s.start.y)
                        quadraticBezierTo(s.control.x, s.control.y, s.end.x, s.end.y)
                    }
                    drawPath(
                        path = curvePath,
                        color = snapColor,
                        style = Stroke(width = snapWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
                    )
                }
                is RecognizedShape.Circle -> {
                    drawCircle(
                        color = snapColor,
                        radius = s.radius,
                        center = s.center,
                        style = Stroke(width = snapWidth),
                        blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
                    )
                }
                is RecognizedShape.Oval -> {
                    drawOval(
                        color = snapColor,
                        topLeft = Offset(s.center.x - s.radiusX, s.center.y - s.radiusY),
                        size = Size(s.radiusX * 2f, s.radiusY * 2f),
                        style = Stroke(width = snapWidth),
                        blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
                    )
                }
                is RecognizedShape.Box -> {
                    drawRect(
                        color = snapColor,
                        topLeft = s.topLeft,
                        size = s.size,
                        style = Stroke(width = snapWidth),
                        blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
                    )
                }
                is RecognizedShape.Triangle -> {
                    val p = Path().apply {
                        moveTo(s.p0.x, s.p0.y)
                        lineTo(s.p1.x, s.p1.y)
                        lineTo(s.p2.x, s.p2.y)
                        close()
                    }
                    drawPath(
                        path = p,
                        color = snapColor,
                        style = Stroke(width = snapWidth, join = StrokeJoin.Round),
                        blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
                    )
                }
                else -> {}
            }
        } else if (currentTimedPoints.size > 1 && updatedTool != AnnotationTool.LASER_POINTER) {
            val raw = currentTimedPoints.map { it.offset }
            val p = Path()
            p.moveTo(raw[0].x, raw[0].y)
            for (i in 1 until raw.size) {
                p.lineTo(raw[i].x, raw[i].y)
            }

            if (updatedTool == AnnotationTool.LASSO_SELECT) {
                // Marching-ants dotted line for lasso selection
                drawPath(
                    path = p,
                    color = Color(0xFF38BDF8),
                    style = Stroke(
                        width = 2.5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                )
            } else {
                val isHighlighter = updatedTool == AnnotationTool.HIGHLIGHTER
                val liveColor = if (isHighlighter) {
                    if (updatedColor.alpha <= 0.4f) updatedColor else updatedColor.copy(alpha = 0.35f)
                } else updatedColor
                val liveWidth = if (isHighlighter) updatedWidth * 3.2f else updatedWidth
                drawPath(
                    path = p,
                    color = liveColor,
                    style = Stroke(
                        width = liveWidth,
                        cap = if (updatedTool == AnnotationTool.MARKER || isHighlighter) StrokeCap.Square else StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
                )
            }
        }

        // 4. Draw Authentic Apple Keynote Laser (Multi-Stroke Persistence + 0.5s Dissolution + Continuous Smooth Glow)
        val allStrokesToRender = if (currentLaserStroke.isNotEmpty()) activeLaserStrokes + listOf(currentLaserStroke) else activeLaserStrokes
        if (allStrokesToRender.isNotEmpty() && laserDissolveAlpha > 0.01f) {
            allStrokesToRender.forEach { singleStroke ->
                if (singleStroke.size >= 2) {
                    val laserPath = buildSmoothLaserPath(singleStroke)

                    // Layer 1: Wide Radiant Neon Aura (Continuous smooth glow, zero dot artifacts)
                    drawPath(
                        path = laserPath,
                        color = Color(0xFFEF4444).copy(alpha = 0.38f * laserDissolveAlpha),
                        style = Stroke(width = 14f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Layer 2: Concentrated Searing Ruby Core
                    drawPath(
                        path = laserPath,
                        color = Color(0xFFFF1E44).copy(alpha = 0.88f * laserDissolveAlpha),
                        style = Stroke(width = 5.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                    // Layer 3: Ultra-Bright White-Hot Filament Center
                    drawPath(
                        path = laserPath,
                        color = Color.White.copy(alpha = 0.92f * laserDissolveAlpha),
                        style = Stroke(width = 2.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // High-intensity pulsating spark emitter directly under finger while actively writing
            if (isLaserDrawingNow && currentLaserStroke.isNotEmpty()) {
                val tipPos = currentLaserStroke.last()
                val now = System.currentTimeMillis()
                val pulse = (kotlin.math.sin(now / 110.0).toFloat() * 0.12f) + 1.0f

                drawCircle(
                    color = Color(0x60EF4444).copy(alpha = 0.40f * laserDissolveAlpha),
                    radius = 16f * pulse,
                    center = tipPos
                )
                drawCircle(
                    color = Color(0xFFFF1E44).copy(alpha = 0.95f * laserDissolveAlpha),
                    radius = 8.5f * pulse,
                    center = tipPos
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.98f * laserDissolveAlpha),
                    radius = 3.8f,
                    center = tipPos
                )
            }
        }
    }
}

/**
 * Specialized Stroke Renderer supporting LINE, ARROW, DOUBLE_ARROW, RULER, ANGLE, and Freehand tools
 */
private fun DrawScope.drawStrokeAnnotation(stroke: ComposeStroke, cW: Float, cH: Float) {
    if (stroke.points.size < 2) return

    val resolvedPoints = if (stroke.isNormalized) {
        stroke.points.map { Offset(it.x * cW, it.y * cH) }
    } else {
        stroke.points
    }

    val toolEnum = try {
        AnnotationTool.valueOf(stroke.toolName)
    } catch (_: Exception) {
        if (stroke.isHighlighter) AnnotationTool.HIGHLIGHTER else AnnotationTool.PEN
    }

    if (resolvedPoints.size == 2 && toolEnum in listOf(
            AnnotationTool.LINE,
            AnnotationTool.ARROW,
            AnnotationTool.DOUBLE_ARROW,
            AnnotationTool.RULER,
            AnnotationTool.ANGLE
        )
    ) {
        drawLinearTool(toolEnum, resolvedPoints[0], resolvedPoints[1], stroke.color, stroke.strokeWidth)
        return
    }

    val path = buildSmoothPath(resolvedPoints)
    val isHighlighter = toolEnum == AnnotationTool.HIGHLIGHTER || stroke.isHighlighter
    val strokeColor = when {
        isHighlighter -> if (stroke.color.alpha <= 0.4f) stroke.color else stroke.color.copy(alpha = 0.35f)
        toolEnum == AnnotationTool.PENCIL -> stroke.color.copy(alpha = 0.7f)
        toolEnum == AnnotationTool.LASER_POINTER -> Color(0xFFFF2A6D)
        else -> stroke.color
    }
    val cap = if (toolEnum == AnnotationTool.MARKER || isHighlighter) StrokeCap.Square else StrokeCap.Round

    drawPath(
        path = path,
        color = strokeColor,
        style = Stroke(
            width = stroke.strokeWidth,
            cap = cap,
            join = StrokeJoin.Round
        ),
        blendMode = if (isHighlighter) BlendMode.Multiply else androidx.compose.ui.graphics.drawscope.DrawScope.DefaultBlendMode
    )
}

/**
 * Draws specialized linear tools: LINE, ARROW, DOUBLE_ARROW, RULER, ANGLE
 */
private fun DrawScope.drawLinearTool(
    tool: AnnotationTool,
    start: Offset,
    end: Offset,
    color: Color,
    strokeWidth: Float
) {
    when (tool) {
        AnnotationTool.LINE -> {
            drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
        AnnotationTool.ARROW -> {
            drawArrow(start, end, color, strokeWidth, doubleArrow = false)
        }
        AnnotationTool.DOUBLE_ARROW -> {
            drawArrow(start, end, color, strokeWidth, doubleArrow = true)
        }
        AnnotationTool.RULER -> {
            drawRuler(start, end, color, strokeWidth)
        }
        AnnotationTool.ANGLE -> {
            drawAngle(start, end, color, strokeWidth)
        }
        else -> {
            drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
        }
    }
}

/**
 * Specialized Shape & Box Renderer supporting all 25+ shape, note, markup, measure, and security tools
 */
private fun DrawScope.drawShapeAnnotation(rect: ComposeRectAnnotation, cW: Float, cH: Float) {
    val tl = if (rect.isNormalized) Offset(rect.topLeft.x * cW, rect.topLeft.y * cH) else rect.topLeft
    val sz = if (rect.isNormalized) Size(rect.size.width * cW, rect.size.height * cH) else rect.size

    val toolEnum = try {
        AnnotationTool.valueOf(rect.toolName)
    } catch (_: Exception) {
        AnnotationTool.RECTANGLE
    }

    drawBoxTool(toolEnum, tl, sz, rect.color, rect.strokeWidth)
}

/**
 * Dispatches drawing to the exact specialized visual representation for box/area tools
 */
private fun DrawScope.drawBoxTool(
    tool: AnnotationTool,
    tl: Offset,
    sz: Size,
    color: Color,
    strokeWidth: Float
) {
    when (tool) {
        AnnotationTool.RECTANGLE -> {
            drawRect(color = color, topLeft = tl, size = sz, style = Stroke(width = strokeWidth))
        }
        AnnotationTool.ROUNDED_RECT -> {
            drawRoundRect(
                color = color,
                topLeft = tl,
                size = sz,
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = strokeWidth)
            )
        }
        AnnotationTool.CIRCLE -> {
            drawOval(color = color, topLeft = tl, size = sz, style = Stroke(width = strokeWidth))
        }
        AnnotationTool.ELLIPSE -> {
            drawOval(color = color, topLeft = tl, size = sz, style = Stroke(width = strokeWidth))
        }
        AnnotationTool.POLYGON -> {
            drawPolygon(tl, sz, color, strokeWidth)
        }
        AnnotationTool.CLOUD -> {
            drawCloud(tl, sz, color, strokeWidth)
        }
        AnnotationTool.TEXT_BOX -> {
            drawTextBox(tl, sz, color, strokeWidth)
        }
        AnnotationTool.STICKY_NOTE -> {
            drawStickyNote(tl, sz, color, strokeWidth)
        }
        AnnotationTool.CALLOUT -> {
            drawCallout(tl, sz, color, strokeWidth)
        }
        AnnotationTool.COMMENT_BUBBLE -> {
            drawCommentBubble(tl, sz, color, strokeWidth)
        }
        AnnotationTool.STAMP -> {
            drawStamp(tl, sz, color, strokeWidth)
        }
        AnnotationTool.REDACTION -> {
            drawRedaction(tl, sz, strokeWidth)
        }
        AnnotationTool.WATERMARK -> {
            drawWatermark(tl, sz, color, strokeWidth)
        }
        AnnotationTool.CERTIFICATE_ID -> {
            drawCertificateBadge(tl, sz, color, strokeWidth)
        }
        AnnotationTool.AREA_MEASURE -> {
            drawAreaMeasure(tl, sz, color, strokeWidth)
        }
        AnnotationTool.PERIMETER -> {
            drawPerimeter(tl, sz, color, strokeWidth)
        }
        AnnotationTool.TEXT_HIGHLIGHT -> {
            drawRect(color = color.copy(alpha = 0.35f), topLeft = tl, size = sz)
        }
        AnnotationTool.STRIKETHROUGH -> {
            val midY = tl.y + sz.height / 2f
            drawLine(color = color, start = Offset(tl.x, midY), end = Offset(tl.x + sz.width, midY), strokeWidth = strokeWidth)
        }
        AnnotationTool.UNDERLINE -> {
            val botY = tl.y + sz.height
            drawLine(color = color, start = Offset(tl.x, botY), end = Offset(tl.x + sz.width, botY), strokeWidth = strokeWidth)
        }
        AnnotationTool.SQUIGGLY -> {
            drawSquiggly(Offset(tl.x, tl.y + sz.height), Offset(tl.x + sz.width, tl.y + sz.height), color, strokeWidth)
        }
        AnnotationTool.CARET -> {
            val midX = tl.x + sz.width / 2f
            val topY = tl.y
            val botY = tl.y + sz.height
            val path = Path().apply {
                moveTo(tl.x, botY)
                lineTo(midX, topY)
                lineTo(tl.x + sz.width, botY)
            }
            drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        AnnotationTool.LASSO_SELECT -> {
            drawRoundRect(
                color = Color(0xFF38BDF8),
                topLeft = tl,
                size = sz,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )
            )
        }
        else -> {
            drawRect(color = color, topLeft = tl, size = sz, style = Stroke(width = strokeWidth))
        }
    }
}

// -----------------------------------------------------------------------------------------
// Custom Precision Vector Drawing Primitives for High-Fidelity PDF Annotations
// -----------------------------------------------------------------------------------------

private fun DrawScope.drawArrow(p0: Offset, p1: Offset, color: Color, strokeWidth: Float, doubleArrow: Boolean) {
    drawLine(color = color, start = p0, end = p1, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    val headLen = (strokeWidth * 4.5f).coerceIn(16f, 36f)
    val angle = atan2(p1.y - p0.y, p1.x - p0.x)

    fun drawHeadAt(tip: Offset, ang: Float) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(
                tip.x - headLen * cos(ang - 0.45f),
                tip.y - headLen * sin(ang - 0.45f)
            )
            lineTo(
                tip.x - headLen * cos(ang + 0.45f),
                tip.y - headLen * sin(ang + 0.45f)
            )
            close()
        }
        drawPath(path = path, color = color)
    }

    drawHeadAt(p1, angle)
    if (doubleArrow) {
        drawHeadAt(p0, angle + Math.PI.toFloat())
    }
}

private fun DrawScope.drawRuler(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    drawLine(color = color, start = start, end = end, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    val angle = atan2(end.y - start.y, end.x - start.x)
    val perp = angle + (Math.PI.toFloat() / 2f)
    val tickLen = 14f

    // Start & End tick marks
    drawLine(
        color = color,
        start = Offset(start.x - tickLen * cos(perp), start.y - tickLen * sin(perp)),
        end = Offset(start.x + tickLen * cos(perp), start.y + tickLen * sin(perp)),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = Offset(end.x - tickLen * cos(perp), end.y - tickLen * sin(perp)),
        end = Offset(end.x + tickLen * cos(perp), end.y + tickLen * sin(perp)),
        strokeWidth = strokeWidth
    )

    // Distance Label
    val distPx = sqrt((end.x - start.x).pow(2) + (end.y - start.y).pow(2))
    val cmVal = ((distPx / 38f) * 10f).roundToInt() / 10f
    val label = "$cmVal cm"
    val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)

    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.WHITE
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val bgPaint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(210, 20, 20, 30)
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val textW = paint.measureText(label)
        canvas.nativeCanvas.drawRoundRect(
            mid.x - textW / 2f - 10f,
            mid.y - 28f,
            mid.x + textW / 2f + 10f,
            mid.y + 12f,
            8f,
            8f,
            bgPaint
        )
        canvas.nativeCanvas.drawText(label, mid.x, mid.y, paint)
    }
}

private fun DrawScope.drawAngle(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val corner = Offset(start.x, end.y)
    drawLine(color = color, start = start, end = corner, strokeWidth = strokeWidth)
    drawLine(color = color, start = corner, end = end, strokeWidth = strokeWidth)

    // Arc at corner
    val arcRadius = 24f
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(corner.x - arcRadius, corner.y - arcRadius),
        size = Size(arcRadius * 2, arcRadius * 2),
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.drawPolygon(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    val cx = tl.x + sz.width / 2f
    val cy = tl.y + sz.height / 2f
    val rx = sz.width / 2f
    val ry = sz.height / 2f
    val path = Path()
    val sides = 6
    for (i in 0 until sides) {
        val ang = (i * 2.0 * Math.PI / sides).toFloat()
        val x = cx + rx * cos(ang)
        val y = cy + ry * sin(ang)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawCloud(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    val path = Path()
    val steps = 8
    val w = sz.width
    val h = sz.height
    // Top border scallops
    path.moveTo(tl.x, tl.y + 15f)
    for (i in 0 until steps) {
        val curX = tl.x + (w / steps) * i
        val nextX = tl.x + (w / steps) * (i + 1)
        path.quadraticBezierTo((curX + nextX) / 2f, tl.y - 12f, nextX, tl.y)
    }
    // Right border scallops
    for (i in 0 until 4) {
        val curY = tl.y + (h / 4) * i
        val nextY = tl.y + (h / 4) * (i + 1)
        path.quadraticBezierTo(tl.x + w + 12f, (curY + nextY) / 2f, tl.x + w, nextY)
    }
    // Bottom border scallops
    for (i in 0 until steps) {
        val curX = tl.x + w - (w / steps) * i
        val nextX = tl.x + w - (w / steps) * (i + 1)
        path.quadraticBezierTo((curX + nextX) / 2f, tl.y + h + 12f, nextX, tl.y + h)
    }
    // Left border scallops
    for (i in 0 until 4) {
        val curY = tl.y + h - (h / 4) * i
        val nextY = tl.y + h - (h / 4) * (i + 1)
        path.quadraticBezierTo(tl.x - 12f, (curY + nextY) / 2f, tl.x, nextY)
    }
    path.close()
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawTextBox(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    drawRoundRect(
        color = color.copy(alpha = 0.08f),
        topLeft = tl,
        size = sz,
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = color,
        topLeft = tl,
        size = sz,
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
        )
    )
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawText("Text Box", tl.x + 12f, tl.y + 28f, paint)
    }
}

private fun DrawScope.drawStickyNote(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    val fold = 24f
    val path = Path().apply {
        moveTo(tl.x, tl.y)
        lineTo(tl.x + sz.width - fold, tl.y)
        lineTo(tl.x + sz.width, tl.y + fold)
        lineTo(tl.x + sz.width, tl.y + sz.height)
        lineTo(tl.x, tl.y + sz.height)
        close()
    }
    // Note fill
    drawPath(path = path, color = Color(0xFFFEF08A).copy(alpha = 0.85f))
    drawPath(path = path, color = Color(0xFFEAB308), style = Stroke(width = 1.5f))

    // Dog-ear fold
    val foldPath = Path().apply {
        moveTo(tl.x + sz.width - fold, tl.y)
        lineTo(tl.x + sz.width - fold, tl.y + fold)
        lineTo(tl.x + sz.width, tl.y + fold)
        close()
    }
    drawPath(path = foldPath, color = Color(0xFFFDE047))
    drawPath(path = foldPath, color = Color(0xFFCA8A04), style = Stroke(width = 1.5f))

    // Note interior rule lines
    val lineStartY = tl.y + fold + 12f
    for (i in 0..2) {
        val y = lineStartY + i * 16f
        if (y < tl.y + sz.height - 8f) {
            drawLine(
                color = Color(0x35000000),
                start = Offset(tl.x + 12f, y),
                end = Offset(tl.x + sz.width - 12f, y),
                strokeWidth = 1.2f
            )
        }
    }
}

private fun DrawScope.drawCallout(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    val corner = 14f
    val tailH = 20f
    val bodyH = sz.height - tailH
    val path = Path().apply {
        moveTo(tl.x + corner, tl.y)
        lineTo(tl.x + sz.width - corner, tl.y)
        quadraticBezierTo(tl.x + sz.width, tl.y, tl.x + sz.width, tl.y + corner)
        lineTo(tl.x + sz.width, tl.y + bodyH - corner)
        quadraticBezierTo(tl.x + sz.width, tl.y + bodyH, tl.x + sz.width - corner, tl.y + bodyH)
        lineTo(tl.x + 40f, tl.y + bodyH)
        lineTo(tl.x + 16f, tl.y + sz.height) // Tail tip
        lineTo(tl.x + 24f, tl.y + bodyH)
        lineTo(tl.x + corner, tl.y + bodyH)
        quadraticBezierTo(tl.x, tl.y + bodyH, tl.x, tl.y + bodyH - corner)
        lineTo(tl.x, tl.y + corner)
        quadraticBezierTo(tl.x, tl.y, tl.x + corner, tl.y)
        close()
    }
    drawPath(path = path, color = color.copy(alpha = 0.15f))
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawCommentBubble(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    drawOval(color = color.copy(alpha = 0.15f), topLeft = tl, size = sz)
    drawOval(color = color, topLeft = tl, size = sz, style = Stroke(width = strokeWidth))

    // Three dots in center
    val cx = tl.x + sz.width / 2f
    val cy = tl.y + sz.height / 2f
    val dotRadius = (strokeWidth * 1.2f).coerceIn(3f, 6f)
    drawCircle(color = color, radius = dotRadius, center = Offset(cx - 14f, cy))
    drawCircle(color = color, radius = dotRadius, center = Offset(cx, cy))
    drawCircle(color = color, radius = dotRadius, center = Offset(cx + 14f, cy))
}

private fun DrawScope.drawStamp(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    // Double border stamp
    drawRoundRect(
        color = Color(0xFFEF4444),
        topLeft = tl,
        size = sz,
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 3.5f)
    )
    val inset = 6f
    drawRoundRect(
        color = Color(0xFFEF4444).copy(alpha = 0.7f),
        topLeft = Offset(tl.x + inset, tl.y + inset),
        size = Size(sz.width - inset * 2, sz.height - inset * 2),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f)
    )
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.parseColor("#EF4444")
            textSize = (sz.height * 0.42f).coerceIn(18f, 38f)
            isAntiAlias = true
            isFakeBoldText = true
            letterSpacing = 0.2f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val cx = tl.x + sz.width / 2f
        val cy = tl.y + sz.height / 2f + (paint.textSize / 3f)
        canvas.nativeCanvas.drawText("APPROVED", cx, cy, paint)
    }
}

private fun DrawScope.drawRedaction(tl: Offset, sz: Size, strokeWidth: Float) {
    // Clean, solid legal redaction block without distracting text overlay
    drawRect(color = Color.Black, topLeft = tl, size = sz)
    drawRect(color = Color(0xFF1E293B), topLeft = tl, size = sz, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawWatermark(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    drawRect(
        color = color.copy(alpha = 0.15f),
        topLeft = tl,
        size = sz,
        style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    )
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(90, 239, 68, 68)
            textSize = (sz.height * 0.38f).coerceIn(18f, 44f)
            isAntiAlias = true
            isFakeBoldText = true
            letterSpacing = 0.25f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val cx = tl.x + sz.width / 2f
        val cy = tl.y + sz.height / 2f + (paint.textSize / 3f)
        canvas.nativeCanvas.save()
        canvas.nativeCanvas.rotate(-25f, cx, cy)
        canvas.nativeCanvas.drawText("CONFIDENTIAL", cx, cy, paint)
        canvas.nativeCanvas.restore()
    }
}

private fun DrawScope.drawCertificateBadge(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    drawRoundRect(
        color = Color(0xFF10B981).copy(alpha = 0.18f),
        topLeft = tl,
        size = sz,
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFF10B981),
        topLeft = tl,
        size = sz,
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = 2f)
    )
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.parseColor("#10B981")
            textSize = (sz.height * 0.35f).coerceIn(16f, 28f)
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val cx = tl.x + sz.width / 2f
        val cy = tl.y + sz.height / 2f + (paint.textSize / 3f)
        canvas.nativeCanvas.drawText("VERIFIED ID", cx, cy, paint)
    }
}

private fun DrawScope.drawAreaMeasure(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    drawRect(
        color = color,
        topLeft = tl,
        size = sz,
        style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
    )
    val areaSq = ((sz.width * sz.height / 1440f) * 10f).roundToInt() / 10f
    val label = "$areaSq cmÂ²"
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.WHITE
            textSize = 24f
            isAntiAlias = true
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val bgPaint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(200, 15, 15, 25)
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val cx = tl.x + sz.width / 2f
        val cy = tl.y + sz.height / 2f
        val tw = paint.measureText(label)
        canvas.nativeCanvas.drawRoundRect(cx - tw / 2f - 10f, cy - 24f, cx + tw / 2f + 10f, cy + 12f, 8f, 8f, bgPaint)
        canvas.nativeCanvas.drawText(label, cx, cy, paint)
    }
}

private fun DrawScope.drawPerimeter(tl: Offset, sz: Size, color: Color, strokeWidth: Float) {
    drawRect(
        color = color,
        topLeft = tl,
        size = sz,
        style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    )
    val perim = (((sz.width + sz.height) * 2f / 38f) * 10f).roundToInt() / 10f
    val label = "P: $perim cm"
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.WHITE
            textSize = 22f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val cx = tl.x + sz.width / 2f
        val cy = tl.y + sz.height / 2f
        canvas.nativeCanvas.drawText(label, cx, cy, paint)
    }
}

private fun DrawScope.drawSquiggly(start: Offset, end: Offset, color: Color, strokeWidth: Float) {
    val path = Path()
    path.moveTo(start.x, start.y)
    val dx = end.x - start.x
    val dy = end.y - start.y
    val dist = sqrt(dx * dx + dy * dy)
    val waveLen = 14f
    val amp = 4f
    val count = (dist / waveLen).toInt().coerceAtLeast(1)

    for (i in 0 until count) {
        val f1 = (i + 0.25f) / count
        val f2 = (i + 0.75f) / count
        val f3 = (i + 1.0f) / count
        path.quadraticBezierTo(
            start.x + dx * f1,
            start.y + dy * f1 - amp,
            start.x + dx * (i + 0.5f) / count,
            start.y + dy * (i + 0.5f) / count
        )
        path.quadraticBezierTo(
            start.x + dx * f2,
            start.y + dy * f2 + amp,
            start.x + dx * f3,
            start.y + dy * f3
        )
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

/**
 * Builds a smooth Cubic/Quadratic Bezier path across point sequences
 */
private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)

    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    for (i in 1 until points.size - 1) {
        val cur = points[i]
        val next = points[i + 1]
        val midX = (cur.x + next.x) / 2f
        val midY = (cur.y + next.y) / 2f
        path.quadraticBezierTo(cur.x, cur.y, midX, midY)
    }
    val last = points.last()
    path.lineTo(last.x, last.y)
    return path
}

/**
 * Catmull-Rom spline interpolation for high-fidelity stroke smoothing
 */
private fun smoothPointsCatmullRom(points: List<Offset>): List<Offset> {
    if (points.size < 4) return points
    val smoothed = mutableListOf<Offset>()
    smoothed.add(points.first())

    for (i in 0 until points.size - 1) {
        val p0 = if (i > 0) points[i - 1] else points[i]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = if (i + 2 < points.size) points[i + 2] else p2

        for (step in 1..3) {
            val t = step / 3f
            val t2 = t * t
            val t3 = t2 * t

            val x = 0.5f * ((2f * p1.x) +
                    (-p0.x + p2.x) * t +
                    (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                    (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3)

            val y = 0.5f * ((2f * p1.y) +
                    (-p0.y + p2.y) * t +
                    (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                    (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3)

            smoothed.add(Offset(x, y))
        }
    }
    smoothed.add(points.last())
    return smoothed
}

/**
 * Calculates instantaneous finger drag velocity in pixels/second
 */
private fun calculateAverageVelocity(points: List<TimedPoint>): Float {
    if (points.size < 2) return 0f
    val dt = (points.last().timeMs - points.first().timeMs).coerceAtLeast(1)
    var dist = 0f
    for (i in 1 until points.size) {
        val dx = points[i].offset.x - points[i - 1].offset.x
        val dy = points[i].offset.y - points[i - 1].offset.y
        dist += sqrt(dx * dx + dy * dy)
    }
    return (dist / (dt.toFloat() / 1000f))
}

/**
 * Shape Recognition Logic: Apple Notes Grade Snapping for Straight Lines, Circles/Ellipses, Rectangles, and Triangles
 */
sealed class RecognizedShape {
    data class StraightLine(val start: Offset, val end: Offset) : RecognizedShape()
    data class CurvedLine(val start: Offset, val control: Offset, val end: Offset) : RecognizedShape()
    data class Circle(val center: Offset, val radius: Float) : RecognizedShape()
    data class Oval(val center: Offset, val radiusX: Float, val radiusY: Float) : RecognizedShape()
    data class Box(val origin: Offset, val topLeft: Offset, val size: Size) : RecognizedShape()
    data class Triangle(val p0: Offset, val p1: Offset, val p2: Offset) : RecognizedShape()
    object None : RecognizedShape()
}

/**
 * Detects geometric shapes when the user holds stationary at the end of a freehand stroke
 */
fun detectShape(points: List<Offset>): RecognizedShape {
    if (points.size < 6) return RecognizedShape.None

    val p0 = points.first()
    val pEnd = points.last()

    var arcLength = 0f
    for (i in 1 until points.size) {
        val dx = points[i].x - points[i - 1].x
        val dy = points[i].y - points[i - 1].y
        arcLength += sqrt(dx * dx + dy * dy)
    }
    if (arcLength < 24f) return RecognizedShape.None

    val directDist = sqrt((pEnd.x - p0.x).pow(2) + (pEnd.y - p0.y).pow(2))

    // 1. Straight Line Check: straight path from p0 to pEnd
    if ((directDist / arcLength) > 0.85f) {
        return RecognizedShape.StraightLine(start = p0, end = pEnd)
    }

    // 1b. Curved Line Check: arc that doesn't close on itself
    // Detect if the stroke curves significantly but endpoints are far apart
    if ((directDist / arcLength) in 0.55f..0.85f && directDist > 40f) {
        // Find the point with max perpendicular distance from the straight line p0â†’pEnd
        val lineVecX = pEnd.x - p0.x
        val lineVecY = pEnd.y - p0.y
        val lineLen = sqrt(lineVecX * lineVecX + lineVecY * lineVecY)
        if (lineLen > 0.01f) {
            var maxPerpDist = 0f
            var controlPt = points[points.size / 2]
            for (pt in points) {
                val cross = abs((pt.x - p0.x) * lineVecY - (pt.y - p0.y) * lineVecX) / lineLen
                if (cross > maxPerpDist) {
                    maxPerpDist = cross
                    controlPt = pt
                }
            }
            // If max perpendicular distance is substantial, it's a curved line
            if (maxPerpDist > directDist * 0.12f) {
                return RecognizedShape.CurvedLine(start = p0, control = controlPt, end = pEnd)
            }
        }
    }

    // 2. Closed Loop Check (Circle, Oval, Rectangle, Triangle)
    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE
    for (pt in points) {
        minX = min(minX, pt.x)
        maxX = max(maxX, pt.x)
        minY = min(minY, pt.y)
        maxY = max(maxY, pt.y)
    }
    val bboxW = maxX - minX
    val bboxH = maxY - minY

    if (bboxW > 20f && bboxH > 20f) {
        var shoelaceArea = 0.0
        for (i in points.indices) {
            val j = (i + 1) % points.size
            shoelaceArea += (points[i].x * points[j].y - points[j].x * points[i].y).toDouble()
        }
        val area = abs(shoelaceArea) / 2.0
        val circularity = (4.0 * Math.PI * area) / (arcLength.toDouble() * arcLength.toDouble())
        val closureRatio = directDist / arcLength

        // Circle / Ellipse / Oval detection
        if (circularity > 0.58 && closureRatio < 0.45f) {
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            val rx = bboxW / 2f
            val ry = bboxH / 2f
            // If aspect ratio is close to 1:1, snap to circle; otherwise oval
            val aspect = if (rx > 0.01f) ry / rx else 1f
            return if (aspect in 0.82f..1.22f) {
                RecognizedShape.Circle(center = Offset(cx, cy), radius = (rx + ry) / 2f)
            } else {
                RecognizedShape.Oval(center = Offset(cx, cy), radiusX = rx, radiusY = ry)
            }
        }

        // Box / Rectangle
        val boxFill = area / (bboxW * bboxH)
        if (boxFill > 0.65 && closureRatio < 0.45f) {
            return RecognizedShape.Box(
                origin = p0,
                topLeft = Offset(minX, minY),
                size = Size(bboxW, bboxH)
            )
        }

        // Triangle
        if (closureRatio < 0.45f && points.size >= 10) {
            val midIdx = points.size / 2
            return RecognizedShape.Triangle(p0 = p0, p1 = points[midIdx], p2 = pEnd)
        }
    }

    // Fallback curved line: moderate straightness with decent length
    if (directDist > 50f && (directDist / arcLength) in 0.60f..0.76f) {
        val midPt = points[points.size / 2]
        return RecognizedShape.CurvedLine(start = p0, control = midPt, end = pEnd)
    }

    // Fallback: If substantial distance and moderately straight, snap to straight line
    if (directDist > 50f && (directDist / arcLength) > 0.76f) {
        return RecognizedShape.StraightLine(start = p0, end = pEnd)
    }

    return RecognizedShape.None
}

/**
 * Dynamically resizes, rotates, or moves the snapped shape as the user drags without lifting
 */
fun updateSnappedShapeWithDrag(shape: RecognizedShape, curPos: Offset): RecognizedShape {
    return when (shape) {
        is RecognizedShape.StraightLine -> {
            RecognizedShape.StraightLine(start = shape.start, end = curPos)
        }
        is RecognizedShape.CurvedLine -> {
            // Drag moves the endpoint; control point interpolates proportionally
            val midX = (shape.start.x + curPos.x) / 2f + (shape.control.x - (shape.start.x + shape.end.x) / 2f)
            val midY = (shape.start.y + curPos.y) / 2f + (shape.control.y - (shape.start.y + shape.end.y) / 2f)
            RecognizedShape.CurvedLine(start = shape.start, control = Offset(midX, midY), end = curPos)
        }
        is RecognizedShape.Circle -> {
            val dist = sqrt((curPos.x - shape.center.x).pow(2) + (curPos.y - shape.center.y).pow(2)).coerceAtLeast(10f)
            RecognizedShape.Circle(center = shape.center, radius = dist)
        }
        is RecognizedShape.Oval -> {
            val rx = abs(curPos.x - shape.center.x).coerceAtLeast(10f)
            val ry = abs(curPos.y - shape.center.y).coerceAtLeast(10f)
            RecognizedShape.Oval(center = shape.center, radiusX = rx, radiusY = ry)
        }
        is RecognizedShape.Box -> {
            val ox = shape.origin.x
            val oy = shape.origin.y
            val minX = min(ox, curPos.x)
            val minY = min(oy, curPos.y)
            val w = max(abs(curPos.x - ox), 10f)
            val h = max(abs(curPos.y - oy), 10f)
            RecognizedShape.Box(origin = shape.origin, topLeft = Offset(minX, minY), size = Size(w, h))
        }
        is RecognizedShape.Triangle -> {
            RecognizedShape.Triangle(p0 = shape.p0, p1 = shape.p1, p2 = curPos)
        }
        else -> shape
    }
}

/**
 * Commits the perfected geometric shape into normalized coordinates
 */
fun commitSnappedShape(
    shape: RecognizedShape,
    cW: Float,
    cH: Float,
    color: Color,
    strokeWidth: Float,
    tool: AnnotationTool = AnnotationTool.PEN,
    onStrokeFinished: (ComposeStroke) -> Unit,
    onRectFinished: (ComposeRectAnnotation) -> Unit
) {
    val normCW = if (cW > 0f) cW else 1f
    val normCH = if (cH > 0f) cH else 1f

    when (shape) {
        is RecognizedShape.StraightLine -> {
            val normStart = Offset(shape.start.x / normCW, shape.start.y / normCH)
            val normEnd = Offset(shape.end.x / normCW, shape.end.y / normCH)
            val isHighlighter = tool == AnnotationTool.HIGHLIGHTER
            val finalColor = if (isHighlighter) {
                if (color.alpha <= 0.4f) color else color.copy(alpha = 0.35f)
            } else color
            val finalWidth = if (isHighlighter) strokeWidth * 3.2f else strokeWidth
            val toolName = if (isHighlighter) AnnotationTool.HIGHLIGHTER.name else AnnotationTool.LINE.name
            onStrokeFinished(
                ComposeStroke(
                    points = listOf(normStart, normEnd),
                    color = finalColor,
                    strokeWidth = finalWidth,
                    isHighlighter = isHighlighter,
                    isNormalized = true,
                    toolName = toolName
                )
            )
        }
        is RecognizedShape.CurvedLine -> {
            val normStart = Offset(shape.start.x / normCW, shape.start.y / normCH)
            val normCtrl = Offset(shape.control.x / normCW, shape.control.y / normCH)
            val normEnd = Offset(shape.end.x / normCW, shape.end.y / normCH)
            val isHighlighter = tool == AnnotationTool.HIGHLIGHTER
            val finalColor = if (isHighlighter) {
                if (color.alpha <= 0.4f) color else color.copy(alpha = 0.35f)
            } else color
            val finalWidth = if (isHighlighter) strokeWidth * 3.2f else strokeWidth
            // Generate Bezier curve points for storage
            val curvePoints = mutableListOf<Offset>()
            for (t in 0..20) {
                val frac = t / 20f
                val invFrac = 1f - frac
                val x = invFrac * invFrac * normStart.x + 2f * invFrac * frac * normCtrl.x + frac * frac * normEnd.x
                val y = invFrac * invFrac * normStart.y + 2f * invFrac * frac * normCtrl.y + frac * frac * normEnd.y
                curvePoints.add(Offset(x, y))
            }
            onStrokeFinished(
                ComposeStroke(
                    points = curvePoints,
                    color = finalColor,
                    strokeWidth = finalWidth,
                    isHighlighter = isHighlighter,
                    isNormalized = true,
                    toolName = AnnotationTool.LINE.name
                )
            )
        }
        is RecognizedShape.Circle -> {
            val tlX = (shape.center.x - shape.radius) / normCW
            val tlY = (shape.center.y - shape.radius) / normCH
            val w = (shape.radius * 2f) / normCW
            val h = (shape.radius * 2f) / normCH
            onRectFinished(
                ComposeRectAnnotation(
                    topLeft = Offset(tlX, tlY),
                    size = Size(w, h),
                    color = color,
                    strokeWidth = strokeWidth,
                    isNormalized = true,
                    toolName = AnnotationTool.CIRCLE.name
                )
            )
        }
        is RecognizedShape.Oval -> {
            val tlX = (shape.center.x - shape.radiusX) / normCW
            val tlY = (shape.center.y - shape.radiusY) / normCH
            val w = (shape.radiusX * 2f) / normCW
            val h = (shape.radiusY * 2f) / normCH
            onRectFinished(
                ComposeRectAnnotation(
                    topLeft = Offset(tlX, tlY),
                    size = Size(w, h),
                    color = color,
                    strokeWidth = strokeWidth,
                    isNormalized = true,
                    toolName = AnnotationTool.ELLIPSE.name
                )
            )
        }
        is RecognizedShape.Box -> {
            val normTl = Offset(shape.topLeft.x / normCW, shape.topLeft.y / normCH)
            val normSz = Size(shape.size.width / normCW, shape.size.height / normCH)
            onRectFinished(
                ComposeRectAnnotation(
                    topLeft = normTl,
                    size = normSz,
                    color = color,
                    strokeWidth = strokeWidth,
                    isNormalized = true,
                    toolName = AnnotationTool.RECTANGLE.name
                )
            )
        }
        is RecognizedShape.Triangle -> {
            val norm0 = Offset(shape.p0.x / normCW, shape.p0.y / normCH)
            val norm1 = Offset(shape.p1.x / normCW, shape.p1.y / normCH)
            val norm2 = Offset(shape.p2.x / normCW, shape.p2.y / normCH)
            onStrokeFinished(
                ComposeStroke(
                    points = listOf(norm0, norm1, norm2, norm0),
                    color = color,
                    strokeWidth = strokeWidth,
                    isNormalized = true,
                    toolName = AnnotationTool.POLYGON.name
                )
            )
        }
        else -> {}
    }
}

/**
 * Calculates distance from a point to a line segment
 */
fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lenSq = abx * abx + aby * aby
    if (lenSq < 0.001f) {
        val dx = p.x - a.x
        val dy = p.y - a.y
        return sqrt(dx * dx + dy * dy)
    }
    val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
    val projX = a.x + t * abx
    val projY = a.y + t * aby
    val dx = p.x - projX
    val dy = p.y - projY
    return sqrt(dx * dx + dy * dy)
}

/**
 * Segment-accurate proximity eraser: removes strokes and shapes in real-time
 */
fun eraseNearby(
    pos: Offset,
    strokes: List<ComposeStroke>,
    rectangles: List<ComposeRectAnnotation>,
    cW: Float,
    cH: Float,
    radius: Float = 36f,
    isObjectEraser: Boolean = false,
    onEraseStroke: (Int) -> Unit,
    onEraseRect: (Int) -> Unit
) {
    val normCW = if (cW > 0f) cW else 1f
    val normCH = if (cH > 0f) cH else 1f
    val effectiveRadius = if (isObjectEraser) maxOf(radius, 32f) else radius

    // 1. Erase touching strokes with O(1) Bounding-Box rejection
    for (i in strokes.indices.reversed()) {
        val stroke = strokes[i]
        val rawPts = stroke.points
        if (rawPts.isEmpty()) continue

        val isNorm = stroke.isNormalized
        var sMinX = Float.MAX_VALUE
        var sMaxX = Float.MIN_VALUE
        var sMinY = Float.MAX_VALUE
        var sMaxY = Float.MIN_VALUE

        for (pt in rawPts) {
            val px = if (isNorm) pt.x * normCW else pt.x
            val py = if (isNorm) pt.y * normCH else pt.y
            if (px < sMinX) sMinX = px
            if (px > sMaxX) sMaxX = px
            if (py < sMinY) sMinY = py
            if (py > sMaxY) sMaxY = py
        }

        val totalR = effectiveRadius + stroke.strokeWidth
        if (pos.x < sMinX - totalR || pos.x > sMaxX + totalR || pos.y < sMinY - totalR || pos.y > sMaxY + totalR) {
            continue // Outside bounding box! O(1) skip!
        }

        var hit = false
        if (rawPts.size == 1) {
            val p0x = if (isNorm) rawPts[0].x * normCW else rawPts[0].x
            val p0y = if (isNorm) rawPts[0].y * normCH else rawPts[0].y
            val dx = pos.x - p0x
            val dy = pos.y - p0y
            if ((dx * dx + dy * dy) < totalR * totalR) hit = true
        } else {
            for (j in 0 until rawPts.size - 1) {
                val p1 = if (isNorm) Offset(rawPts[j].x * normCW, rawPts[j].y * normCH) else rawPts[j]
                val p2 = if (isNorm) Offset(rawPts[j + 1].x * normCW, rawPts[j + 1].y * normCH) else rawPts[j + 1]
                if (distToSegment(pos, p1, p2) < totalR) {
                    hit = true
                    break
                }
            }
        }
        if (hit) {
            onEraseStroke(i)
            if (isObjectEraser) return // In object mode, clean single-target deletion
        }
    }

    // 2. Erase touching shapes & rectangles with O(1) BBox rejection
    for (i in rectangles.indices.reversed()) {
        val rect = rectangles[i]
        val rx = if (rect.isNormalized) rect.topLeft.x * normCW else rect.topLeft.x
        val ry = if (rect.isNormalized) rect.topLeft.y * normCH else rect.topLeft.y
        val rw = if (rect.isNormalized) rect.size.width * normCW else rect.size.width
        val rh = if (rect.isNormalized) rect.size.height * normCH else rect.size.height

        if (pos.x < rx - effectiveRadius || pos.x > rx + rw + effectiveRadius || pos.y < ry - effectiveRadius || pos.y > ry + rh + effectiveRadius) {
            continue
        }

        val p1 = Offset(rx, ry)
        val p2 = Offset(rx + rw, ry)
        val p3 = Offset(rx + rw, ry + rh)
        val p4 = Offset(rx, ry + rh)

        val hit = distToSegment(pos, p1, p2) < effectiveRadius ||
                  distToSegment(pos, p2, p3) < effectiveRadius ||
                  distToSegment(pos, p3, p4) < effectiveRadius ||
                  distToSegment(pos, p4, p1) < effectiveRadius ||
                  (pos.x in rx..(rx + rw) && pos.y in ry..(ry + rh))

        if (hit) {
            onEraseRect(i)
            if (isObjectEraser) return
        }
    }
}

/**
 * Calculates screen pixel bounding box for a stroke annotation
 */
fun getStrokeBoundsPx(stroke: ComposeStroke, cW: Float, cH: Float): androidx.compose.ui.geometry.Rect {
    val pts = stroke.points
    if (pts.isEmpty()) return androidx.compose.ui.geometry.Rect.Zero
    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE
    for (pt in pts) {
        val px = if (stroke.isNormalized) pt.x * cW else pt.x
        val py = if (stroke.isNormalized) pt.y * cH else pt.y
        if (px < minX) minX = px
        if (px > maxX) maxX = px
        if (py < minY) minY = py
        if (py > maxY) maxY = py
    }
    val pad = maxOf(14f, stroke.strokeWidth + 8f)
    return androidx.compose.ui.geometry.Rect(minX - pad, minY - pad, maxX + pad, maxY + pad)
}

/**
 * Calculates screen pixel bounding box for a rect annotation
 */
fun getRectBoundsPx(rect: ComposeRectAnnotation, cW: Float, cH: Float): androidx.compose.ui.geometry.Rect {
    val rx = if (rect.isNormalized) rect.topLeft.x * cW else rect.topLeft.x
    val ry = if (rect.isNormalized) rect.topLeft.y * cH else rect.topLeft.y
    val rw = if (rect.isNormalized) rect.size.width * cW else rect.size.width
    val rh = if (rect.isNormalized) rect.size.height * cH else rect.size.height
    val pad = 12f
    return androidx.compose.ui.geometry.Rect(rx - pad, ry - pad, rx + rw + pad, ry + rh + pad)
}

/**
 * Renders Apple PencilKit-grade marching selection box with 4 corner anchors & move handle
 */
fun DrawScope.drawAppleSelectionBox(bounds: androidx.compose.ui.geometry.Rect, isDragging: Boolean = false) {
    if (bounds.width <= 0f || bounds.height <= 0f) return

    // Subtle blue background sheen
    drawRoundRect(
        color = Color(0x12007AFF),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(8f, 8f)
    )

    // Apple Blue Marching-Ants / Dashed Border
    drawRoundRect(
        color = Color(0xFF007AFF),
        topLeft = bounds.topLeft,
        size = bounds.size,
        cornerRadius = CornerRadius(8f, 8f),
        style = Stroke(
            width = if (isDragging) 2.4f else 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        )
    )

    // 4 Apple Corner Resize/Anchor Rings
    val cornerRadiusPx = 5.5f
    val corners = listOf(
        bounds.topLeft,
        bounds.topRight,
        bounds.bottomLeft,
        bounds.bottomRight
    )
    corners.forEach { c ->
        drawCircle(
            color = Color.White,
            radius = cornerRadiusPx,
            center = c
        )
        drawCircle(
            color = Color(0xFF007AFF),
            radius = cornerRadiusPx,
            center = c,
            style = Stroke(width = 1.8f)
        )
    }

    // Move Handle Indicator Badge at Top-Center
    val topMid = Offset(bounds.left + bounds.width / 2f, bounds.top)
    drawCircle(
        color = Color(0xFF007AFF),
        radius = 7f,
        center = topMid
    )
    drawCircle(
        color = Color.White,
        radius = 2.5f,
        center = topMid
    )
}

/**
 * Builds continuous smoothed path for Apple Keynote laser pointer
 * (Eliminates dots caused by overlapping segment caps)
 */
fun buildSmoothLaserPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 1 until points.size - 1) {
        val cur = points[i]
        val next = points[i + 1]
        val midX = (cur.x + next.x) / 2f
        val midY = (cur.y + next.y) / 2f
        path.quadraticBezierTo(cur.x, cur.y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}
