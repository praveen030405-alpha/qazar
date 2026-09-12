package com.qazar.pdfviewer.data.converter

import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object PptToPdfConverter {

    suspend fun convert(
        context: Context,
        pptFile: File,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f)
            val pdfDocument = PdfDocument()
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 14f
                isAntiAlias = true
            }

            // Landscape standard PowerPoint slide aspect ratio (16:9 approx)
            val pageWidth = 960
            val pageHeight = 540
            
            val zip = ZipFile(pptFile)
            
            // Find all slides
            val slides = zip.entries().toList().filter { 
                it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml")
            }.sortedBy { 
                // Sort slide1.xml, slide2.xml etc correctly
                val num = it.name.replace(Regex("[^0-9]"), "")
                if (num.isNotEmpty()) num.toInt() else 0
            }
            
            if (slides.isEmpty()) {
                zip.close()
                return@withContext Result.failure(Exception("Invalid PPTX format: no slides found"))
            }

            onProgress(0.2f)
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true

            slides.forEachIndexed { index, slideEntry ->
                var currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                var currentPage = pdfDocument.startPage(currentPageInfo)
                var canvas = currentPage.canvas

                // Draw a simple white background
                canvas.drawColor(Color.WHITE)

                val parser = factory.newPullParser()
                val inputStream = zip.getInputStream(slideEntry)
                parser.setInput(inputStream, "UTF-8")

                var eventType = parser.eventType
                var inText = false
                var currentText = StringBuilder()
                var currentY = 50f
                val startX = 50f
                val lineHeight = 20f

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (name == "t") {
                                inText = true
                                currentText.clear()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inText) {
                                currentText.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "t") {
                                inText = false
                                val textStr = currentText.toString().trim()
                                if (textStr.isNotEmpty()) {
                                    val words = textStr.split(" ")
                                    var line = ""
                                    for (word in words) {
                                        val testLine = if (line.isEmpty()) word else "$line $word"
                                        if (textPaint.measureText(testLine) > (pageWidth - 100f)) {
                                            canvas.drawText(line, startX, currentY, textPaint)
                                            currentY += lineHeight
                                            line = word
                                        } else {
                                            line = testLine
                                        }
                                    }
                                    if (line.isNotEmpty()) {
                                        canvas.drawText(line, startX, currentY, textPaint)
                                        currentY += lineHeight
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }

                inputStream.close()
                pdfDocument.finishPage(currentPage)
                
                onProgress(0.2f + (0.6f * ((index + 1) / slides.size.toFloat())))
            }

            zip.close()
            onProgress(0.8f)

            targetFile.parentFile?.mkdirs()
            val outputStream = FileOutputStream(targetFile)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            
            onProgress(1.0f)
            Result.success(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
