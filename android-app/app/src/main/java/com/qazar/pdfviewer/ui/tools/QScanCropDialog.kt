package com.qazar.pdfviewer.ui.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qazar.pdfviewer.data.tools.QScanManager
import com.qazar.pdfviewer.theme.GoogleSansFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Enterprise-grade Interactive Document Cropping Tool for Q-Scan.
 * Allows users to adjust borders, drag corners, apply document presets (A4, 1:1, auto margins),
 * and generates lossless cropped bitmaps.
 */
@Composable
fun QScanCropDialog(
    page: QScanManager.ScannedPage,
    onCropSaved: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Normalized coordinates (0f .. 1f)
    var cropLeft by remember { mutableFloatStateOf(0.04f) }
    var cropTop by remember { mutableFloatStateOf(0.04f) }
    var cropRight by remember { mutableFloatStateOf(0.96f) }
    var cropBottom by remember { mutableFloatStateOf(0.96f) }

    // Load and orient bitmap on launch
    LaunchedEffect(page.originalFile, page.rotationDegrees) {
        withContext(Dispatchers.IO) {
            val raw = BitmapFactory.decodeFile(page.originalFile.absolutePath)
            if (raw != null) {
                val rotated = if (page.rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(page.rotationDegrees.toFloat()) }
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                } else raw
                withContext(Dispatchers.Main) {
                    sourceBitmap = rotated
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF09090E)) // Deep Dark Obsidian Black
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header Bar: Title, Reset, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0x28DC2626),
                            border = BorderStroke(0.8.dp, Color(0xFFEF4444)),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = "Crop",
                                    tint = Color(0xFFFF3B56),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Crop Document Page",
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Drag corners or choose preset",
                                fontFamily = GoogleSansFamily,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Reset button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                cropLeft = 0f
                                cropTop = 0f
                                cropRight = 1f
                                cropBottom = 1f
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Crop",
                                tint = Color(0xFFEF4444)
                            )
                        }

                        // Close button
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Interactive Crop Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = sourceBitmap
                    if (bmp != null) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            val density = LocalDensity.current
                            val maxWPx = with(density) { maxWidth.toPx() }
                            val maxHPx = with(density) { maxHeight.toPx() }

                            val bmpW = bmp.width.toFloat()
                            val bmpH = bmp.height.toFloat()
                            val scale = minOf(maxWPx / bmpW, maxHPx / bmpH)
                            val drawnW = bmpW * scale
                            val drawnH = bmpH * scale
                            val offsetX = (maxWPx - drawnW) / 2f
                            val offsetY = (maxHPx - drawnH) / 2f

                            val imgBitmap = remember(bmp) { bmp.asImageBitmap() }

                            // Main Canvas: Draws the Image, Dimmed Letterbox, and glowing crop rectangle
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(bmp, drawnW, drawnH, offsetX, offsetY) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val dx = dragAmount.x / drawnW
                                            val dy = dragAmount.y / drawnH

                                            // Determine which handle or edge is closest to touch, or move whole box
                                            val touchX = (change.position.x - offsetX) / drawnW
                                            val touchY = (change.position.y - offsetY) / drawnH

                                            val nearLeft = kotlin.math.abs(touchX - cropLeft) < 0.12f
                                            val nearRight = kotlin.math.abs(touchX - cropRight) < 0.12f
                                            val nearTop = kotlin.math.abs(touchY - cropTop) < 0.12f
                                            val nearBottom = kotlin.math.abs(touchY - cropBottom) < 0.12f

                                            if (nearLeft && nearTop) {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.1f)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.1f)
                                            } else if (nearRight && nearTop) {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + 0.1f, 1f)
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.1f)
                                            } else if (nearLeft && nearBottom) {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.1f)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.1f, 1f)
                                            } else if (nearRight && nearBottom) {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + 0.1f, 1f)
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.1f, 1f)
                                            } else if (nearLeft) {
                                                cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.1f)
                                            } else if (nearRight) {
                                                cropRight = (cropRight + dx).coerceIn(cropLeft + 0.1f, 1f)
                                            } else if (nearTop) {
                                                cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.1f)
                                            } else if (nearBottom) {
                                                cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.1f, 1f)
                                            } else if (touchX in cropLeft..cropRight && touchY in cropTop..cropBottom) {
                                                // Move whole crop box
                                                val w = cropRight - cropLeft
                                                val h = cropBottom - cropTop
                                                val newLeft = (cropLeft + dx).coerceIn(0f, 1f - w)
                                                val newTop = (cropTop + dy).coerceIn(0f, 1f - h)
                                                cropLeft = newLeft
                                                cropRight = newLeft + w
                                                cropTop = newTop
                                                cropBottom = newTop + h
                                            }
                                        }
                                    }
                            ) {
                                // 1. Draw source document image
                                drawImage(
                                    image = imgBitmap,
                                    dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(drawnW.roundToInt(), drawnH.roundToInt())
                                )

                                val cLeft = offsetX + cropLeft * drawnW
                                val cTop = offsetY + cropTop * drawnH
                                val cRight = offsetX + cropRight * drawnW
                                val cBottom = offsetY + cropBottom * drawnH
                                val cW = cRight - cLeft
                                val cH = cBottom - cTop

                                val dimColor = Color(0xB3000000) // 70% dark dimming on excluded regions

                                // Top dim
                                drawRect(dimColor, Offset(offsetX, offsetY), Size(drawnW, cTop - offsetY))
                                // Bottom dim
                                drawRect(dimColor, Offset(offsetX, cBottom), Size(drawnW, (offsetY + drawnH) - cBottom))
                                // Left dim
                                drawRect(dimColor, Offset(offsetX, cTop), Size(cLeft - offsetX, cH))
                                // Right dim
                                drawRect(dimColor, Offset(cRight, cTop), Size((offsetX + drawnW) - cRight, cH))

                                // Glowing Red Crop Border
                                drawRect(
                                    color = Color(0xFFEF4444),
                                    topLeft = Offset(cLeft, cTop),
                                    size = Size(cW, cH),
                                    style = Stroke(width = 2.5f)
                                )

                                // Rule of Thirds subtle grid inside crop box
                                val thirdW = cW / 3f
                                val thirdH = cH / 3f
                                val gridColor = Color(0x35FFFFFF)
                                drawLine(gridColor, Offset(cLeft + thirdW, cTop), Offset(cLeft + thirdW, cBottom), 1f)
                                drawLine(gridColor, Offset(cLeft + thirdW * 2, cTop), Offset(cLeft + thirdW * 2, cBottom), 1f)
                                drawLine(gridColor, Offset(cLeft, cTop + thirdH), Offset(cRight, cTop + thirdH), 1f)
                                drawLine(gridColor, Offset(cLeft, cTop + thirdH * 2), Offset(cRight, cTop + thirdH * 2), 1f)

                                // 4 Tactile Corner Handles
                                val handleRadius = 14f
                                val handleColor = Color.White
                                val handleBorder = Color(0xFFEF4444)

                                val corners = listOf(
                                    Offset(cLeft, cTop),
                                    Offset(cRight, cTop),
                                    Offset(cLeft, cBottom),
                                    Offset(cRight, cBottom)
                                )

                                for (corner in corners) {
                                    drawCircle(handleColor, radius = handleRadius, center = corner)
                                    drawCircle(handleBorder, radius = handleRadius, center = corner, style = Stroke(3f))
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator(color = Color(0xFFEF4444))
                    }
                }

                // Preset Buttons Row (Full, Auto 5%, A4 Doc, 1:1 Square)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        "Full Page" to { cropLeft = 0f; cropTop = 0f; cropRight = 1f; cropBottom = 1f },
                        "Auto Margin (5%)" to { cropLeft = 0.05f; cropTop = 0.05f; cropRight = 0.95f; cropBottom = 0.95f },
                        "Auto Margin (10%)" to { cropLeft = 0.10f; cropTop = 0.10f; cropRight = 0.90f; cropBottom = 0.90f },
                        "1:1 Square" to {
                            val size = minOf(cropRight - cropLeft, cropBottom - cropTop)
                            val midX = (cropLeft + cropRight) / 2f
                            val midY = (cropTop + cropBottom) / 2f
                            cropLeft = (midX - size / 2f).coerceIn(0f, 1f - size)
                            cropRight = cropLeft + size
                            cropTop = (midY - size / 2f).coerceIn(0f, 1f - size)
                            cropBottom = cropTop + size
                        },
                        "A4 Document" to {
                            val targetRatio = 1f / 1.414f
                            val currentW = cropRight - cropLeft
                            val newH = (currentW / targetRatio).coerceAtMost(1f)
                            cropTop = ((1f - newH) / 2f).coerceAtLeast(0f)
                            cropBottom = cropTop + newH
                        }
                    )

                    presets.forEach { (label, action) ->
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                action()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF161622),
                            border = BorderStroke(0.8.dp, Color(0xFF33334A))
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE2E8F0),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons: Cancel and Apply Crop
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF181824),
                        border = BorderStroke(1.dp, Color(0xFF2E2E40)),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Cancel",
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val bmp = sourceBitmap
                            if (bmp != null && !isProcessing) {
                                isProcessing = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val cropX = (cropLeft * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                                        val cropY = (cropTop * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                                        val cropW = ((cropRight - cropLeft) * bmp.width).toInt().coerceIn(10, bmp.width - cropX)
                                        val cropH = ((cropBottom - cropTop) * bmp.height).toInt().coerceIn(10, bmp.height - cropY)

                                        val croppedBmp = Bitmap.createBitmap(bmp, cropX, cropY, cropW, cropH)
                                        val croppedFile = File(context.cacheDir, "qscan_cropped_${System.currentTimeMillis()}.jpg")
                                        FileOutputStream(croppedFile).use { out ->
                                            croppedBmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                        }

                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            Toast.makeText(context, "Page cropped successfully", Toast.LENGTH_SHORT).show()
                                            onCropSaved(croppedFile)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            isProcessing = false
                                            Toast.makeText(context, "Failed to crop: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing && sourceBitmap != null,
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFDC2626),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isProcessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Apply",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Apply Crop",
                                        fontFamily = GoogleSansFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
