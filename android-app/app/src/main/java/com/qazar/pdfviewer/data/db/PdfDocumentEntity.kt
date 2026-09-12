package com.qazar.pdfviewer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey val documentId: String,
    val displayName: String,
    val uriOrPath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val importedTimestamp: Long,
    val modifiedTimestamp: Long,
    val folder: String?,
    val isFavorite: Boolean,
    val lastOpenedTimestamp: Long,
    val pageCount: Int,
    val isImportedLocalCopy: Boolean
)
