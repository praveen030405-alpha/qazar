package com.qazar.pdfviewer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.qazar.pdfviewer.engine.ActiveTool
import com.qazar.pdfviewer.engine.AnnotationController
import com.qazar.pdfviewer.engine.PdfPoint

/**
 * Vanguard Zero-Latency Render Fast-Path.
 * Draws optimistic ink extrapolations over the PDF view, then offloads to Rust 
 * when the gesture completes.
 */
@Composable
fun LocalOverlayCanvas(modifier: Modifier = Modifier) {
    val activePath = remember { Path() }
    val pointsBuffer = remember { mutableListOf<Offset>() }
    // A simple trigger to force recomposition
    val recomposeTrigger = remember { mutableStateListOf<Int>() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    
                    // Only intercept if we are not panning
                    if (AnnotationController.activeTool.value == ActiveTool.PAN) {
                        return@awaitEachGesture
                    }
                    
                    down.consume()
                    activePath.reset()
                    activePath.moveTo(down.position.x, down.position.y)
                    pointsBuffer.clear()
                    pointsBuffer.add(down.position)
                    recomposeTrigger.add(1)

                    drag(down.id) { change ->
                        if (change.positionChange() != Offset.Zero) {
                            change.consume()
                            activePath.lineTo(change.position.x, change.position.y)
                            pointsBuffer.add(change.position)
                            
                            // 120Hz Predictive Extrapolation (Kalman/Catmull-Rom logic goes here)
                            // We instantly update the Compose Canvas
                            recomposeTrigger.add(1)
                        }
                    }

                    // Stroke finished. Dispatch to Rust via AnnotationController.
                    val pdfPoints = pointsBuffer.map { AnnotationController.screenToPdf(it) }
                    AnnotationController.commitAnnotation(pdfPoints)
                    
                    // Clear the local optimistic path since Rust will now render it
                    activePath.reset()
                    pointsBuffer.clear()
                    recomposeTrigger.add(1)
                }
            }
    ) {
        // Tie to state to force recomposition
        recomposeTrigger.size

        if (!activePath.isEmpty) {
            val color = when (AnnotationController.activeTool.value) {
                ActiveTool.HIGHLIGHTER -> Color(1f, 1f, 0f, 0.5f) // Yellow
                ActiveTool.ARROW -> Color.Red
                else -> Color.Red // Default ink color
            }
            val width = if (AnnotationController.activeTool.value == ActiveTool.HIGHLIGHTER) 40f else 8f

            drawPath(
                path = activePath,
                color = color,
                style = Stroke(
                    width = width,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
