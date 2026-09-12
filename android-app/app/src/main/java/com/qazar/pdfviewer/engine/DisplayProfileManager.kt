package com.qazar.pdfviewer.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager

/**
 * Meridian Flagship Display Profile Manager.
 *
 * Core Philosophy: EXACT 1:1 HARDWARE PIXEL PARITY as the primary rendering target.
 * Instead of wasteful 2x supersampling (4x pixel count, 4x CPU, 4x battery drain),
 * we render at exactly 1 PDF pixel = 1 physical screen pixel. Combined with stem hinting
 * and thin-stroke snapping, this produces razor-sharp text with 75% LESS compute and memory.
 *
 * DPI Tiers:
 *   - thumbnailDpi:    Ultra-fast skeleton (64-72 DPI, ~200KB, always cached)
 *   - scrollDpi:       1:1 hardware pixel parity (120-140 DPI, 1.5MB per page)
 *   - readingDpi:      1.5x supersample for settled reading sharpness (180-210 DPI)
 *   - deepZoomDpi:     2x supersample for pinch-zoom sharpness (260-350 DPI)
 *
 * Features:
 *   - Stem hinting for laser-crisp text edges
 *   - Thin-stroke snapping for razor-sharp table borders & diagram lines
 *   - Thermal throttle to prevent device overheating
 *   - 4-tier velocity-gated quality escalation
 */
object DisplayProfileManager {
    private const val TAG = "DisplayProfileManager"

    private var isInitialized = false

    // --- 4-Tier DPI System ---
    /** Ultra-lightweight skeleton DPI for background thumbnails (<4ms render) */
    var thumbnailDpi: Float = 72f
        private set

    /** Exact 1:1 hardware pixel parity DPI. Used during fast scrolling and as the baseline render. */
    var scrollDpi: Float = 180f
        private set

    /** High-fidelity reading DPI. 2.14x supersampled for crystal-sharp print-quality text on fit-screen. */
    var readingDpi: Float = 280f
        private set

    /** High-resolution DPI for progressive tile rendering when zoomed in. */
    var deepZoomDpi: Float = 450f
        private set

    // --- Legacy aliases for backward compatibility (maps to new tier system) ---
    val fastScrollDpi: Float get() = scrollDpi
    val retinaReadingDpi: Float get() = readingDpi
    val thumbnailPreviewDpi: Float get() = thumbnailDpi

    // --- Quality Enhancement Flags ---
    /** Enable FreeType stem hinting (snaps glyph stems to pixel grid for laser-crisp letters) */
    var stemHintingEnabled: Boolean = true
        private set

    /** Enable thin-stroke snapping (rounds strokes â‰¤1.5pt to nearest half-pixel for sharp table lines) */
    var thinStrokeSnapping: Boolean = true
        private set

    // --- Memory Budget ---
    var maxCacheBudgetMb: Long = 128
        private set

    // --- Hardware Profile Cache ---
    var screenWidthPx: Int = 1080
        private set
    var screenHeightPx: Int = 1920
        private set
    var refreshRateHz: Float = 60f
        private set
    var hardware1x1Dpi: Float = 130f
        private set

    // --- Thermal State ---
    @Volatile
    private var thermalThrottleFactor: Float = 1.0f // 1.0 = no throttle, 0.85 = 15% reduction

    fun initialize(context: Context) {
        if (isInitialized) return

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val largeMemoryClass = am?.largeMemoryClass ?: 256

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { context.display } catch (_: Throwable) { null }
        } else {
            @Suppress("DEPRECATION")
            wm?.defaultDisplay
        }

        refreshRateHz = display?.mode?.refreshRate ?: 60f
        screenWidthPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(1080)
        screenHeightPx = context.resources.displayMetrics.heightPixels.coerceAtLeast(1920)

        // Adaptive Memory Scaling (safe 50% of large heap)
        maxCacheBudgetMb = (largeMemoryClass * 0.50f).toLong().coerceIn(128, 512)

        // --- Core DPI Calculation ---
        // A4 width = 595.28pt. Exact 1:1 DPI = screenWidthPx / (595.28 / 72)
        hardware1x1Dpi = screenWidthPx * (72f / 595.28f) // ~130 DPI for 1080px
        val devicePhysicalDpi = context.resources.displayMetrics.densityDpi.toFloat()

        when {
            // Flagship (Large heap >= 512MB or 120Hz+)
            largeMemoryClass >= 512 || refreshRateHz >= 120f -> {
                thumbnailDpi = 72f
                scrollDpi = (hardware1x1Dpi * 1.55f).coerceIn(180f, 240f)
                readingDpi = maxOf(hardware1x1Dpi * 2.5f, devicePhysicalDpi * 0.75f).coerceIn(320f, 420f)
                deepZoomDpi = maxOf(hardware1x1Dpi * 4.0f, devicePhysicalDpi * 1.25f).coerceIn(520f, 650f)
                stemHintingEnabled = true
                thinStrokeSnapping = true
            }
            // Mid-Range (384MB+ or 90Hz+)
            largeMemoryClass >= 384 || refreshRateHz >= 90f -> {
                thumbnailDpi = 64f
                scrollDpi = (hardware1x1Dpi * 1.35f).coerceIn(160f, 210f)
                readingDpi = maxOf(hardware1x1Dpi * 2.2f, devicePhysicalDpi * 0.70f).coerceIn(280f, 360f)
                deepZoomDpi = maxOf(hardware1x1Dpi * 3.5f, devicePhysicalDpi * 1.10f).coerceIn(450f, 560f)
                stemHintingEnabled = true
                thinStrokeSnapping = true
            }
            // Budget (Low RAM, 60Hz)
            else -> {
                thumbnailDpi = 54f
                scrollDpi = (hardware1x1Dpi * 1.2f).coerceIn(140f, 180f)
                readingDpi = (hardware1x1Dpi * 1.8f).coerceIn(240f, 300f)
                deepZoomDpi = (hardware1x1Dpi * 3.0f).coerceIn(380f, 480f)
                stemHintingEnabled = true
                thinStrokeSnapping = false // Save CPU on budget devices
            }
        }

        Log.i(TAG, "Initialized Meridian Display Profile: " +
            "1:1=${hardware1x1Dpi.toInt()} scroll=${scrollDpi.toInt()} " +
            "reading=${readingDpi.toInt()} deep=${deepZoomDpi.toInt()} " +
            "budget=${maxCacheBudgetMb}MB ${screenWidthPx}x${screenHeightPx}@${refreshRateHz.toInt()}Hz " +
            "physicalDpi=${devicePhysicalDpi.toInt()} stemHint=$stemHintingEnabled strokeSnap=$thinStrokeSnapping"
        )

        isInitialized = true
    }

    /**
     * 4-Tier Velocity-Gated Quality Escalation.
     * Smoothly transitions between DPI tiers based on scroll speed:
     *   - Violent fling (>1500 dp/s): thumbnail DPI â€” human eye can't resolve detail during fast motion
     *   - Fast scroll (>600 dp/s):    1:1 scroll DPI â€” crisp but lightweight
     *   - Slow read (<600 dp/s):      1.5x reading DPI â€” enhanced sharpness
     *   - Resting (0 dp/s):           Use reading DPI (deep zoom handled separately)
     */
    fun getEscalatedDpi(scrollVelocityDpPerSec: Float): Float {
        val baseDpi = when {
            scrollVelocityDpPerSec > 1500f -> thumbnailDpi  // Eye can't resolve at this speed
            scrollVelocityDpPerSec > 600f  -> scrollDpi     // 1:1 pixel parity
            else                           -> readingDpi    // Settled reading sharpness
        }
        return baseDpi * thermalThrottleFactor
    }

    /**
     * Apply thermal throttle factor (called by ThermalGovernor).
     * @param factor 0.7 to 1.0 â€” reduces all DPI targets proportionally when device heats up.
     */
    fun applyThermalThrottle(factor: Float) {
        thermalThrottleFactor = factor.coerceIn(0.7f, 1.0f)
        Log.i(TAG, "Thermal throttle applied: factor=${thermalThrottleFactor}")
    }

    /** Reset thermal throttle to full quality */
    fun clearThermalThrottle() {
        thermalThrottleFactor = 1.0f
    }

    /** Current effective thermal multiplier */
    val currentThermalFactor: Float get() = thermalThrottleFactor
}
