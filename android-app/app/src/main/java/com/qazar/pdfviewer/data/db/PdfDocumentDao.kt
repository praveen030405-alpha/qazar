package com.qazar.pdfviewer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllDocuments(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavorites(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedTimestamp DESC")
    fun getAllDocumentsSynchronously(): List<PdfDocumentEntity>

    @Query("SELECT * FROM pdf_documents WHERE documentId = :id")
    fun getDocumentById(id: String): PdfDocumentEntity?
    
    @Query("SELECT * FROM pdf_documents WHERE uriOrPath = :path")
    fun getDocumentByPath(path: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(document: PdfDocumentEntity)

    @Delete
    fun delete(document: PdfDocumentEntity)
    
    @Query("DELETE FROM pdf_documents WHERE documentId = :id")
    fun deleteById(id: String)
}
