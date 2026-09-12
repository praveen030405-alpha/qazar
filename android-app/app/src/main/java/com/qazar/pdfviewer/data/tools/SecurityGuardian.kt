package com.qazar.pdfviewer.data.tools

import android.os.Build
import android.os.Debug
import android.util.Log
import java.io.File

object SecurityGuardian {
    private const val TAG = "SecurityGuardian"

    @Volatile
    var isVaultRestricted: Boolean = false
        private set

    fun verifyEnvironment() {
        val isRooted = checkRoot()
        val isDebuggerConnected = Debug.isDebuggerConnected()
        val isEmulator = checkEmulator()

        if (isRooted || isDebuggerConnected || isEmulator) {
            Log.e(TAG, "Security risk detected (Root: $isRooted, Debugger: $isDebuggerConnected, Emulator: $isEmulator). Vault operations restricted.")
            isVaultRestricted = true
        } else {
            isVaultRestricted = false
        }
    }

    private fun checkRoot(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        return false
    }

    private fun checkEmulator(): Boolean {
        return (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.PRODUCT.contains("sdk_google")
                || Build.PRODUCT.contains("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.PRODUCT.contains("sdk_x86")
                || Build.PRODUCT.contains("vbox86p")
                || Build.PRODUCT.contains("emulator")
                || Build.PRODUCT.contains("simulator")
    }
}
