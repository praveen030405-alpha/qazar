package com.qazar.pdfviewer.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Generator for the official "About Qazar PDF Viewer" PDF document.
 * Creates a beautifully structured, typographic A4 PDF document on device.
 */
object AboutPdfGenerator {

    fun getOrCreateAboutPdf(context: Context): File {
        val file = File(context.cacheDir, "About_Qazar_PDF_Viewer.pdf")
        if (file.exists() && file.length() > 1000L) {
            return file
        }

        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // ISO A4
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        // Background subtle paper tone
        val bgPaint = Paint().apply { color = android.graphics.Color.rgb(252, 250, 248) }
        canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

        // Top decorative brand banner
        val bannerPaint = Paint().apply { color = android.graphics.Color.rgb(220, 38, 38) } // Crimson #DC2626
        canvas.drawRect(40f, 40f, 555f, 46f, bannerPaint)

        // Title Paint
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(15, 23, 42)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("QAZAR QUANTUM PDF VIEWER", 40f, 80f, titlePaint)

        // Subtitle Paint
        val subPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(220, 38, 38)
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("NEXT-GENERATION HIGH VELOCITY DOCUMENT ENGINE", 40f, 100f, subPaint)

        // Divider
        val divPaint = Paint().apply {
            color = android.graphics.Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }
        canvas.drawLine(40f, 114f, 555f, 114f, divPaint)

        // Body Text Paint
        val bodyPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(51, 65, 85)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        val boldPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(15, 23, 42)
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var y = 140f
        canvas.drawText("1. EXECUTIVE OVERVIEW & ARCHITECTURE PRINCIPLES", 40f, y, boldPaint)
        y += 18f
        canvas.drawText("Qazar is a clean-sheet, high-performance document viewer designed for instantaneous sub-300ms", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("cold opens, fluid 120Hz scrolling, and sub-pixel vector rendering. Rejecting legacy bloated WebViews", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("and Electron containers, Qazar couples a hardened native Rust systems engine with modern Jetpack Compose.", 40f, y, bodyPaint)

        y += 30f
        canvas.drawText("2. CORE ENGINEERING ADVANTAGES", 40f, y, boldPaint)
        y += 18f

        val features = listOf(
            "â€¢ 120Hz Hardware Pipeline: Velocity-predictive multi-tier tile scheduler with zero critical-path rasterization.",
            "â€¢ Tantivy Text Engine: Full-text inverted index delivering sub-millisecond document search and visual heatmap radar.",
            "â€¢ Bradford Color Adaptation: Linear-light eye-comfort substrates (Standard D65, Paper 5500K, Archival Sepia 4500K).",
            "â€¢ SQLite WAL CRDT Annotation Engine: Append-only operation logs guaranteeing lossless ISO 32000 export and full undo.",
            "â€¢ Cryptographic Integrity & Redaction: True stream sanitization with SHA-256 hash-chained audit logging."
        )

        for (feat in features) {
            canvas.drawText(feat, 40f, y, bodyPaint)
            y += 18f
        }

        y += 20f
        canvas.drawText("3. SYSTEM & RUNTIME SPECIFICATIONS", 40f, y, boldPaint)
        y += 18f
        canvas.drawText("â€¢ App Edition: Qazar Quantum Edition v2.4.0", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("â€¢ Native Core: Rust (Edition 2024) with PDFium C++ FFI sandbox", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("â€¢ Memory Ceiling: Enforced <=250MB resident allocation with active memory governor", 40f, y, bodyPaint)
        y += 15f
        canvas.drawText("â€¢ Privacy Guarantee: 100% Offline-first. Zero telemetry transmission or external data leakage.", 40f, y, bodyPaint)

        y += 35f
        canvas.drawLine(40f, y, 555f, y, divPaint)
        y += 22f

        val footerPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.rgb(148, 163, 184)
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        canvas.drawText("Developed by Quantum Labs â€¢ Meridian Quantum Engineering Lab â€¢ Built with Precision", 40f, y, footerPaint)

        pdfDoc.finishPage(page)

        try {
            FileOutputStream(file).use { out ->
                pdfDoc.writeTo(out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDoc.close()
        }

        return file
    }
}
