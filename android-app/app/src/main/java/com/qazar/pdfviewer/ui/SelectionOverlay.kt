package com.qazar.pdfviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Draws the bounding boxes and resize handles around currently selected annotations.
 * Uses dashed strokes and corner drag handles.
 */
@Composable
fun SelectionOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // In a full implementation, we'd query the JNI bridge for the current selection rects.
        // For Phase 3 architecture validation, we draw a dummy selection box to demonstrate
        // the Compose layer structure over the PDF.
        val selectionActive = false 
        
        if (selectionActive) {
            val rectStart = Offset(200f, 300f)
            val rectSize = Size(400f, 200f)
            
            // Draw Dashed Bounding Box
            drawRoundRect(
                color = Color.Blue,
                topLeft = rectStart,
                size = rectSize,
                cornerRadius = CornerRadius(8f, 8f),
                style = Stroke(
                    width = 4f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(10f, 10f), 0f
                    )
                )
            )

            // Draw Resize Handles (Corners)
            val handleRadius = 12f
            drawCircle(Color.Blue, handleRadius, rectStart) // Top Left
            drawCircle(Color.Blue, handleRadius, Offset(rectStart.x + rectSize.width, rectStart.y)) // Top Right
            drawCircle(Color.Blue, handleRadius, Offset(rectStart.x, rectStart.y + rectSize.height)) // Bottom Left
            drawCircle(Color.Blue, handleRadius, Offset(rectStart.x + rectSize.width, rectStart.y + rectSize.height)) // Bottom Right
        }
    }
}
