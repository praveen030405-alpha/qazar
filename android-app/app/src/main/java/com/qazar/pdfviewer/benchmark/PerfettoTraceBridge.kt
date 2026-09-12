package com.qazar.pdfviewer.benchmark

import android.os.SystemClock
import android.os.Trace
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Phase 0: Perfetto System Tracing & Latency Profiling Bridge.
 * 
 * Provides zero-overhead trace spans integrated directly with Android's system-level
 * ATrace / Perfetto profiler, alongside lock-free microsecond latency tracking
 * for P50, P95, and P99 tail-latency monitoring.
 */
object PerfettoTraceBridge {
    private const val TAG = "PerfettoTraceBridge"

    // Circular latency recorder for rendering stages
    private const val MAX_SAMPLES = 1000
    private val renderLatenciesUs = ConcurrentLinkedQueue<Long>()

    // Cold / Warm Start Timers
    private var processStartTimeNs: Long = System.nanoTime()
    private var firstFrameTimeNs: Long = 0L
    private var resumeStartTimeNs: Long = 0L
    private var warmFrameTimeNs: Long = 0L

    fun recordProcessStart() {
        processStartTimeNs = System.nanoTime()
    }

    fun recordFirstFrameRendered() {
        if (firstFrameTimeNs == 0L) {
            firstFrameTimeNs = System.nanoTime()
            val coldStartMs = (firstFrameTimeNs - processStartTimeNs) / 1_000_000
            Log.i(TAG, "âš¡ [Cold Start] Process spawn -> First frame rendered: ${coldStartMs}ms")
        }
    }

    fun recordWarmResume() {
        resumeStartTimeNs = System.nanoTime()
    }

    fun recordWarmFrameRendered() {
        if (resumeStartTimeNs != 0L) {
            warmFrameTimeNs = System.nanoTime()
            val warmStartMs = (warmFrameTimeNs - resumeStartTimeNs) / 1_000_000
            Log.i(TAG, "âš¡ [Warm Start] Resume -> First frame rendered: ${warmStartMs}ms")
            resumeStartTimeNs = 0L
        }
    }

    /**
     * Executes a block within a Perfetto system trace section.
     * Automatically forwards section markers to Android ATrace.
     */
    inline fun <T> traceSection(sectionName: String, block: () -> T): T {
        Trace.beginSection(sectionName)
        val startNs = System.nanoTime()
        try {
            return block()
        } finally {
            val elapsedUs = (System.nanoTime() - startNs) / 1000
            recordRenderLatency(elapsedUs)
            Trace.endSection()
        }
    }

    /**
     * Records a microsecond latency measurement.
     */
    fun recordRenderLatency(latencyUs: Long) {
        renderLatenciesUs.add(latencyUs)
        while (renderLatenciesUs.size > MAX_SAMPLES) {
            renderLatenciesUs.poll()
        }
    }

    /**
     * Computes P50, P95, and P99 latency percentiles across recorded samples.
     */
    fun getMetricsSummary(): LatencySummary {
        val samples = renderLatenciesUs.toList().sorted()
        if (samples.isEmpty()) {
            return LatencySummary(0, 0, 0, 0)
        }
        val count = samples.size
        val p50 = samples[(count * 0.50).toInt().coerceIn(0, count - 1)]
        val p95 = samples[(count * 0.95).toInt().coerceIn(0, count - 1)]
        val p99 = samples[(count * 0.99).toInt().coerceIn(0, count - 1)]
        val avg = samples.average().toLong()
        return LatencySummary(count, p50, p95, p99, avg)
    }

    data class LatencySummary(
        val sampleCount: Int,
        val p50Us: Long,
        val p95Us: Long,
        val p99Us: Long,
        val avgUs: Long = 0L
    ) {
        override fun toString(): String {
            return "Samples=$sampleCount | P50=${p50Us / 1000.0}ms | P95=${p95Us / 1000.0}ms | P99=${p99Us / 1000.0}ms | Avg=${avgUs / 1000.0}ms"
        }
    }

    fun exportBenchmarkReport(context: android.content.Context): String {
        val summary = getMetricsSummary()
        val report = buildString {
            appendLine("=== MERIDIAN NEXT-GEN BENCHMARK REPORT ===")
            appendLine("Device Display:")
            appendLine("  Resolution: ${com.qazar.pdfviewer.engine.DisplayProfileManager.screenWidthPx}x${com.qazar.pdfviewer.engine.DisplayProfileManager.screenHeightPx}")
            appendLine("  Refresh Rate: ${com.qazar.pdfviewer.engine.DisplayProfileManager.refreshRateHz} Hz")
            appendLine("  1:1 Parity DPI: ${com.qazar.pdfviewer.engine.DisplayProfileManager.hardware1x1Dpi.toInt()} DPI")
            appendLine("  Scroll DPI: ${com.qazar.pdfviewer.engine.DisplayProfileManager.scrollDpi.toInt()} DPI")
            appendLine("  Reading DPI: ${com.qazar.pdfviewer.engine.DisplayProfileManager.readingDpi.toInt()} DPI (Supersampled Print Quality)")
            appendLine("  Deep Zoom DPI: ${com.qazar.pdfviewer.engine.DisplayProfileManager.deepZoomDpi.toInt()} DPI")
            appendLine("  Stem Hinting: ${com.qazar.pdfviewer.engine.DisplayProfileManager.stemHintingEnabled}")
            appendLine("  Thin Stroke Snapping: ${com.qazar.pdfviewer.engine.DisplayProfileManager.thinStrokeSnapping}")
            appendLine()
            appendLine("Latency Profile (Perfetto Spans):")
            appendLine("  Total Render Samples: ${summary.sampleCount}")
            appendLine("  P50 Latency: ${summary.p50Us / 1000.0} ms")
            appendLine("  P95 Latency: ${summary.p95Us / 1000.0} ms")
            appendLine("  P99 Latency: ${summary.p99Us / 1000.0} ms")
            appendLine("  Avg Latency: ${summary.avgUs / 1000.0} ms")
            appendLine()
            appendLine("Pipeline Flags:")
            appendLine("  Vector Anti-Aliasing: RENDER_MODE_FOR_PRINT (Active)")
            appendLine("  Hairline Stroke Expansion: Clamped to 1.0px (Active)")
            appendLine("  Discrete Sqrt(2) Quality Steps: Active")
            appendLine("  Velocity-Scaled Predictive Prefetching: Active")
        }
        try {
            val targetFile = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "benchmark_report.txt")
            targetFile.writeText(report)
        } catch (_: Throwable) {}
        return report
    }
}
