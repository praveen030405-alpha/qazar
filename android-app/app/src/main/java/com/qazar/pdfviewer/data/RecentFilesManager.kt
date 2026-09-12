package com.qazar.pdfviewer.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class RecentDocItem(
    val path: String,
    val title: String,
    val sizeBytes: Long,
    val timestamp: Long
)

object RecentFilesManager {
    private const val PREFS_NAME = "meridian_recents"
    private const val KEY_RECENTS = "recent_documents_list"
    private const val MAX_RECENTS = 15

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getRecents(context: Context): List<RecentDocItem> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<RecentDocItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RecentDocItem(
                        path = obj.getString("path"),
                        title = obj.getString("title"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecent(context: Context, path: String, title: String, sizeBytes: Long) {
        val current = getRecents(context).filter { it.path != path }.toMutableList()
        current.add(
            0,
            RecentDocItem(
                path = path,
                title = title,
                sizeBytes = sizeBytes,
                timestamp = System.currentTimeMillis()
            )
        )
        val limited = current.take(MAX_RECENTS)
        saveRecents(context, limited)
    }

    fun removeRecent(context: Context, path: String) {
        val current = getRecents(context).filter { it.path != path }
        saveRecents(context, current)
    }

    fun renameRecent(context: Context, path: String, newTitle: String) {
        val current = getRecents(context).map {
            if (it.path == path) it.copy(title = newTitle) else it
        }
        saveRecents(context, current)
    }

    private fun saveRecents(context: Context, items: List<RecentDocItem>) {
        val arr = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("path", item.path)
            obj.put("title", item.title)
            obj.put("sizeBytes", item.sizeBytes)
            obj.put("timestamp", item.timestamp)
            arr.put(obj)
        }
        getPrefs(context).edit().putString(KEY_RECENTS, arr.toString()).apply()
    }
}
