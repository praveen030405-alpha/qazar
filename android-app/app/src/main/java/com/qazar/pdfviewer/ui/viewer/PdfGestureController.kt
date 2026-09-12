package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot

fun Modifier.pdfGestures(
    transformState: ViewportHardwareTransformState,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    minZoomLimit: Float,
    isPanTool: Boolean,
    isSearchActive: Boolean,
    isThumbnailStripVisible: Boolean,
    textSelectionState: TextSelectionState? = null,
    onToggleUi: () -> Unit,
    onHideThumbnailStrip: () -> Unit,
    onShowThumbnailArrow: () -> Unit
): Modifier = this.pointerInput(isPanTool) {
    var lastTapTime = 0L

    awaitEachGesture {
        val velocityTracker = VelocityTracker()
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val downTime = System.currentTimeMillis()
        val downPos = down.position
        var isMultiTouch = false
        var totalDragDistance = 0f
        var dragAxisLock = 0 // 0 = undecided, 1 = locked vertical (straight-line scroll), 2 = free 2D / horizontal
        var accumulatedX = 0f
        var accumulatedY = 0f

        val viewW = size.width.toFloat()
        val viewH = size.height.toFloat()

        do {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val activePointers = event.changes.filter { it.pressed }
            activePointers.forEach { velocityTracker.addPosition(it.uptimeMillis, it.position) }

            // Multi-Finger Gesture: 2-Finger Vertical Scroll & Focal Zoom / 2D Pan on Hardware Layer
            if (activePointers.size >= 2) {
                isMultiTouch = true
                totalDragDistance += 20f

                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid()

                val oldScale = transformState.scale
                val newScale = (oldScale * zoomChange).coerceIn(0.75f, 15.0f)

                val maxPanX = (viewW * (newScale - 1f)).coerceAtLeast(0f) / 2f
                val maxPanY = (viewH * (newScale - 1f)).coerceAtLeast(0f) / 2f

                if (newScale > 1.01f) {
                    val scaleRatio = newScale / oldScale
                    val cX = centroid.x - (viewW / 2f)
                    val cY = centroid.y - (viewH / 2f)

                    val proposedPanX = (transformState.panX + panChange.x) * scaleRatio + cX * (1f - scaleRatio)
                    val proposedPanY = (transformState.panY + panChange.y) * scaleRatio + cY * (1f - scaleRatio)

                    val clampedPanX = proposedPanX.coerceIn(-maxPanX, maxPanX)
                    val clampedPanY = proposedPanY.coerceIn(-maxPanY, maxPanY)

                    // Only when hitting the vertical edge of the zoomed page does it scroll the list, normalized by zoom scale
                    val overflowY = proposedPanY - clampedPanY
                    if (abs(overflowY) > 0.1f && abs(zoomChange - 1f) < 0.05f) {
                        listState.dispatchRawDelta(-overflowY / newScale)
                    }

                    transformState.setTransform(
                        newScale = newScale,
                        newPanX = clampedPanX,
                        newPanY = clampedPanY
                    )
                } else {
                    // Normal 1.0x view: 2-finger scroll moves document pages at natural, comfortable 1:1 finger velocity
                    if (abs(zoomChange - 1f) < 0.06f && abs(panChange.y) > 0.4f) {
                        listState.dispatchRawDelta(-panChange.y)
                    }
                    transformState.setTransform(newScale, 0f, 0f)
                }
                // Consume all changes during 2-finger touch so inking canvas doesn't draw
                event.changes.forEach { it.consume() }
            } else if (isPanTool && activePointers.size == 1 && transformState.isZoomed) {
                val pointer = activePointers.first()
                if (textSelectionState?.isInteracting == true || pointer.isConsumed) {
                    // Selection handle drag or text drag in progress: yield completely, do NOT pan or scroll!
                    continue
                }

                val dragDelta = pointer.positionChange()
                totalDragDistance += dragDelta.getDistance()

                // Only commit to 2D panning if moved past touch slop, preventing finger micro-tremors from moving page during text hold
                if (totalDragDistance > 14f) {
                    accumulatedX += dragDelta.x
                    accumulatedY += dragDelta.y

                    // Directional Straight-Line Axis Lock:
                    // If user starts scrolling vertically while zoomed, lock sideways movement to 0f
                    // unless they deliberately swipe sideways past intentional threshold!
                    if (dragAxisLock == 0) {
                        if (abs(accumulatedY) > abs(accumulatedX) * 1.35f) {
                            dragAxisLock = 1 // LOCKED VERTICAL: Scrolling in straight line
                        } else if (abs(accumulatedX) > abs(accumulatedY) * 1.35f) {
                            dragAxisLock = 2 // LOCKED HORIZONTAL / FREE 2D
                        }
                    }

                    val effectiveDeltaX = when (dragAxisLock) {
                        1 -> {
                            // In vertical straight-line lock: only allow sideways move if user explicitly drags sideways
                            if (abs(dragDelta.x) > 22f || abs(accumulatedX) > 44f) {
                                dragAxisLock = 2 // Intentional sideways swipe: unlock to 2D
                                dragDelta.x
                            } else {
                                0f // Straight-line scroll: zero sideways drift!
                            }
                        }
                        else -> dragDelta.x
                    }

                    velocityTracker.addPosition(pointer.uptimeMillis, pointer.position)

                    val maxPanX = (viewW * (transformState.scale - 1f)).coerceAtLeast(0f) / 2f
                    val maxPanY = (viewH * (transformState.scale - 1f)).coerceAtLeast(0f) / 2f

                    val proposedPanY = transformState.panY + dragDelta.y
                    val clampedPanY = proposedPanY.coerceIn(-maxPanY, maxPanY)
                    val overflowY = proposedPanY - clampedPanY

                    if (abs(overflowY) > 0.01f) {
                        listState.dispatchRawDelta(-overflowY / transformState.scale)
                    }

                    transformState.setTransform(
                        newScale = transformState.scale,
                        newPanX = (transformState.panX + effectiveDeltaX).coerceIn(-maxPanX, maxPanX),
                        newPanY = clampedPanY
                    )
                    pointer.consume()
                }
            } else if (isPanTool && activePointers.size == 1 && !transformState.isZoomed) {
                // NOT zoomed â€” single finger: track for tap detection but do NOT consume!
                // LazyColumn / HorizontalPager handles scrolling natively
                val pointer = activePointers.first()
                val dragDelta = pointer.positionChange()
                totalDragDistance += dragDelta.getDistance()
            }
        } while (event.changes.any { it.pressed })

        if (isMultiTouch) {
            transformState.endGesture()
        } else {
            transformState.syncDisplayPercent()
        }

        // Smooth spring-back to 1.0f if released below 1.05f
        if (transformState.scale < 1.05f && transformState.scale != 1.0f) {
            coroutineScope.launch {
                transformState.startGesture()
                val initialScale = transformState.scale
                val initialPanX = transformState.panX
                val initialPanY = transformState.panY
                val anim = Animatable(initialScale)
                anim.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f)
                ) {
                    val t = if (1.0f - initialScale != 0f) (value - initialScale) / (1.0f - initialScale) else 1f
                    transformState.setTransform(
                        newScale = value,
                        newPanX = initialPanX * (1f - t),
                        newPanY = initialPanY * (1f - t)
                    )
                }
                transformState.setTransform(1.0f, 0f, 0f)
                transformState.endGesture()
            }
        }

        if (!isMultiTouch && transformState.isZoomed && isPanTool && textSelectionState?.isInteracting != true) {
            val velocity = velocityTracker.calculateVelocity()
            val speed = hypot(velocity.x, velocity.y)
            val maxPanX = (viewW * (transformState.scale - 1f)).coerceAtLeast(0f) / 2f
            val maxPanY = (viewH * (transformState.scale - 1f)).coerceAtLeast(0f) / 2f

            if (speed > 120f) {
                coroutineScope.launch {
                    var currentVx = if (dragAxisLock == 1) 0f else velocity.x
                    var currentVy = velocity.y
                    var lastFrameTimeNanos = System.nanoTime()
                    var isFlinging = true

                    listState.scroll {
                        while (isFlinging && (abs(currentVx) > 5f || abs(currentVy) > 5f)) {
                            withFrameNanos { frameTimeNanos ->
                                val dtNanos = frameTimeNanos - lastFrameTimeNanos
                                lastFrameTimeNanos = frameTimeNanos
                                val dt = (dtNanos / 1_000_000_000f).coerceIn(0.001f, 0.05f)

                                val proposedX = transformState.panX + currentVx * dt
                                val proposedY = transformState.panY + currentVy * dt
                                val clampedX = proposedX.coerceIn(-maxPanX, maxPanX)
                                val clampedY = proposedY.coerceIn(-maxPanY, maxPanY)
                                val overflowY = proposedY - clampedY

                                if (abs(overflowY) > 0.01f) {
                                    scrollBy(-overflowY / transformState.scale)
                                }

                                if (proposedX != clampedX) {
                                    currentVx = 0f
                                }
                                
                                transformState.setTransform(transformState.scale, clampedX, clampedY)
                                
                                val decayFactor = kotlin.math.exp(-dt * 5.0f)
                                currentVx *= decayFactor
                                currentVy *= decayFactor
                                
                                if (abs(currentVx) <= 5f && abs(currentVy) <= 5f) {
                                    isFlinging = false
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isPanTool && !isMultiTouch && totalDragDistance < 25f) {
            val upTime = System.currentTimeMillis()
            if (upTime - downTime < 500) {
                if (upTime - lastTapTime < 300) {
                    // Double tap detected
                    val oldScale = transformState.scale
                    val viewW = size.width.toFloat()
                    val viewH = size.height.toFloat()
                    
                    if (oldScale > 1.1f) {
                        // Zoom out to 1.0x
                        coroutineScope.launch {
                            transformState.startGesture()
                            val anim = Animatable(transformState.scale)
                            val initialPanX = transformState.panX
                            val initialPanY = transformState.panY
                            anim.animateTo(1.0f, spring(dampingRatio = 0.85f, stiffness = 500f)) {
                                val t = if (1.0f - oldScale != 0f) (value - oldScale) / (1.0f - oldScale) else 1f
                                transformState.setTransform(
                                    value,
                                    initialPanX * (1f - t),
                                    initialPanY * (1f - t)
                                )
                            }
                            transformState.setTransform(1.0f, 0f, 0f)
                            transformState.endGesture()
                        }
                    } else {
                        // Zoom in to 2.5x centered on tapped point
                        coroutineScope.launch {
                            transformState.startGesture()
                            val anim = Animatable(transformState.scale)
                            val targetScale = 2.5f
                            val maxPanX = (viewW * (targetScale - 1f)) / 2f
                            val maxPanY = (viewH * (targetScale - 1f)) / 2f

                            val cX = downPos.x - (viewW / 2f)
                            val cY = downPos.y - (viewH / 2f)

                            val targetPanX = (-cX * (targetScale - 1f)).coerceIn(-maxPanX, maxPanX)
                            val targetPanY = (-cY * (targetScale - 1f)).coerceIn(-maxPanY, maxPanY)

                            val initialPanX = transformState.panX
                            val initialPanY = transformState.panY

                            anim.animateTo(targetScale, spring(dampingRatio = 0.85f, stiffness = 500f)) {
                                val currentT = if (targetScale - oldScale != 0f) (value - oldScale) / (targetScale - oldScale) else 1f
                                val currentPanX = initialPanX + (targetPanX - initialPanX) * currentT
                                val currentPanY = initialPanY + (targetPanY - initialPanY) * currentT
                                transformState.setTransform(value, currentPanX, currentPanY)
                            }
                            transformState.endGesture()
                        }
                    }
                    lastTapTime = 0L
                } else {
                    lastTapTime = upTime
                    if (textSelectionState?.isActive == true) {
                        textSelectionState.clearSelection()
                    } else {
                        onToggleUi()
                        if (isThumbnailStripVisible) {
                            onHideThumbnailStrip()
                        }
                        onShowThumbnailArrow()
                    }
                }
            }
        }
    }
}
