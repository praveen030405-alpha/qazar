package com.qazar.pdfviewer.data.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import com.qazar.pdfviewer.data.QazarStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Q-Scan Engine: Multi-Page High-Definition Document Scanner & PDF Synthesizer
 */
object QScanManager {

    private const val TAG = "QScanManager"

    enum class ScanFilter {
        ORIGINAL,
        MAGIC_COLOR,      // Vibrant document clarity (boosts text contrast & cleans background)
        BW_DOCUMENT,      // Crisp binary black & white for legal text
        GRAYSCALE         // Smooth multi-tone monochrome
    }

    data class ScannedPage(
        val originalFile: File,
        val rotationDegrees: Int = 0,
        val filter: ScanFilter = ScanFilter.ORIGINAL
    )

    /**
     * Applies image enhancement filter to a captured document page.
     */
    fun applyFilter(src: Bitmap, filter: ScanFilter): Bitmap {
        val w = src.width
        val h = src.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        when (filter) {
            ScanFilter.ORIGINAL -> {
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
            ScanFilter.MAGIC_COLOR -> {
                // High contrast + subtle brightness boost for ultra-legible text
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, 10f,
                        0f, 1.25f, 0f, 0f, 10f,
                        0f, 0f, 1.25f, 0f, 10f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
            ScanFilter.GRAYSCALE -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
            ScanFilter.BW_DOCUMENT -> {
                // High-pass thresholding for clean document scans
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val m = cm.array
                // Amplify contrast strongly to snap dark ink to black and light paper to pure white
                val contrast = 2.2f
                val translate = (-128f * contrast) + 128f + 15f
                val highContrastMatrix = ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(highContrastMatrix)
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(src, 0f, 0f, paint)
            }
        }

        return output
    }

    /**
     * Compiles a series of scanned pages into an ultra-sharp PDF document in Qazar/Scans/.
     */
    suspend fun generatePdf(
        context: Context,
        pages: List<ScannedPage>,
        documentName: String? = null,
        outputFile: File? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (pages.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No pages to compile into PDF"))
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val title = documentName?.ifBlank { "QScan_$timestamp" } ?: "QScan_$timestamp"
            val pdfFile = outputFile ?: File(QazarStorageManager.getExportsDir(), if (title.endsWith(".pdf", ignoreCase = true)) title else "$title.pdf")

            val pdfDocument = PdfDocument()

            for (i in pages.indices) {
                val p = pages[i]
                val rawBmp = BitmapFactory.decodeFile(p.originalFile.absolutePath) ?: continue

                // Apply rotation if needed
                val rotatedBmp = if (p.rotationDegrees != 0) {
                    val matrix = android.graphics.Matrix().apply { postRotate(p.rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, matrix, true)
                } else rawBmp

                // Apply image enhancement filter
                val filteredBmp = applyFilter(rotatedBmp, p.filter)

                // High-Definition Page Dimension (Target Standard 300 DPI High-Res A4: 1240 x 1754)
                val aspect = filteredBmp.height.toFloat() / filteredBmp.width.toFloat()
                val pageW = 1240
                val pageH = (1240 * aspect).toInt().coerceIn(800, 3200)

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas

                val destRect = android.graphics.Rect(0, 0, pageW, pageH)
                val highResPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    isDither = true
                }
                canvas.drawBitmap(filteredBmp, null, destRect, highResPaint)

                pdfDocument.finishPage(page)

                if (rotatedBmp != rawBmp) rotatedBmp.recycle()
                if (filteredBmp != rotatedBmp) filteredBmp.recycle()
                rawBmp.recycle()
            }

            pdfFile.parentFile?.mkdirs()
            FileOutputStream(pdfFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            MediaScannerConnection.scanFile(
                context,
                arrayOf(pdfFile.absolutePath),
                arrayOf("application/pdf"),
                null
            )

            Log.i(TAG, "Q-Scan PDF successfully generated: ${pdfFile.absolutePath} (${pdfFile.length()} bytes)")
            Result.success(pdfFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compile Q-Scan PDF", e)
            Result.failure(e)
        }
    }
}
