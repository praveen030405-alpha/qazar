package com.qazar.pdfviewer.bridge

import android.graphics.RectF
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data structures and orchestrator for Phase 5:
 * Forms, Signatures & True Cryptographic Redaction
 */

data class RedactionArea(
    val pageIndex: Int,
    val bounds: RectF,
    val reason: String = "CONFIDENTIAL",
    val classification: String = "PII" // PII, FINANCIAL, PRIVILEGED, SECURITY
)

data class SignatureInfo(
    val signerName: String,
    val reason: String,
    val pageIndex: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val timestamp: String = "",
    val isValid: Boolean = true,
    val sha256Digest: String = ""
)

data class FormFieldInfo(
    val pageIndex: Int,
    val name: String,
    val fieldType: String, // TEXT, CHECKBOX, SIGNATURE
    var value: String,
    val bounds: RectF
)

data class AuditLogEntry(
    val index: Long,
    val timestamp: String,
    val action: String,
    val details: String,
    val prevHash: String,
    val entryHash: String
)

object RedactionAuditManager {
    private const val TAG = "RedactionAuditManager"

    fun applyRedaction(area: RedactionArea): Boolean {
        return try {
            val resStr = MeridianNativeBridge.applyRedaction(
                pageIndex = area.pageIndex,
                left = area.bounds.left,
                top = area.bounds.top,
                right = area.bounds.right,
                bottom = area.bounds.bottom,
                reason = "[${area.classification}] ${area.reason}"
            )
            Log.d(TAG, "Redaction applied: $resStr")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply redaction", e)
            false
        }
    }

    fun signDocument(
        signerName: String,
        reason: String,
        pageIndex: Int,
        bounds: RectF
    ): SignatureInfo? {
        return try {
            val jsonStr = MeridianNativeBridge.signDocument(
                signerName = signerName,
                reason = reason,
                pageIndex = pageIndex,
                x = bounds.left,
                y = bounds.top,
                w = bounds.width(),
                h = bounds.height()
            )
            val json = JSONObject(jsonStr)
            SignatureInfo(
                signerName = json.optString("signer_name", signerName),
                reason = json.optString("reason", reason),
                pageIndex = json.optInt("page_index", pageIndex),
                x = bounds.left,
                y = bounds.top,
                width = bounds.width(),
                height = bounds.height(),
                timestamp = json.optString("timestamp", ""),
                isValid = json.optBoolean("is_valid", true),
                sha256Digest = json.optString("sha256_digest", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign document", e)
            null
        }
    }

    fun getAuditLog(): List<AuditLogEntry> {
        val list = mutableListOf<AuditLogEntry>()
        try {
            val jsonStr = MeridianNativeBridge.getAuditLog()
            if (jsonStr.isBlank()) return list
            val json = JSONObject(jsonStr)
            val entries = json.optJSONArray("entries") ?: return list
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                list.add(
                    AuditLogEntry(
                        index = e.optLong("index", i.toLong()),
                        timestamp = e.optString("timestamp", ""),
                        action = e.optString("action", ""),
                        details = e.optString("details", ""),
                        prevHash = e.optString("prev_hash", ""),
                        entryHash = e.optString("entry_hash", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve audit log", e)
        }
        return list
    }

    fun getFormFields(pageIndex: Int): List<FormFieldInfo> {
        val list = mutableListOf<FormFieldInfo>()
        try {
            val jsonStr = MeridianNativeBridge.getFormFields(pageIndex)
            if (jsonStr.isBlank()) return list
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                val boundsObj = f.optJSONObject("bounds")
                val bounds = if (boundsObj != null) {
                    RectF(
                        boundsObj.optDouble("left", 0.0).toFloat(),
                        boundsObj.optDouble("top", 0.0).toFloat(),
                        boundsObj.optDouble("right", 0.0).toFloat(),
                        boundsObj.optDouble("bottom", 0.0).toFloat()
                    )
                } else {
                    RectF(0f, 0f, 100f, 30f)
                }
                list.add(
                    FormFieldInfo(
                        pageIndex = pageIndex,
                        name = f.optString("name", "Field_$i"),
                        fieldType = f.optString("type", "TEXT"),
                        value = f.optString("value", ""),
                        bounds = bounds
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse form fields", e)
        }
        return list
    }

    fun setFormFieldValue(pageIndex: Int, name: String, value: String): Boolean {
        return MeridianNativeBridge.setFormFieldValue(pageIndex, name, value)
    }
}
