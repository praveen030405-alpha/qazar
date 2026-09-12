package com.qazar.pdfviewer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.activity.compose.BackHandler
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qazar.pdfviewer.data.PdfLibraryManager
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.GoogleSansTextFamily
import com.qazar.pdfviewer.theme.TextPrimary
import com.qazar.pdfviewer.theme.TextSecondary
import com.qazar.pdfviewer.ui.home.HomeScreen
import com.qazar.pdfviewer.ui.splash.DynamicSplashScreen
import com.qazar.pdfviewer.ui.viewer.PdfViewerScreen
import com.qazar.pdfviewer.ui.viewer.PdfViewerViewModel
import com.qazar.pdfviewer.ui.viewer.PdfViewerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AppNavigation(
    insetsController: WindowInsetsControllerCompat,
    isSplashVisible: Boolean,
    onSplashComplete: () -> Unit,
    pendingIntentPdfUri: Pair<Uri, String>?,
    onPendingIntentHandled: () -> Unit,
    finishAndRemoveTask: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Manual DI for ViewModel to avoid adding heavy Hilt dependency in M1
    val factory = remember { PdfViewerViewModelFactory(context.applicationContext as android.app.Application) }
    val pdfViewModel: PdfViewerViewModel = viewModel(factory = factory)
    
    val viewerState by pdfViewModel.state.collectAsState()
    
    var isOpenedFromExternal by remember { mutableStateOf(false) }
    var isIntentLoading by remember { mutableStateOf(false) }
    var activeToolFlow by remember { mutableStateOf<ActiveToolFlow?>(null) }
    
    val prefs = remember { context.getSharedPreferences("qazar_prefs", Context.MODE_PRIVATE) }

    // Handle Intent when it arrives
    androidx.compose.runtime.LaunchedEffect(pendingIntentPdfUri) {
        if (pendingIntentPdfUri != null) {
            isIntentLoading = true
            isOpenedFromExternal = true
            Toast.makeText(context, "Opening PDF...", Toast.LENGTH_SHORT).show()
            try {
                withContext(Dispatchers.IO) {
                    val uri = pendingIntentPdfUri.first
                    val title = pendingIntentPdfUri.second
                    
                    if (uri.scheme == "content" || uri.scheme == "file") {
                        pdfViewModel.loadDocument(
                            uri = uri,
                            path = if (uri.scheme == "file") uri.path else null,
                            title = title,
                            pfd = null,
                            isImported = false
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isIntentLoading = false
                onPendingIntentHandled()
            }
        }
    }

    val handleBack = {
        if (isOpenedFromExternal) {
            finishAndRemoveTask()
        } else {
            pdfViewModel.loadDocument(null, null, null, null, false)
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    androidx.activity.compose.BackHandler(enabled = activeToolFlow != null) {
        activeToolFlow = null
    }

    androidx.activity.compose.BackHandler(enabled = activeToolFlow == null && (viewerState.documentPath != null || viewerState.documentUri != null)) {
        handleBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AnimatedContent(
            targetState = isSplashVisible,
            transitionSpec = {
                (fadeIn(animationSpec = tween(340, easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))) +
                 scaleIn(initialScale = 0.98f, animationSpec = tween(340, easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f))))
                    .togetherWith(fadeOut(animationSpec = tween(260, easing = androidx.compose.animation.core.FastOutLinearInEasing)))
            },
            label = "SplashTransition"
        ) { splashActive ->
            if (splashActive) {
                Box(modifier = Modifier.fillMaxSize()) {
                    DynamicSplashScreen(onAnimationComplete = onSplashComplete)
                }
            } else {
                AnimatedContent(
                    targetState = viewerState.documentPath != null || viewerState.documentUri != null,
                    transitionSpec = {
                        if (targetState) {
                            // Enterprise PDF Opening: Satisfying, seamless Slide-In from Bottom of screen
                            (slideInVertically(
                                initialOffsetY = { fullHeight -> fullHeight },
                                animationSpec = tween(
                                    durationMillis = 360,
                                    easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
                                )
                            ) + scaleIn(
                                initialScale = 0.96f,
                                animationSpec = tween(
                                    durationMillis = 360,
                                    easing = androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
                                )
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                            )) togetherWith (
                                fadeOut(animationSpec = tween(durationMillis = 200)) +
                                scaleOut(
                                    targetScale = 0.96f,
                                    animationSpec = tween(durationMillis = 200)
                                )
                            )
                        } else {
                            // Enterprise PDF Closing: Smooth, visible slide-down without snapping
                            (fadeIn(
                                animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
                            ) + scaleIn(
                                initialScale = 0.98f,
                                animationSpec = tween(
                                    durationMillis = 320,
                                    easing = FastOutSlowInEasing
                                )
                            )) togetherWith (
                                slideOutVertically(
                                    targetOffsetY = { fullHeight -> fullHeight },
                                    animationSpec = tween(
                                        durationMillis = 420,
                                        easing = FastOutSlowInEasing
                                    )
                                ) + fadeOut(
                                    animationSpec = tween(durationMillis = 320, easing = LinearEasing)
                                )
                            )
                        }
                    },
                    label = "ScreenSharedAxisTransition"
                ) { isViewerActive ->
                    if (!isViewerActive) {
                        DisposableEffect(Unit) {
                            insetsController.show(WindowInsetsCompat.Type.statusBars())
                            insetsController.isAppearanceLightStatusBars = false
                            onDispose { }
                        }
                        
                        if (isIntentLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.material3.Surface(
                                    modifier = Modifier.size(140.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF1E1E1E).copy(alpha = 0.9f),
                                    tonalElevation = 8.dp,
                                    shadowElevation = 16.dp
                                ) {
                                    androidx.compose.foundation.layout.Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                    ) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            color = Color.White,
                                            strokeWidth = 3.dp
                                        )
                                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Opening PDF...",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontFamily = GoogleSansTextFamily
                                        )
                                    }
                                }
                            }
                        } else {
                            BackHandler(enabled = activeToolFlow != null) {
                                activeToolFlow = null
                            }

                            AnimatedContent(
                                targetState = activeToolFlow,
                                transitionSpec = {
                                    if (targetState != null && initialState == null) {
                                        // Entering tool flow (e.g. ToolsHubScreen): slide in from right to left
                                        (slideInHorizontally(
                                            initialOffsetX = { fullWidth -> fullWidth },
                                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                                        ) + fadeIn(tween(320)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> -fullWidth / 3 },
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                ) + fadeOut(tween(200))
                                            )
                                    } else if (targetState == null && initialState != null) {
                                        // Exiting back to HomeScreen: slide out to right
                                        (slideInHorizontally(
                                            initialOffsetX = { fullWidth -> -fullWidth / 3 },
                                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                                        ) + fadeIn(tween(320)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> fullWidth },
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                ) + fadeOut(tween(200))
                                            )
                                    } else if (targetState != null && initialState != null) {
                                        // Navigating from ToolsHub to another tool: slide in from right
                                        (slideInHorizontally(
                                            initialOffsetX = { fullWidth -> fullWidth },
                                            animationSpec = tween(320, easing = FastOutSlowInEasing)
                                        ) + fadeIn(tween(320)))
                                            .togetherWith(
                                                slideOutHorizontally(
                                                    targetOffsetX = { fullWidth -> -fullWidth / 3 },
                                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                                ) + fadeOut(tween(200))
                                            )
                                    } else {
                                        fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                                    }
                                },
                                label = "ToolFlowTransition"
                            ) { currentFlow ->
                                when (currentFlow) {
                                    ActiveToolFlow.Q_SCAN -> {
                                        com.qazar.pdfviewer.ui.tools.QScanCameraScreen(
                                            onClose = { activeToolFlow = null },
                                            onPdfGenerated = { file ->
                                                activeToolFlow = null
                                                isOpenedFromExternal = false
                                                pdfViewModel.loadDocument(null, file.absolutePath, file.name, null, true)
                                            }
                                        )
                                    }
                                    ActiveToolFlow.RESUME_BUILDER -> {
                                        com.qazar.pdfviewer.ui.tools.ResumeBuilderScreen(
                                            onBack = { activeToolFlow = null },
                                            onPdfGenerated = { file ->
                                                activeToolFlow = null
                                                isOpenedFromExternal = false
                                                pdfViewModel.loadDocument(null, file.absolutePath, file.name, null, true)
                                            }
                                        )
                                    }
                                    ActiveToolFlow.TOOLS_HUB -> {
                                        com.qazar.pdfviewer.ui.tools.ToolsHubScreen(
                                            onNavigateBack = { activeToolFlow = null },
                                            onLaunchQScan = { activeToolFlow = ActiveToolFlow.Q_SCAN },
                                            onLaunchResumeMaker = { activeToolFlow = ActiveToolFlow.RESUME_BUILDER },
                                            onOpenGeneratedPdf = { file ->
                                                activeToolFlow = null
                                                isOpenedFromExternal = false
                                                pdfViewModel.loadDocument(null, file.absolutePath, file.name, null, true)
                                            }
                                        )
                                    }
                                    null -> {
                                        HomeScreen(
                                            onOpenPdf = { path, title ->
                                                isOpenedFromExternal = false
                                                pdfViewModel.loadDocument(null, path, title, null, true)
                                            },
                                            onNavigateToQScan = { activeToolFlow = ActiveToolFlow.Q_SCAN },
                                            onNavigateToResumeMaker = { activeToolFlow = ActiveToolFlow.RESUME_BUILDER },
                                            onNavigateToToolsHub = { activeToolFlow = ActiveToolFlow.TOOLS_HUB }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        PdfViewerScreen(
                            viewModel = pdfViewModel,
                            documentPath = viewerState.documentPath,
                            documentUri = viewerState.documentUri,
                            documentTitle = viewerState.documentTitle,
                            documentPfd = viewerState.documentPfd,
                            assetFileName = viewerState.documentTitle,
                            isImportedLocalCopy = viewerState.isImportedLocalCopy,
                            onSaveToLibrary = {
                                if (isOpenedFromExternal) {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        try {
                                            if (viewerState.documentUri != null) {
                                                PdfLibraryManager.importDocument(context, viewerState.documentUri!!, viewerState.documentTitle)
                                            } else if (viewerState.documentPath != null) {
                                                val sourceFile = File(viewerState.documentPath!!)
                                                if (sourceFile.exists()) {
                                                    PdfLibraryManager.importDocument(context, Uri.fromFile(sourceFile), viewerState.documentTitle)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // ignore
                                        }
                                    }
                                }
                            },
                            onBack = handleBack,
                            onSetSystemBarsVisible = { visible ->
                                if (visible) {
                                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                                } else {
                                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                                }
                            },
                            onSetLightStatusBars = { isLight ->
                                insetsController.isAppearanceLightStatusBars = isLight
                                insetsController.isAppearanceLightNavigationBars = isLight
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class ActiveToolFlow {
    Q_SCAN,
    RESUME_BUILDER,
    TOOLS_HUB
}

