package com.qazar.pdfviewer.data

import android.content.Context
import android.content.SharedPreferences

object BookmarkManager {
    private const val PREFS_NAME = "meridian_bookmarks"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getDocKey(docPathOrTitle: String): String {
        return "bookmarks_" + docPathOrTitle.hashCode().toString()
    }

    fun getBookmarks(context: Context, docPathOrTitle: String): Set<Int> {
        val prefs = getPrefs(context)
        val key = getDocKey(docPathOrTitle)
        val stringSet = prefs.getStringSet(key, emptySet()) ?: emptySet()
        return stringSet.mapNotNull { it.toIntOrNull() }.toSortedSet()
    }

    fun isBookmarked(context: Context, docPathOrTitle: String, pageIndex: Int): Boolean {
        return getBookmarks(context, docPathOrTitle).contains(pageIndex)
    }

    fun toggleBookmark(context: Context, docPathOrTitle: String, pageIndex: Int): Boolean {
        val current = getBookmarks(context, docPathOrTitle).toMutableSet()
        val newState: Boolean
        if (current.contains(pageIndex)) {
            current.remove(pageIndex)
            newState = false
        } else {
            current.add(pageIndex)
            newState = true
        }
        val prefs = getPrefs(context)
        val key = getDocKey(docPathOrTitle)
        val stringSet = current.map { it.toString() }.toSet()
        prefs.edit().putStringSet(key, stringSet).apply()
        return newState
    }
}
