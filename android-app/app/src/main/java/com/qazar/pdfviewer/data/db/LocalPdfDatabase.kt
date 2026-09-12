package com.qazar.pdfviewer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfDocumentEntity::class], version = 1, exportSchema = false)
abstract class LocalPdfDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: LocalPdfDatabase? = null

        fun getDatabase(context: Context): LocalPdfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalPdfDatabase::class.java,
                    "meridian_pdf_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
