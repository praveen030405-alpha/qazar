package com.qazar.pdfviewer.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Dedicated Qazar System Storage Manager
 * Creates and guarantees the official document ecosystem in mobile internal storage:
 *
 * Internal Storage/
 * â””â”€â”€ Qazar/
 *     â”œâ”€â”€ Documents/  (Active & saved PDF documents)
 *     â”œâ”€â”€ Scans/      (Camera document scans & multi-page captures from Q-Scan)
 *     â”œâ”€â”€ Exports/    (Word, Excel, PPT, merged & compressed PDF exports)
 *     â”œâ”€â”€ Imports/    (PDFs received from WhatsApp, Drive, Chrome, etc.)
 *     â””â”€â”€ Templates/  (Lined, grid, notebook, & resume templates)
 */
object QazarStorageManager {

    private const val TAG = "QazarStorage"
    const val ROOT_FOLDER_NAME = "Qazar"

    const val SUBDIR_DOCUMENTS = "Documents"
    const val SUBDIR_SCANS = "Scans"
    const val SUBDIR_EXPORTS = "Exports"
    const val SUBDIR_IMPORTS = "Imports"
    const val SUBDIR_TEMPLATES = "Templates"

    @Volatile
    var appContext: Context? = null

    private val subdirs = listOf(
        SUBDIR_DOCUMENTS,
        SUBDIR_SCANS,
        SUBDIR_EXPORTS,
        SUBDIR_IMPORTS,
        SUBDIR_TEMPLATES
    )

    /**
     * Verifies if a directory physically allows file creation and writing.
     */
    fun isDirWritable(dir: File?): Boolean {
        if (dir == null) return false
        return try {
            if (!dir.exists()) dir.mkdirs()
            if (!dir.exists()) return false
            val testFile = File(dir, ".qwrite_${System.currentTimeMillis()}")
            testFile.writeText("ok")
            val writable = testFile.exists() && testFile.length() > 0
            testFile.delete()
            writable
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Initializes the full Qazar folder ecosystem.
     * Guarantees root and all 5 standard subdirectories on mobile internal storage.
     */
    fun initStorage(context: Context) {
        appContext = context.applicationContext
        try {
            val createdFiles = mutableListOf<String>()

            // 1. Primary Internal Storage (/sdcard/Qazar)
            val primaryRoot = getPrimaryStorageDir()
            if (primaryRoot != null) {
                createEcosystem(primaryRoot, createdFiles)
            }

            // 2. Secondary Public Documents (/sdcard/Documents/Qazar) - Accessible on all Android versions
            val docsRoot = getPublicDocumentsDir()
            if (docsRoot != null && docsRoot != primaryRoot) {
                createEcosystem(docsRoot, createdFiles)
            }

            // 3. Fallback app storage
            val appStorage = File(context.getExternalFilesDir(null) ?: context.filesDir, ROOT_FOLDER_NAME)
            createEcosystem(appStorage, createdFiles)

            // 4. Generate built-in templates in Templates folder if missing
            val templatesDir = getTemplatesDir(context)
            generateStarterTemplates(templatesDir, createdFiles)

            // 5. Broadcast to Android MediaStore & File Manager
            if (createdFiles.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    createdFiles.toTypedArray(),
                    null
                ) { path, uri ->
                    Log.d(TAG, "Indexed physical file: $path -> $uri")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Qazar storage structure", e)
        }
    }

    private fun createEcosystem(rootDir: File, filesToScan: MutableList<String>) {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        if (rootDir.exists()) {
            filesToScan.add(rootDir.absolutePath)
            for (sub in subdirs) {
                val subDir = File(rootDir, sub)
                if (!subDir.exists()) {
                    subDir.mkdirs()
                }
                if (subDir.exists()) {
                    filesToScan.add(subDir.absolutePath)
                }
            }

            // Vault marker
            val marker = File(rootDir, ".qazar_vault")
            if (!marker.exists()) {
                try {
                    marker.writeText("Qazar High-Performance Quantum PDF Ecosystem\nCreated: ${System.currentTimeMillis()}\n")
                    filesToScan.add(marker.absolutePath)
                } catch (_: Exception) {}
            }
        }
    }

    private fun generateStarterTemplates(templatesDir: File, filesToScan: MutableList<String>) {
        if (!templatesDir.exists()) templatesDir.mkdirs()

        val resumeFile = File(templatesDir, "Professional_Resume_Template.pdf")
        if (!resumeFile.exists()) {
            createSamplePdf(resumeFile, "PROFESSIONAL RESUME TEMPLATE", "Qazar Inbuilt Resume Designer")
            filesToScan.add(resumeFile.absolutePath)
        }

        val notesFile = File(templatesDir, "Lined_Notebook_Template.pdf")
        if (!notesFile.exists()) {
            createSamplePdf(notesFile, "LINED NOTEBOOK TEMPLATE", "Qazar Smart Note Substrate")
            filesToScan.add(notesFile.absolutePath)
        }

        val agendaFile = File(templatesDir, "Meeting_Minutes_Template.pdf")
        if (!agendaFile.exists()) {
            createSamplePdf(agendaFile, "EXECUTIVE MEETING MINUTES", "Qazar Enterprise Collaboration")
            filesToScan.add(agendaFile.absolutePath)
        }
    }

    private fun createSamplePdf(targetFile: File, title: String, subtitle: String) {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Background
            val bgPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(0f, 0f, 595f, 842f, bgPaint)

            // Header Banner
            val headerPaint = Paint().apply { color = Color.parseColor("#140D12") }
            canvas.drawRect(0f, 0f, 595f, 100f, headerPaint)

            val accentPaint = Paint().apply {
                color = Color.parseColor("#DC2626")
                strokeWidth = 3f
            }
            canvas.drawLine(0f, 100f, 595f, 100f, accentPaint)

            // Title
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 20f
                isFakeBoldText = true
                isAntiAlias = true
            }
            canvas.drawText(title, 40f, 55f, titlePaint)

            // Subtitle
            val subPaint = Paint().apply {
                color = Color.parseColor("#EF4444")
                textSize = 12f
                isAntiAlias = true
            }
            canvas.drawText(subtitle, 40f, 78f, subPaint)

            // Template Guidelines Lines
            val linePaint = Paint().apply {
                color = Color.parseColor("#E2E8F0")
                strokeWidth = 1f
            }
            var y = 140f
            while (y < 800f) {
                canvas.drawLine(40f, y, 555f, y, linePaint)
                y += 28f
            }

            doc.finishPage(page)
            FileOutputStream(targetFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating template PDF: ${targetFile.name}", e)
        }
    }

    private fun getPrimaryStorageDir(): File? {
        val root = Environment.getExternalStorageDirectory()
        return if (root != null && Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            File(root, ROOT_FOLDER_NAME)
        } else null
    }

    private fun getPublicDocumentsDir(): File? {
        val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        return if (docs != null) {
            File(docs, ROOT_FOLDER_NAME)
        } else null
    }

    /**
     * Resolves the primary guaranteed writable Qazar root directory.
     */
    fun getQazarRootDir(context: Context): File {
        appContext = context.applicationContext

        // 1. Try public Documents/Qazar (/sdcard/Documents/Qazar)
        val publicDocs = getPublicDocumentsDir()
        if (publicDocs != null && isDirWritable(publicDocs)) {
            return publicDocs
        }

        // 2. Try Primary Storage (/sdcard/Qazar)
        val primary = getPrimaryStorageDir()
        if (primary != null && isDirWritable(primary)) {
            return primary
        }

        // 3. Fallback: App External Files dir (Guaranteed 100% writable on ALL Android versions!)
        val appExt = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.resolve(ROOT_FOLDER_NAME)
            ?: context.getExternalFilesDir(null)?.resolve(ROOT_FOLDER_NAME)
        if (appExt != null && isDirWritable(appExt)) {
            return appExt
        }

        // 4. Ultimate internal fallback
        val internal = File(context.filesDir, ROOT_FOLDER_NAME)
        internal.mkdirs()
        return internal
    }

    fun getDocumentsDir(context: Context): File = File(getQazarRootDir(context), SUBDIR_DOCUMENTS).apply { mkdirs() }
    fun getScansDir(context: Context): File = File(getQazarRootDir(context), SUBDIR_SCANS).apply { mkdirs() }
    fun getExportsDir(context: Context): File = File(getQazarRootDir(context), SUBDIR_EXPORTS).apply { mkdirs() }
    fun getImportsDir(context: Context): File = File(getQazarRootDir(context), SUBDIR_IMPORTS).apply { mkdirs() }
    fun getTemplatesDir(context: Context): File = File(getQazarRootDir(context), SUBDIR_TEMPLATES).apply { mkdirs() }

    fun getDocumentsDir(): File {
        val ctx = appContext
        if (ctx != null) return getDocumentsDir(ctx)
        val candidate = getPublicDocumentsDir()?.resolve(SUBDIR_DOCUMENTS)
        if (candidate != null && isDirWritable(candidate)) return candidate
        return File(System.getProperty("java.io.tmpdir") ?: "/sdcard", SUBDIR_DOCUMENTS).apply { mkdirs() }
    }

    fun getExportsDir(): File {
        val ctx = appContext
        if (ctx != null) return getExportsDir(ctx)
        val candidate = getPublicDocumentsDir()?.resolve(SUBDIR_EXPORTS)
        if (candidate != null && isDirWritable(candidate)) return candidate
        val primary = getPrimaryStorageDir()?.resolve(SUBDIR_EXPORTS)
        if (primary != null && isDirWritable(primary)) return primary
        return File(System.getProperty("java.io.tmpdir") ?: "/sdcard", SUBDIR_EXPORTS).apply { mkdirs() }
    }

    fun getScansDir(): File {
        val ctx = appContext
        if (ctx != null) return getScansDir(ctx)
        val candidate = getPublicDocumentsDir()?.resolve(SUBDIR_SCANS)
        if (candidate != null && isDirWritable(candidate)) return candidate
        return File(System.getProperty("java.io.tmpdir") ?: "/sdcard", SUBDIR_SCANS).apply { mkdirs() }
    }

    fun indexFile(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(file.absolutePath), null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed indexing ${file.name}", e)
        }
    }
}
