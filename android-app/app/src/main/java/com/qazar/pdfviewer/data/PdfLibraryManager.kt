package com.qazar.pdfviewer.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.qazar.pdfviewer.data.db.LocalPdfDatabase
import com.qazar.pdfviewer.data.db.PdfDocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PdfLibraryManager {

    suspend fun importDocument(context: Context, uri: Uri, overrideName: String? = null): PdfDocumentEntity? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            var displayName = overrideName ?: "Imported Document"
            var sizeBytes = 0L

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && overrideName == null) displayName = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1) sizeBytes = cursor.getLong(sizeIdx)
                }
            }

            val mimeType = contentResolver.getType(uri) ?: "application/pdf"
            
            // Create local storage directory
            val libraryDir = File(context.filesDir, "pdf_library").apply { mkdirs() }
            val docId = UUID.randomUUID().toString()
            val sanitizedName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(libraryDir, "${docId}_$sanitizedName")

            // Copy to local library
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val entity = PdfDocumentEntity(
                documentId = docId,
                displayName = displayName,
                uriOrPath = targetFile.absolutePath,
                sizeBytes = sizeBytes.takeIf { it > 0 } ?: targetFile.length(),
                mimeType = mimeType,
                importedTimestamp = System.currentTimeMillis(),
                modifiedTimestamp = System.currentTimeMillis(),
                folder = null,
                isFavorite = false,
                lastOpenedTimestamp = System.currentTimeMillis(),
                pageCount = 0,
                isImportedLocalCopy = true
            )

            LocalPdfDatabase.getDatabase(context).pdfDocumentDao().insertOrUpdate(entity)
            entity
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getAllDocuments(context: Context): kotlinx.coroutines.flow.Flow<List<PdfDocumentEntity>> {
        return LocalPdfDatabase.getDatabase(context).pdfDocumentDao().getAllDocuments()
    }

    suspend fun trackExternalDocument(context: Context, path: String, name: String, sizeBytes: Long) = withContext(Dispatchers.IO) {
        val dao = LocalPdfDatabase.getDatabase(context).pdfDocumentDao()
        val existing = dao.getAllDocumentsSynchronously().find { it.uriOrPath == path }
        if (existing != null) {
            dao.insertOrUpdate(existing.copy(lastOpenedTimestamp = System.currentTimeMillis()))
        } else {
            val entity = PdfDocumentEntity(
                documentId = UUID.randomUUID().toString(),
                displayName = name,
                uriOrPath = path,
                sizeBytes = sizeBytes,
                mimeType = "application/pdf",
                importedTimestamp = System.currentTimeMillis(),
                modifiedTimestamp = System.currentTimeMillis(),
                folder = null,
                isFavorite = false,
                lastOpenedTimestamp = System.currentTimeMillis(),
                pageCount = 0,
                isImportedLocalCopy = false
            )
            dao.insertOrUpdate(entity)
        }
    }
}
