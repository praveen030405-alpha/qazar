package com.qazar.pdfviewer.ui.viewer

import androidx.compose.runtime.*

enum class PageFitMode {
    FIT_PAGE,
    FIT_WIDTH,
    FIT_HEIGHT
}

/**
 * High-Performance Zero-Latency 2D Hardware Transform State for Meridian Viewport.
 *
 * Engineered to eliminate Compose recomposition and layout overhead entirely during touch gestures:
 * - [scale], [panX], [panY], [rotation] are updated continuously at 120 FPS.
 * - They are ONLY read inside the hardware RenderNode draw phase via Modifier.graphicsLayer { ... }.
 * - Zero Composable measure or layout passes occur during pinch or pan!
 * - Discrete flags [isZoomed] (> 1.05f) and [isDeepZoomActive] (> 1.8f) only notify when crossing thresholds.
 * - [displayZoomPercent] is synchronized only on gesture completion or explicit button clicks.
 */
@Stable
class ViewportHardwareTransformState {
    // Hardware render layer properties â€” ONLY read in graphicsLayer draw scope!
    var scale by mutableFloatStateOf(1.0f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var rotation by mutableFloatStateOf(0f)

    var fitMode by mutableStateOf(PageFitMode.FIT_PAGE)

    // Discrete boolean: only true when zoomed in beyond threshold
    var isZoomed by mutableStateOf(false)
        private set

    var isDeepZoomActive by mutableStateOf(false)
        private set

    var displayZoomPercent by mutableIntStateOf(100)
        private set

    // Gesture Lifecycle States for 120 FPS Smoothness:
    // When true, gestures or animations are active -> bypass Composable recomposition entirely!
    var isGestureActive by mutableStateOf(false)

    // Settled zoom scale: Only updated when gesture completes (fingers lifted / anim done).
    // Background high-res tile/slab requests ONLY listen to this, ensuring 0 thrashing during pinch!
    var settledScale by mutableFloatStateOf(1.0f)
        private set

    fun setTransform(newScale: Float, newPanX: Float, newPanY: Float, newRotation: Float = rotation) {
        scale = newScale
        panX = newPanX
        panY = newPanY
        rotation = newRotation

        val zoomed = newScale > 1.05f
        if (isZoomed != zoomed) {
            isZoomed = zoomed
        }

        val deep = newScale > 1.25f
        if (isDeepZoomActive != deep) {
            isDeepZoomActive = deep
        }
    }

    fun startGesture() {
        isGestureActive = true
    }

    fun endGesture() {
        isGestureActive = false
        settledScale = scale
        syncDisplayPercent()
    }

    fun syncDisplayPercent() {
        val pct = (scale * 100).toInt()
        if (displayZoomPercent != pct) {
            displayZoomPercent = pct
        }
    }

    fun reset() {
        scale = 1.0f
        settledScale = 1.0f
        panX = 0f
        panY = 0f
        rotation = 0f
        fitMode = PageFitMode.FIT_PAGE
        isZoomed = false
        isDeepZoomActive = false
        isGestureActive = false
        displayZoomPercent = 100
    }
}
