package com.qazar.pdfviewer.engine

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.util.concurrent.Executors

/**
 * Meridian Flagship Thermal Governor.
 * Integrates with Android 10+ PowerManager Thermal API.
 * Dynamically throttles rendering DPI and quality scaling if the device heats up,
 * preventing CPU thermal runaway, frame drops, and battery degradation.
 */
object ThermalGovernor {
    private const val TAG = "ThermalGovernor"

    private var isStarted = false
    private var powerManager: PowerManager? = null
    private var thermalListener: Any? = null
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    var currentThermalStatus: Int = 0 // 0 = THERMAL_STATUS_NONE
        private set

    fun start(context: Context) {
        if (isStarted) return
        isStarted = true

        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager = pm

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pm != null) {
                currentThermalStatus = pm.currentThermalStatus
                applyStatus(currentThermalStatus)

                val listener = PowerManager.OnThermalStatusChangedListener { status ->
                    currentThermalStatus = status
                    applyStatus(status)
                }
                thermalListener = listener
                pm.addThermalStatusListener(executor, listener)
                Log.i(TAG, "ThermalGovernor started with initial status: $currentThermalStatus")
            } else {
                Log.i(TAG, "Thermal API not available on SDK ${Build.VERSION.SDK_INT}, using standard profile")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register thermal status listener: ${e.message}")
        }
    }

    private fun applyStatus(status: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val factor = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> 1.0f
                PowerManager.THERMAL_STATUS_LIGHT -> 1.0f
                PowerManager.THERMAL_STATUS_MODERATE -> 0.88f // 12% reduction in render resolution
                PowerManager.THERMAL_STATUS_SEVERE -> 0.75f   // 25% reduction to aggressively cool down
                PowerManager.THERMAL_STATUS_CRITICAL -> 0.65f // Low-power survival mode
                PowerManager.THERMAL_STATUS_EMERGENCY -> 0.60f
                PowerManager.THERMAL_STATUS_SHUTDOWN -> 0.50f
                else -> 1.0f
            }
            Log.i(TAG, "Thermal status changed to $status -> throttle factor: $factor")
            DisplayProfileManager.applyThermalThrottle(factor)
        }
    }

    fun stop() {
        if (!isStarted) return
        isStarted = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = powerManager
            val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener
            if (pm != null && listener != null) {
                try {
                    pm.removeThermalStatusListener(listener)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error unregistering thermal listener: ${e.message}")
                }
            }
        }
        thermalListener = null
        powerManager = null
    }
}
