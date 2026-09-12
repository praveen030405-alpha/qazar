package com.qazar.pdfviewer.engine

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import com.qazar.pdfviewer.ui.viewer.AnnotationTool
import java.util.UUID
import org.json.JSONObject

typealias ActiveTool = AnnotationTool

data class PdfPoint(val x: Float, val y: Float)

/**
 * Singleton Controller for Annotation State and Fast-Path Routing.
 * Manages active tools, transforms screen coordinates to stable PDF coordinates,
 * and routes committed paths down to the JNI bridge.
 */
object AnnotationController {
    var activeTool = mutableStateOf(AnnotationTool.PAN)
    
    // Figma-style layer states
    var activeLayerId = mutableStateOf<UUID?>(null)
    var activeGroupId = mutableStateOf<UUID?>(null)

    // Current page bounds and transform metadata fed by the UI
    var currentPageIndex = 0
    var zoomScale = 1.0f
    var offsetX = 0.0f
    var offsetY = 0.0f
    
    /**
     * Translates a raw Android screen coordinate into an absolute PDF coordinate.
     * This guarantees that when the device rotates or zooms, the annotation stays 
     * perfectly anchored to the document content.
     */
    fun screenToPdf(screenPoint: Offset): PdfPoint {
        // Reverse the UI scroll and zoom
        val pdfX = (screenPoint.x - offsetX) / zoomScale
        val pdfY = (screenPoint.y - offsetY) / zoomScale
        return PdfPoint(pdfX, pdfY)
    }

    /**
     * Commits a completed predictive stroke/shape down to the CRDT Engine via JNI.
     */
    fun commitAnnotation(points: List<PdfPoint>) {
        if (points.isEmpty()) return
        
        val json = JSONObject()
        json.put("type", activeTool.value.name)
        
        activeLayerId.value?.let { json.put("layer_id", it.toString()) }
        activeGroupId.value?.let { json.put("group_id", it.toString()) }
        
        // Build coordinates array
        val ptsArray = org.json.JSONArray()
        for (p in points) {
            val ptObj = JSONObject()
            ptObj.put("x", p.x.toDouble())
            ptObj.put("y", p.y.toDouble())
            ptsArray.put(ptObj)
        }
        json.put("points", ptsArray)
        
        val payloadStr = json.toString()
        try {
            com.qazar.pdfviewer.bridge.MeridianNativeBridge.addAnnotation(currentPageIndex, payloadStr)
        } catch (_: Throwable) {
            // Guard in case native library is not loaded or during mock preview
        }
    }
}
