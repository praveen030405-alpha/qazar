package com.qazar.pdfviewer

import android.app.Application
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class QazarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        com.qazar.pdfviewer.data.QazarStorageManager.initStorage(this)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                Log.e("QazarApp", "Uncaught exception caught by global handler", exception)
                
                // Write crash to local file instead of taking down the OS with a hard ANR
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val crashFile = File(cacheDir, "crash_$timestamp.txt")
                
                FileWriter(crashFile).use { writer ->
                    writer.write("Timestamp: $timestamp\n")
                    writer.write("Thread: ${thread.name}\n")
                    writer.write("Exception: ${exception.message}\n")
                    writer.write("Stacktrace:\n")
                    exception.printStackTrace(java.io.PrintWriter(writer))
                }
            } catch (e: Exception) {
                // Ignore, we're already crashing
            } finally {
                // Exit cleanly
                exitProcess(1)
            }
        }
    }
}
