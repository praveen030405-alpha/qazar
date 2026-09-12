package com.qazar.pdfviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfLaunchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Render a small transparent loading modal in Compose
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xD9161622)), // Charcoal Glass
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFEF4444),
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        // Process Intent asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            val intentData = extractPdfUriFromIntent(intent)
            if (intentData != null) {
                val (uri, title) = intentData
                var finalPath = ""

                try {
                    if (uri.scheme == "content") {
                        val (copiedPath, _) = com.qazar.pdfviewer.ui.home.copyUriToCache(this@PdfLaunchActivity, uri)
                        if (copiedPath != null) {
                            finalPath = copiedPath
                        }
                    } else {
                        finalPath = uri.path ?: ""
                    }
                    
                    if (finalPath.isNotEmpty()) {
                        val file = java.io.File(finalPath)
                        if (file.exists()) {
                            val sizeBytes = file.length()
                            com.qazar.pdfviewer.data.PdfLibraryManager.trackExternalDocument(this@PdfLaunchActivity, finalPath, title, sizeBytes)
                            
                            // Route to MainActivity explicitly
                            val mainIntent = Intent(this@PdfLaunchActivity, MainActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                putExtra("EXTRA_PDF_PATH", finalPath)
                                putExtra("EXTRA_PDF_TITLE", title)
                            }
                            startActivity(mainIntent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PdfLaunchActivity", "Failed to cache PDF", e)
                }
            }
            
            withContext(Dispatchers.Main) {
                finish()
            }
        }
    }

    private fun extractPdfUriFromIntent(intent: Intent?): Pair<Uri, String>? {
        if (intent == null) return null
        val action = intent.action
        val targetUri: Uri = (if (action == Intent.ACTION_SEND) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        } else if (action == Intent.ACTION_VIEW) {
            intent.data
        } else {
            null
        }) ?: return null

        var fileName = "Document.pdf"
        try {
            val extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
            val extraTitle = intent.getStringExtra(Intent.EXTRA_TITLE)
            val fallbackTitle = extraSubject ?: extraTitle
            
            if (targetUri.scheme == "content") {
                contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && cursor.moveToFirst()) {
                        val queriedName = cursor.getString(nameIdx)
                        if (queriedName != null) {
                            fileName = if (!queriedName.endsWith(".pdf", ignoreCase = true)) {
                                "$queriedName.pdf"
                            } else {
                                queriedName
                            }
                        }
                    }
                }
            } else if (targetUri.scheme == "file") {
                fileName = targetUri.lastPathSegment ?: fileName
            }
            
            if (fallbackTitle != null && (fileName == "Document.pdf" || fileName.matches(Regex("^[0-9]+(\\.pdf)?$")))) {
                fileName = if (!fallbackTitle.endsWith(".pdf", ignoreCase = true)) "$fallbackTitle.pdf" else fallbackTitle
            }
        } catch (e: Exception) {
            Log.e("PdfLaunchActivity", "Failed to resolve incoming PDF URI", e)
        }
        
        return Pair(targetUri, fileName)
    }
}
