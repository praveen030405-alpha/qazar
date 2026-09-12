package com.qazar.pdfviewer.ui.viewer

import androidx.compose.ui.geometry.Rect

/**
 * High-fidelity PDF Document representation for Jetpack Compose UI.
 * Mirrors the Quantum Architecture models in pdf-kernel, tile-engine, and text-engine.
 */
data class PdfPageModel(
    val pageIndex: Int,
    val widthPoints: Float = 595f,
    val heightPoints: Float = 842f, // Standard A4 ratio 1 : 1.4142
    val chars: List<ExtractedCharModel> = emptyList(),
    val isRendered: Boolean = true,
    val bitmap: android.graphics.Bitmap? = null,
    val isVirtual: Boolean = false
) {
    val aspectRatio: Float get() = widthPoints / heightPoints
}

data class ExtractedCharModel(
    val char: Char,
    val bounds: Rect,
    val isRtl: Boolean = false,
    val isCjk: Boolean = false
)

data class SearchHitModel(
    val pageIndex: Int,
    val snippet: String,
    val highlightBounds: List<Rect>
)

data class ArchitectureTelemetryState(
    val fps: Float = 120.0f,
    val frameTimeMs: Float = 0.83f,
    val coldOpenMs: Float = 103.9f,
    val l0VramUsedMb: Float = 6.25f,
    val l0VramMaxMb: Float = 96.0f,
    val l1RamUsedMb: Float = 7.25f,
    val l1RamMaxMb: Float = 96.0f,
    val governorTotalMb: Float = 13.50f,
    val governorCeilingMb: Float = 250.0f,
    val searchLatencyMs: Float = 6.65f,
    val activeTier: String = "L0 (GPU Atlas) + L1 (Near-Viewport)",
    val iccProfile: String = "Display P3 (Wide Gamut)",
    val deltaE2000: Float = 0.87f,
    val colorContrastRatio: Float = 20.1f,
    val melanopicReductionPct: Float = 0.0f
)
