package com.qazar.pdfviewer.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

object IntentRouter {
    private const val TAG = "IntentRouter"

    fun extractPdfUriFromIntent(context: Context, intent: Intent?): Pair<Uri, String>? {
        if (intent == null) return null

        val explicitPath = intent.getStringExtra("EXTRA_PDF_PATH")
        val explicitTitle = intent.getStringExtra("EXTRA_PDF_TITLE")
        if (explicitPath != null && explicitTitle != null) {
            return Pair(Uri.fromFile(File(explicitPath)), explicitTitle)
        }

        val action = intent.action
        val targetUri: Uri = (if (action == Intent.ACTION_SEND) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                context.contentResolver.query(targetUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
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
            Log.e(TAG, "Failed to resolve incoming PDF URI", e)
        }
        
        return Pair(targetUri, fileName)
    }
}
