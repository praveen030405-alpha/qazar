package com.qazar.pdfviewer.data.converter

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ExcelToPdfConverter {

    suspend fun convert(
        context: Context,
        excelFile: File,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.1f)
            val pdfDocument = PdfDocument()
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 10f
                isAntiAlias = true
            }
            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

            val pageHeight = 842
            val pageWidth = 595
            var currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            var currentPage = pdfDocument.startPage(currentPageInfo)
            var canvas = currentPage.canvas

            val zip = ZipFile(excelFile)
            val sharedStringsEntry = zip.getEntry("xl/sharedStrings.xml")
            val sharedStrings = mutableListOf<String>()

            onProgress(0.2f)
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true

            // 1. Extract shared strings if present
            if (sharedStringsEntry != null) {
                val parser = factory.newPullParser()
                val inputStream = zip.getInputStream(sharedStringsEntry)
                parser.setInput(inputStream, "UTF-8")
                var eventType = parser.eventType
                var inT = false
                val currentText = StringBuilder()
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    val name = parser.name
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (name == "t") {
                                inT = true
                                currentText.clear()
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inT) {
                                currentText.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (name == "t") {
                                inT = false
                                sharedStrings.add(currentText.toString())
                            }
                        }
                    }
                    eventType = parser.next()
                }
                inputStream.close()
            }

            onProgress(0.4f)
            // 2. Extract sheet data
            val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            if (sheetEntry == null) {
                zip.close()
                return@withContext Result.failure(Exception("Invalid XLSX format: xl/worksheets/sheet1.xml not found"))
            }

            val parser = factory.newPullParser()
            val inputStream = zip.getInputStream(sheetEntry)
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var inCell = false
            var inValue = false
            var cellType = ""
            var currentText = StringBuilder()
            
            var currentY = 50f
            val startX = 50f
            val cellWidth = 100f
            val rowHeight = 20f
            var currentCol = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "row") {
                            currentCol = 0
                        } else if (name == "c") {
                            inCell = true
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                        } else if (name == "v") {
                            inValue = true
                            currentText.clear()
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inValue) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "v") {
                            inValue = false
                        } else if (name == "c") {
                            inCell = false
                            val valueStr = currentText.toString()
                            var displayValue = valueStr
                            if (cellType == "s") {
                                val idx = valueStr.toIntOrNull()
                                if (idx != null && idx >= 0 && idx < sharedStrings.size) {
                                    displayValue = sharedStrings[idx]
                                }
                            }
                            
                            val xPos = startX + (currentCol * cellWidth)
                            // Basic wrapping or truncation for cell value
                            val truncated = if (textPaint.measureText(displayValue) > cellWidth - 4) {
                                displayValue.take(12) + "..."
                            } else {
                                displayValue
                            }
                            
                            if (xPos + cellWidth <= pageWidth - 50f) {
                                canvas.drawRect(xPos, currentY, xPos + cellWidth, currentY + rowHeight, linePaint)
                                canvas.drawText(truncated, xPos + 4f, currentY + 14f, textPaint)
                            }
                            currentCol++
                        } else if (name == "row") {
                            currentY += rowHeight
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
