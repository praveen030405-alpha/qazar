package com.qazar.pdfviewer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.qazar.pdfviewer.ui.viewer.ComposeRectAnnotation
import com.qazar.pdfviewer.ui.viewer.ComposeStroke
import org.json.JSONArray
import org.json.JSONObject

/**
 * Isolated persistent annotation storage per document.
 * Prevents cross-document annotation leakage completely.
 */
object AnnotationStorageManager {
    private const val PREFS_NAME = "meridian_annotations_store"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getDocKey(docPathOrTitle: String): String {
        return "annots_" + docPathOrTitle.hashCode().toString()
    }

    fun getDocAnnotations(
        context: Context,
        docPathOrTitle: String
    ): Pair<Map<Int, List<ComposeStroke>>, Map<Int, List<ComposeRectAnnotation>>> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(getDocKey(docPathOrTitle), null) ?: return Pair(emptyMap(), emptyMap())

        return try {
            val root = JSONObject(jsonStr)
            val strokesMap = mutableMapOf<Int, MutableList<ComposeStroke>>()
            val rectsMap = mutableMapOf<Int, MutableList<ComposeRectAnnotation>>()

            val strokesArray = root.optJSONArray("strokes") ?: JSONArray()
            for (i in 0 until strokesArray.length()) {
                val obj = strokesArray.getJSONObject(i)
                val pageIdx = obj.getInt("page")
                val colorInt = obj.getInt("color")
                val width = obj.getDouble("width").toFloat()
                val isHighlighter = obj.optBoolean("isHighlighter", false)
                val ptsArray = obj.getJSONArray("pts")
                val pts = mutableListOf<Offset>()
                for (j in 0 until ptsArray.length()) {
                    val p = ptsArray.getJSONObject(j)
                    pts.add(Offset(p.getDouble("x").toFloat(), p.getDouble("y").toFloat()))
                }
                val list = strokesMap.getOrPut(pageIdx) { mutableListOf() }
                list.add(ComposeStroke(pts, Color(colorInt), width, isHighlighter))
            }

            val rectsArray = root.optJSONArray("rectangles") ?: JSONArray()
            for (i in 0 until rectsArray.length()) {
                val obj = rectsArray.getJSONObject(i)
                val pageIdx = obj.getInt("page")
                val colorInt = obj.getInt("color")
                val width = obj.getDouble("width").toFloat()
                val r = obj.getJSONObject("rect")
                val x = r.optDouble("x", r.optDouble("l", 0.0)).toFloat()
                val y = r.optDouble("y", r.optDouble("t", 0.0)).toFloat()
                val w = r.optDouble("w", 0.0).toFloat()
                val h = r.optDouble("h", 0.0).toFloat()
                val list = rectsMap.getOrPut(pageIdx) { mutableListOf() }
                list.add(ComposeRectAnnotation(Offset(x, y), Size(w, h), Color(colorInt), width))
            }

            Pair(strokesMap, rectsMap)
        } catch (e: Exception) {
            Pair(emptyMap(), emptyMap())
        }
    }

    fun saveDocAnnotations(
        context: Context,
        docPathOrTitle: String,
        strokesMap: Map<Int, List<ComposeStroke>>,
        rectsMap: Map<Int, List<ComposeRectAnnotation>>
    ) {
        val root = JSONObject()
        val strokesArray = JSONArray()
        for ((pageIdx, list) in strokesMap) {
            for (s in list) {
                val sObj = JSONObject()
                sObj.put("page", pageIdx)
                sObj.put("color", s.color.toArgb())
                sObj.put("width", s.strokeWidth.toDouble())
                sObj.put("isHighlighter", s.isHighlighter)
                val ptsArray = JSONArray()
                for (pt in s.points) {
                    val ptObj = JSONObject()
                    ptObj.put("x", pt.x.toDouble())
                    ptObj.put("y", pt.y.toDouble())
                    ptsArray.put(ptObj)
                }
                sObj.put("pts", ptsArray)
                strokesArray.put(sObj)
            }
        }
        root.put("strokes", strokesArray)

        val rectsArray = JSONArray()
        for ((pageIdx, list) in rectsMap) {
            for (r in list) {
                val rObj = JSONObject()
                rObj.put("page", pageIdx)
                rObj.put("color", r.color.toArgb())
                rObj.put("width", r.strokeWidth.toDouble())
                val rectJson = JSONObject()
                rectJson.put("x", r.topLeft.x.toDouble())
                rectJson.put("y", r.topLeft.y.toDouble())
                rectJson.put("w", r.size.width.toDouble())
                rectJson.put("h", r.size.height.toDouble())
                rObj.put("rect", rectJson)
                rectsArray.put(rObj)
            }
        }
        root.put("rectangles", rectsArray)

        getPrefs(context).edit().putString(getDocKey(docPathOrTitle), root.toString()).apply()
    }

    fun getVirtualPageCount(context: Context, docPathOrTitle: String): Int {
        val prefs = getPrefs(context)
        return prefs.getInt(getDocKey(docPathOrTitle) + "_vpc", 0)
    }

    fun saveVirtualPageCount(context: Context, docPathOrTitle: String, count: Int) {
        val prefs = getPrefs(context)
        prefs.edit().putInt(getDocKey(docPathOrTitle) + "_vpc", count).apply()
    }
}
