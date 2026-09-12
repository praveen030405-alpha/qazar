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

object WordToPdfConverter {

    suspend fun convert(
        context: Context,
        wordFile: File,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f)
            val pdfDocument = PdfDocument()
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 12f
                isAntiAlias = true
            }

            var currentY = 50f
            val startX = 50f
            val pageHeight = 842
            val pageWidth = 595
            val lineHeight = 16f

            var currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            var currentPage = pdfDocument.startPage(currentPageInfo)
            var canvas = currentPage.canvas

            val zip = ZipFile(wordFile)
            val documentEntry = zip.getEntry("word/document.xml")
            if (documentEntry == null) {
                zip.close()
                return@withContext Result.failure(Exception("Invalid DOCX format: word/document.xml not found"))
            }

            onProgress(0.3f)
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            val inputStream = zip.getInputStream(documentEntry)
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var inParagraph = false
            var inText = false
            val currentText = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "p") {
                            inParagraph = true
                            currentText.clear()
                        } else if (name == "t") {
                            inText = true
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
                        } else if (name == "p") {
                            inParagraph = false
                            val paragraphText = currentText.toString().trim()
                            if (paragraphText.isNotEmpty()) {
                                val words = paragraphText.split(" ")
                                var line = ""
                                for (word in words) {
                                    val testLine = if (line.isEmpty()) word else "$line $word"
                                    if (textPaint.measureText(testLine) > (pageWidth - 100f)) {
                                        canvas.drawText(line, startX, currentY, textPaint)
                                        currentY += lineHeight
                                        line = word
                                        if (currentY > pageHeight - 50f) {
                                            pdfDocument.finishPage(currentPage)
                                            val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
                                            currentPage = pdfDocument.startPage(newPageInfo)
                                            canvas = currentPage.canvas
                                            currentY = 50f
                                        }
                                    } else {
                                        line = testLine
                                    }
                                }
                                if (line.isNotEmpty()) {
                                    canvas.drawText(line, startX, currentY, textPaint)
                                    currentY += lineHeight
                                    if (currentY > pageHeight - 50f) {
                                        pdfDocument.finishPage(currentPage)
                                        val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
                                        currentPage = pdfDocument.startPage(newPageInfo)
                                        canvas = currentPage.canvas
                                        currentY = 50f
                                    }
                                }
                            }
                            currentY += lineHeight
                            if (currentY > pageHeight - 50f) {
                                pdfDocument.finishPage(currentPage)
                                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
                                currentPage = pdfDocument.startPage(newPageInfo)
                                canvas = currentPage.canvas
                                currentY = 50f
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            inputStream.close()
            zip.close()
            onProgress(0.8f)

            pdfDocument.finishPage(currentPage)

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
