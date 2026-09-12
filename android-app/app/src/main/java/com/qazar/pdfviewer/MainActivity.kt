package com.qazar.pdfviewer

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.qazar.pdfviewer.bridge.MeridianNativeBridge
import com.qazar.pdfviewer.data.IntentRouter
import com.qazar.pdfviewer.theme.CanvasWorkspaceBg
import com.qazar.pdfviewer.theme.MeridianTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    private var pendingIntentPdfUri = mutableStateOf<Pair<Uri, String>?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IntentRouter.extractPdfUriFromIntent(this, intent)?.let {
            pendingIntentPdfUri.value = it
        }
    }

    override fun onResume() {
        super.onResume()
        com.qazar.pdfviewer.benchmark.PerfettoTraceBridge.recordWarmResume()
    }

    override fun onPause() {
        super.onPause()
        com.qazar.pdfviewer.benchmark.PerfettoTraceBridge.exportBenchmarkReport(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        com.qazar.pdfviewer.benchmark.PerfettoTraceBridge.recordProcessStart()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF0C0C12.toInt()))
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Enable maximum native display refresh rate (120Hz AMOLED) for butter-smooth fluidity
        try {
            val lp = window.attributes
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                lp.preferredRefreshRate = 120.0f
            }
            window.attributes = lp
        } catch (_: Throwable) {}

        // Security check for rooted/debugged environment
        com.qazar.pdfviewer.data.tools.SecurityGuardian.verifyEnvironment()

        IntentRouter.extractPdfUriFromIntent(this, intent)?.let {
            pendingIntentPdfUri.value = it
        }

        // Initialize Qazar Storage System & 5 standard subdirectories
        com.qazar.pdfviewer.data.QazarStorageManager.initStorage(this)

        // Request Camera & Storage permissions upon installation/launch
        val permissionsToRequest = mutableListOf<String>()
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.CAMERA)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 1001)
        }

        // Pipeline Pre-Warming in background (Zero disk I/O or UI thread blocking)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.qazar.pdfviewer.engine.DisplayProfileManager.initialize(this@MainActivity)
                com.qazar.pdfviewer.engine.MemoryBudgetManager.initialize(this@MainActivity)
                com.qazar.pdfviewer.engine.PageRenderScheduler.start()
                MeridianNativeBridge.initialize(this@MainActivity)
                Log.i("MainActivity", "Core rendering pipeline and memory governors pre-warmed.")
            } catch (e: Exception) {
                Log.w("MainActivity", "Core initialization deferred: ${e.message}")
            }
        }

        setContent {
            MeridianTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CanvasWorkspaceBg
                ) {
                    val insetsController = remember { WindowInsetsControllerCompat(window, window.decorView) }
                    var isSplashVisible by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(pendingIntentPdfUri.value == null) }

                    AppNavigation(
                        insetsController = insetsController,
                        isSplashVisible = isSplashVisible,
                        onSplashComplete = { isSplashVisible = false },
                        pendingIntentPdfUri = pendingIntentPdfUri.value,
                        onPendingIntentHandled = { pendingIntentPdfUri.value = null },
                        finishAndRemoveTask = { finishAndRemoveTask() }
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            // Re-initialize storage now that user has potentially granted permissions
            com.qazar.pdfviewer.data.QazarStorageManager.initStorage(this)
        }
    }
}
