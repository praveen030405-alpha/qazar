package com.qazar.pdfviewer.ui.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.qazar.pdfviewer.data.QazarStorageManager
import com.qazar.pdfviewer.data.tools.QScanManager
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.GoogleSansTextFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Q-Scan Camera Interface & Document Synthesizer
 *
 * Fully integrated with real runtime camera permissions, hardware torch,
 * live viewfinder, bottom page navigation strip, page reordering, image filters,
 * and standard export saving with naming popup.
 */
@Composable
fun QScanCameraScreen(
    onClose: () -> Unit,
    onPdfGenerated: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // 1. Camera Permission State & Launcher
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var capturedPages by remember { mutableStateOf<List<QScanManager.ScannedPage>>(emptyList()) }
    var isFlashEnabled by remember { mutableStateOf(false) }
    var isSuperBright by remember { mutableStateOf(true) }
    var isAutoDetectActive by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var isCompilingPdf by remember { mutableStateOf(false) }
    var isReviewingBatch by remember { mutableStateOf(false) }
    var reviewInitialIndex by remember { mutableStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var completedConversionFile by remember { mutableStateOf<File?>(null) }

    // CameraX instance references
    var cameraControlInstance by remember { mutableStateOf<CameraControl?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    // Gallery Picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val newPages = mutableListOf<QScanManager.ScannedPage>()
                for (uri in uris) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri) ?: continue
                        val tempFile = File(context.cacheDir, "qscan_import_${UUID.randomUUID()}.jpg")
                        FileOutputStream(tempFile).use { fos -> inputStream.copyTo(fos) }
                        newPages.add(QScanManager.ScannedPage(tempFile))
                    } catch (_: Exception) {}
                }
                withContext(Dispatchers.Main) {
                    capturedPages = capturedPages + newPages
                    if (capturedPages.isNotEmpty()) {
                        reviewInitialIndex = capturedPages.lastIndex
                        isReviewingBatch = true
                    }
                }
            }
        }
    }

    // Camera lifecycle binding - only runs when camera permission is active
    LaunchedEffect(hasCameraPermission, isSuperBright) {
        if (hasCameraPermission) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    cameraControlInstance = camera.cameraControl
                    // Apply torch state if active
                    camera.cameraControl.enableTorch(isFlashEnabled)

                    // Hardware Sensor Exposure Boost for brilliant crystal-clear documents
                    try {
                        val exposureState = camera.cameraInfo.exposureState
                        if (exposureState.isExposureCompensationSupported) {
                            val range = exposureState.exposureCompensationRange
                            val boostIndex = if (isSuperBright) range.upper else ((range.upper * 2) / 3).coerceIn(range.lower, range.upper)
                            camera.cameraControl.setExposureCompensationIndex(boostIndex)
                            Log.i("QScanCameraScreen", "Camera sensor exposure boosted to index $boostIndex (range ${range.lower}..${range.upper})")
                        }
                    } catch (e: Exception) {
                        Log.w("QScanCameraScreen", "Failed to set exposure compensation", e)
                    }
                } catch (e: Exception) {
                    Log.e("QScanCameraScreen", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    // Pulse animation for auto document finder
    val infiniteTransition = rememberInfiniteTransition(label = "DocFinderPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FinderGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080C)) // Deep Dark Obsidian Black
    ) {
        if (!hasCameraPermission) {
            // Permission Request Fallback Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFEF4444), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color(0xFFFF3B56),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Camera Access Required",
                    fontFamily = GoogleSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Q-Scan requires camera permission to capture and convert physical paper documents into vector PDFs.",
                    fontFamily = GoogleSansTextFamily,
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFFF5252))
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Grant Camera Permission", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent
                ) {
                    Text(
                        text = "Cancel & Go Back",
                        fontFamily = GoogleSansFamily,
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        } else if (!isReviewingBatch) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF06060A))
            ) {
                // 1. Top Dock: Deep Black and Red Gradient Dock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF18080B), Color(0xFF0C0608))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(listOf(Color(0x20EF4444), Color(0x70EF4444))),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = onClose,
                            shape = CircleShape,
                            color = Color(0xD90E0E14),
                            border = BorderStroke(0.8.dp, Color(0x60EF4444)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Brightness Booster Button
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isSuperBright = !isSuperBright
                                },
                                shape = CircleShape,
                                color = if (isSuperBright) Color(0x35EF4444) else Color(0xD90E0E14),
                                border = BorderStroke(0.8.dp, if (isSuperBright) Color(0xFFEF4444) else Color(0x40EF4444)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LightMode,
                                        contentDescription = "Brightness Boost",
                                        tint = if (isSuperBright) Color(0xFFFF9800) else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Flash / Torch Toggle Button
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isFlashEnabled = !isFlashEnabled
                                    cameraControlInstance?.enableTorch(isFlashEnabled)
                                },
                                shape = CircleShape,
                                color = if (isFlashEnabled) Color(0x35EF4444) else Color(0xD90E0E14),
                                border = BorderStroke(0.8.dp, if (isFlashEnabled) Color(0xFFEF4444) else Color(0x40EF4444)),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                        contentDescription = "Torch",
                                        tint = if (isFlashEnabled) Color(0xFFFF3B56) else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Center Viewport (Well Fitted & Contained between Docks)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    val captureScale by androidx.compose.animation.core.animateFloatAsState(if (isCapturing) 0.92f else 1f, androidx.compose.animation.core.tween(150))
                    val captureAlpha by androidx.compose.animation.core.animateFloatAsState(if (isCapturing) 0.7f else 1f, androidx.compose.animation.core.tween(150))

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(captureScale)
                            .alpha(captureAlpha)
                    )

                    // White flash shutter effect
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isCapturing,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(50)),
                        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.White))
                    }

                    // Auto Document Page Finding Overlay
                    if (isAutoDetectActive) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            val boxW = w * 0.84f
                            val boxH = boxW * 1.36f
                            val left = (w - boxW) / 2f
                            val top = (h - boxH) / 2.3f

                            val docPath = Path().apply {
                                moveTo(left, top)
                                lineTo(left + boxW, top)
                                lineTo(left + boxW, top + boxH)
                                lineTo(left, top + boxH)
                                close()
                            }

                            drawPath(
                                path = docPath,
                                color = Color(0xFFEF4444).copy(alpha = pulseAlpha * 0.45f),
                                style = Stroke(
                                    width = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                )
                            )

                            // 4 Glowing Ruby Corner Finder Brackets
                            val bracketLen = 36f
                            val bracketStroke = 3.2f
                            val cornerColor = Color(0xFFFF2A4B).copy(alpha = pulseAlpha)

                            drawLine(cornerColor, Offset(left, top), Offset(left + bracketLen, top), bracketStroke)
                            drawLine(cornerColor, Offset(left, top), Offset(left, top + bracketLen), bracketStroke)

                            drawLine(cornerColor, Offset(left + boxW, top), Offset(left + boxW - bracketLen, top), bracketStroke)
                            drawLine(cornerColor, Offset(left + boxW, top), Offset(left + boxW, top + bracketLen), bracketStroke)

                            drawLine(cornerColor, Offset(left, top + boxH), Offset(left + bracketLen, top + boxH), bracketStroke)
                            drawLine(cornerColor, Offset(left, top + boxH), Offset(left, top + boxH - bracketLen), bracketStroke)

                            drawLine(cornerColor, Offset(left + boxW, top + boxH), Offset(left + boxW - bracketLen, top + boxH), bracketStroke)
                            drawLine(cornerColor, Offset(left + boxW, top + boxH), Offset(left + boxW, top + boxH - bracketLen), bracketStroke)
                        }
                    }
                }

                // 3. Bottom Dock: Deep Black and Red Gradient Dock
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0C0608), Color(0xFF18080B))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(listOf(Color(0x70EF4444), Color(0x20EF4444))),
                            shape = androidx.compose.ui.graphics.RectangleShape
                        )
                        .navigationBarsPadding()
                        .padding(top = 10.dp, bottom = 14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Bottom Page Navigation Strip in Camera Mode
                        if (capturedPages.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                itemsIndexed(capturedPages) { idx, page ->
                                    val bmp = remember(page.originalFile) {
                                        BitmapFactory.decodeFile(page.originalFile.absolutePath)
                                    }
                                    Surface(
                                        onClick = {
                                            reviewInitialIndex = idx
                                            isReviewingBatch = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF161622),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                        modifier = Modifier.size(54.dp, 68.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (bmp != null) {
                                                Image(
                                                    bitmap = bmp.asImageBitmap(),
                                                    contentDescription = "Page ${idx + 1}",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${idx + 1}",
                                                    fontSize = 9.sp,
                                                    fontFamily = GoogleSansFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Shutter Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 28.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Gallery Picker Button
                            Surface(
                                onClick = { galleryLauncher.launch("image/*") },
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xD9100B10),
                                border = BorderStroke(0.8.dp, Color(0x60EF4444)),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoLibrary,
                                        contentDescription = "Import from Gallery",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // Main Capture Button
                            Surface(
                                onClick = {
                                    if (!isCapturing) {
                                        isCapturing = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val photoFile = File(context.cacheDir, "qscan_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                                        imageCapture.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    isCapturing = false
                                                    capturedPages = capturedPages + QScanManager.ScannedPage(photoFile)
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    isCapturing = false
                                                    Log.e("QScanCameraScreen", "Capture failed", exception)
                                                }
                                            }
                                        )
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(1.2.dp, Color(0xFFEF4444)),
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(Color.White, Color(0xFFE2E8F0))
                                                )
                                            )
                                            .border(1.dp, Color(0x40EF4444), CircleShape)
                                    )
                                }
                            }

                            // Done / Review Button
                            Surface(
                                onClick = {
                                    if (capturedPages.isNotEmpty()) {
                                        reviewInitialIndex = 0
                                        isReviewingBatch = true
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (capturedPages.isNotEmpty()) Color(0x28DC2626) else Color(0xD9100B10),
                                border = BorderStroke(0.8.dp, if (capturedPages.isNotEmpty()) Color(0xFFEF4444) else Color(0x30EF4444)),
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (capturedPages.isNotEmpty()) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${capturedPages.size}",
                                                fontSize = 17.sp,
                                                fontFamily = GoogleSansFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFFF3B56)
                                            )
                                            Text(
                                                text = "DONE",
                                                fontSize = 9.sp,
                                                fontFamily = GoogleSansFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Done",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 5. Batch Review, Reorder & Image Enhancement Filters Screen
            QScanBatchReviewSheet(
                pages = capturedPages,
                initialIndex = reviewInitialIndex,
                onUpdatePages = { capturedPages = it },
                onAddMore = { isReviewingBatch = false },
                onRequestSavePdf = { 
                    if (capturedPages.size > 1) {
                        showSaveDialog = true 
                    } else {
                        android.widget.Toast.makeText(context, "A minimum of 2 pages is required to generate a PDF.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                isCompiling = isCompilingPdf
            )

            // Name Entering Popup for Q-Scan
            if (showSaveDialog) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                SaveFileNameDialog(
                    initialName = "QScan_$timestamp",
                    targetExtension = "pdf",
                    sourceOriginalName = null,
                    destinationFolder = "Internal Storage > Qazar > Exports",
                    onDismiss = { showSaveDialog = false },
                    onConfirm = { enteredName ->
                        showSaveDialog = false
                        scope.launch(Dispatchers.IO) {
                            isCompilingPdf = true
                            val targetFile = QazarStorageManager.getExportsDir(context).resolve(
                                if (enteredName.endsWith(".pdf", ignoreCase = true)) enteredName else "$enteredName.pdf"
                            )
                            val result = QScanManager.generatePdf(
                                context = context,
                                pages = capturedPages,
                                documentName = enteredName,
                                outputFile = targetFile
                            )
                            withContext(Dispatchers.Main) {
                                isCompilingPdf = false
                                result.onSuccess { file ->
                                    QazarStorageManager.indexFile(context, file)
                                    completedConversionFile = file
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Universal Conversion / Processing Result Dialog (Open & Share)
    completedConversionFile?.let { file ->
        ConversionResultDialog(
            file = file,
            onDismiss = {
                completedConversionFile = null
                onClose()
            },
            onOpenInViewer = { pdfFile ->
                completedConversionFile = null
                onPdfGenerated(pdfFile)
            }
        )
    }
}

/**
 * Review, Reorder & Filter Screen for captured pages before compiling into PDF
 */
@Composable
private fun QScanBatchReviewSheet(
    pages: List<QScanManager.ScannedPage>,
    initialIndex: Int,
    onUpdatePages: (List<QScanManager.ScannedPage>) -> Unit,
    onAddMore: () -> Unit,
    onRequestSavePdf: () -> Unit,
    isCompiling: Boolean
) {
    var selectedPageIndex by remember { mutableStateOf(initialIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
    var showCropDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(pages.size) {
        if (selectedPageIndex >= pages.size) {
            selectedPageIndex = (pages.size - 1).coerceAtLeast(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Bar: Add Pages (+) button, page count, Crop, Rotate & Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add Pages button with '+' symbol instead of 'x'
            Surface(
                onClick = onAddMore,
                shape = RoundedCornerShape(12.dp),
                color = Color(0x1CDC2626),
                border = BorderStroke(0.8.dp, Color(0x80EF4444))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Pages", tint = Color.White, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Pages", color = Color.White, fontSize = 12.sp, fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = "Page ${selectedPageIndex + 1} of ${pages.size}",
                fontSize = 15.sp,
                fontFamily = GoogleSansFamily,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Crop Page Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCropDialog = true
                    }
                ) {
                    Icon(Icons.Default.Crop, contentDescription = "Crop Page", tint = Color(0xFFEF4444))
                }

                // Rotate Page Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (pages.isNotEmpty() && selectedPageIndex in pages.indices) {
                            val current = pages[selectedPageIndex]
                            val updated = current.copy(rotationDegrees = (current.rotationDegrees + 90) % 360)
                            val list = pages.toMutableList()
                            list[selectedPageIndex] = updated
                            onUpdatePages(list)
                        }
                    }
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = Color(0xFFEF4444))
                }

                // Delete Page Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (pages.isNotEmpty() && selectedPageIndex in pages.indices) {
                            val list = pages.toMutableList()
                            list.removeAt(selectedPageIndex)
                            onUpdatePages(list)
                            if (list.isEmpty()) {
                                onAddMore()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Page", tint = Color(0xFF94A3B8))
                }
            }
        }

        // Reorder Bar: Move Left / Move Right Controls
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        if (selectedPageIndex > 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val list = pages.toMutableList()
                            val item = list.removeAt(selectedPageIndex)
                            list.add(selectedPageIndex - 1, item)
                            onUpdatePages(list)
                            selectedPageIndex -= 1
                        }
                    },
                    enabled = selectedPageIndex > 0,
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedPageIndex > 0) Color(0xFF1E1E2A) else Color(0xFF12121A),
                    border = BorderStroke(0.8.dp, if (selectedPageIndex > 0) Color(0xFF3F3F5A) else Color(0xFF22222E))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move Left",
                            tint = if (selectedPageIndex > 0) Color.White else Color(0xFF475569),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Move Left",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansFamily,
                            color = if (selectedPageIndex > 0) Color.White else Color(0xFF475569)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Surface(
                    onClick = {
                        if (selectedPageIndex < pages.size - 1) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val list = pages.toMutableList()
                            val item = list.removeAt(selectedPageIndex)
                            list.add(selectedPageIndex + 1, item)
                            onUpdatePages(list)
                            selectedPageIndex += 1
                        }
                    },
                    enabled = selectedPageIndex < pages.size - 1,
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedPageIndex < pages.size - 1) Color(0xFF1E1E2A) else Color(0xFF12121A),
                    border = BorderStroke(0.8.dp, if (selectedPageIndex < pages.size - 1) Color(0xFF3F3F5A) else Color(0xFF22222E))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Move Right",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansFamily,
                            color = if (selectedPageIndex < pages.size - 1) Color.White else Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move Right",
                            tint = if (selectedPageIndex < pages.size - 1) Color.White else Color(0xFF475569),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Preview Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pages.isNotEmpty() && selectedPageIndex in pages.indices) {
                val curPage = pages[selectedPageIndex]
                val bmp = remember(curPage.originalFile, curPage.rotationDegrees, curPage.filter) {
                    val raw = BitmapFactory.decodeFile(curPage.originalFile.absolutePath)
                    if (raw != null) {
                        val rotated = if (curPage.rotationDegrees != 0) {
                            val matrix = android.graphics.Matrix().apply { postRotate(curPage.rotationDegrees.toFloat()) }
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                        } else raw
                        val filtered = QScanManager.applyFilter(rotated, curPage.filter)
                        if (rotated != raw) rotated.recycle()
                        filtered
                    } else null
                }

                if (bmp != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF140D12),
                        border = BorderStroke(1.dp, Color(0x60EF4444)),
                        shadowElevation = 16.dp
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Page Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }
                }
            }
        }

        // Filter Strip (Original, Magic Color, B&W Document, Grayscale)
        if (pages.isNotEmpty() && selectedPageIndex in pages.indices) {
            val curPage = pages[selectedPageIndex]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QScanManager.ScanFilter.entries.forEach { filter ->
                    val isSelected = (curPage.filter == filter)
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val list = pages.toMutableList()
                            list[selectedPageIndex] = curPage.copy(filter = filter)
                            onUpdatePages(list)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0x28DC2626) else Color(0xFF140E14),
                        border = BorderStroke(0.8.dp, if (isSelected) Color(0xFFEF4444) else Color(0x30EF4444))
                    ) {
                        Text(
                            text = filter.name.replace("_", " "),
                            fontSize = 11.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFFFF3B56) else Color(0xFFCBD5E1),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Page Carousel Strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(pages) { idx, page ->
                val isSelected = (idx == selectedPageIndex)
                val bmp = remember(page.originalFile) {
                    BitmapFactory.decodeFile(page.originalFile.absolutePath)
                }
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedPageIndex = idx
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF161622),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 0.8.dp,
                        if (isSelected) Color(0xFFEF4444) else Color(0xFF2E2E3E)
                    ),
                    modifier = Modifier.size(46.dp, 60.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Page ${idx + 1}",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) Color(0xFFEF4444) else Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp)
                        ) {
                            Text(
                                text = "${idx + 1}",
                                fontSize = 8.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Save & Compile PDF Button (Triggers Name Entering Popup)
        Surface(
            onClick = onRequestSavePdf,
            enabled = !isCompiling && pages.isNotEmpty(),
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFFDC2626),
            border = BorderStroke(1.dp, Color(0xFFFF5252)),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCompiling) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Save as PDF (${pages.size} Pages)",
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Interactive Crop Dialog
    if (showCropDialog && pages.isNotEmpty() && selectedPageIndex in pages.indices) {
        val targetPage = pages[selectedPageIndex]
        QScanCropDialog(
            page = targetPage,
            onCropSaved = { croppedFile ->
                showCropDialog = false
                val list = pages.toMutableList()
                list[selectedPageIndex] = targetPage.copy(
                    originalFile = croppedFile,
                    rotationDegrees = 0
                )
                onUpdatePages(list)
            },
            onDismiss = { showCropDialog = false }
        )
    }
}
