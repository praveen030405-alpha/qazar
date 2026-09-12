package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Apple PencilKit Realistic Writing Instruments.
 * Directly replicating the physical tools from the Apple Notes pencil tray:
 * - Pen (Fine ballpoint / technical tip with active color)
 * - Highlighter (45-degree angled chisel neon marker with Abc 80 badge)
 * - Marker / Crayon (Conical colored felt tip)
 * - Pencil (Sharpened striped graphite sketching pencil)
 * - Eraser (Pink rubber dome head with metallic ferrule)
 * - Laser (Luminous neon ruby pointer with tactical barrel)
 * - Pan (Natural navigation stylus)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ApplePencilToolItem(
    tool: AnnotationTool,
    activeColor: Color,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Tactile 8dp spring elevation when selected (matching Apple Notes)
    val elevationY by animateDpAsState(
        targetValue = if (isSelected) (-8).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ToolElevation_${tool.name}"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .offset(y = elevationY)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick?.invoke()
                }
            )
            .padding(horizontal = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(58.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                when (tool) {
                    AnnotationTool.LASSO_SELECT -> drawRealisticLasso(w, h, isSelected)
                    AnnotationTool.PEN -> drawRealisticPen(w, h, activeColor, isSelected)
                    AnnotationTool.HIGHLIGHTER -> drawRealisticHighlighter(w, h, activeColor, isSelected)
                    AnnotationTool.MARKER -> drawRealisticMarker(w, h, activeColor, isSelected)
                    AnnotationTool.PENCIL -> drawRealisticPencil(w, h, isSelected)
                    AnnotationTool.ERASER -> drawRealisticEraser(w, h, isSelected)
                    AnnotationTool.LASER_POINTER -> drawRealisticLaser(w, h, isSelected)
                    else -> drawRealisticPan(w, h, isSelected)
                }
            }
        }

        // Active indicator dot under the elevated tool
        if (isSelected) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

/**
 * 1. Apple Technical / Fine Ballpoint Pen
 */
private fun DrawScope.drawRealisticPen(w: Float, h: Float, color: Color, isSelected: Boolean) {
    val bodyTop = h * 0.36f
    val coneTop = h * 0.12f
    val tipTop = h * 0.04f

    // Cylindrical Body with 3D cylindrical lighting
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFE2E8F0), Color(0xFFFFFFFF), Color(0xFFCBD5E1))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Silver Ferrule Collar
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF94A3B8), Color(0xFFE2E8F0), Color(0xFF64748B))
        ),
        topLeft = Offset(w * 0.15f, bodyTop - (h * 0.06f)),
        size = Size(w * 0.7f, h * 0.06f)
    )

    // Tapered Metallic Cone
    val conePath = Path().apply {
        moveTo(w * 0.15f, bodyTop - (h * 0.06f))
        lineTo(w * 0.85f, bodyTop - (h * 0.06f))
        lineTo(w * 0.58f, coneTop)
        lineTo(w * 0.42f, coneTop)
        close()
    }
    drawPath(
        path = conePath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF94A3B8), Color(0xFFF1F5F9), Color(0xFF64748B))
        )
    )

    // Fine Colored Pen Nib
    val nibPath = Path().apply {
        moveTo(w * 0.42f, coneTop)
        lineTo(w * 0.58f, coneTop)
        lineTo(w * 0.5f, tipTop)
        close()
    }
    drawPath(path = nibPath, color = color)

    // Needle Point Ball Tip
    drawCircle(
        color = Color.White,
        radius = 1.2f,
        center = Offset(w * 0.5f, tipTop)
    )
}

/**
 * 2. Apple Highlighter (Chisel Neon Tip with Abc 80 badge)
 */
private fun DrawScope.drawRealisticHighlighter(w: Float, h: Float, color: Color, isSelected: Boolean) {
    val bodyTop = h * 0.42f
    val neckTop = h * 0.22f
    val chiselTop = h * 0.06f

    // Wide White Barrel
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFE2E8F0), Color(0xFFFFFFFF), Color(0xFFCBD5E1))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Silver Contoured Shoulder / Neck
    val neckPath = Path().apply {
        moveTo(w * 0.1f, bodyTop)
        lineTo(w * 0.9f, bodyTop)
        lineTo(w * 0.75f, neckTop)
        lineTo(w * 0.25f, neckTop)
        close()
    }
    drawPath(
        path = neckPath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF94A3B8), Color(0xFFF1F5F9), Color(0xFF64748B))
        )
    )

    // 45-degree Angled Neon Chisel Tip
    val chiselPath = Path().apply {
        moveTo(w * 0.28f, neckTop)
        lineTo(w * 0.72f, neckTop)
        lineTo(w * 0.82f, chiselTop + (h * 0.05f))
        lineTo(w * 0.32f, chiselTop)
        close()
    }
    drawPath(
        path = chiselPath,
        color = color.copy(alpha = 0.90f)
    )
}

/**
 * 3. Apple Marker / Crayon (Conical Pointed Felt Tip)
 */
private fun DrawScope.drawRealisticMarker(w: Float, h: Float, color: Color, isSelected: Boolean) {
    val bodyTop = h * 0.38f
    val collarTop = h * 0.32f
    val tipTop = h * 0.06f

    // White Body
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFE2E8F0), Color(0xFFFFFFFF), Color(0xFFCBD5E1))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Colored Collar Band
    drawRect(
        color = color,
        topLeft = Offset(0f, collarTop),
        size = Size(w, bodyTop - collarTop)
    )

    // Bold Conical Crayon / Felt Tip
    val tipPath = Path().apply {
        moveTo(w * 0.18f, collarTop)
        lineTo(w * 0.82f, collarTop)
        lineTo(w * 0.5f, tipTop)
        close()
    }
    drawPath(path = tipPath, color = color)
}

/**
 * 4. Apple Sketching Pencil (Striped Hexagonal Barrel & Lead Tip)
 */
private fun DrawScope.drawRealisticPencil(w: Float, h: Float, isSelected: Boolean) {
    val bodyTop = h * 0.34f
    val woodTop = h * 0.12f
    val leadTop = h * 0.04f

    // Striped Body
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFE2E8F0), Color(0xFFFFFFFF), Color(0xFFCBD5E1))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Pinstripes on body
    val stripeBrush = Brush.linearGradient(
        listOf(Color(0x35000000), Color.Transparent),
        start = Offset(0f, bodyTop),
        end = Offset(w, bodyTop + 14f)
    )
    drawRect(
        brush = stripeBrush,
        topLeft = Offset(w * 0.25f, bodyTop),
        size = Size(w * 0.5f, h - bodyTop)
    )

    // Sharpened Wooden Collar
    val woodPath = Path().apply {
        moveTo(w * 0.12f, bodyTop)
        lineTo(w * 0.88f, bodyTop)
        lineTo(w * 0.5f, woodTop)
        close()
    }
    drawPath(
        path = woodPath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFD4A373), Color(0xFFFAEDCD), Color(0xFFCCD5AE))
        )
    )

    // Graphite Lead Tip
    val leadPath = Path().apply {
        moveTo(w * 0.38f, woodTop + (h * 0.04f))
        lineTo(w * 0.62f, woodTop + (h * 0.04f))
        lineTo(w * 0.5f, leadTop)
        close()
    }
    drawPath(path = leadPath, color = Color(0xFF2B2D42))
}

/**
 * 5. Apple Stylus Eraser (Modern Apple-Style Angled Wedge & Silver Ferrule)
 */
private fun DrawScope.drawRealisticEraser(w: Float, h: Float, isSelected: Boolean) {
    val bodyTop = h * 0.40f
    val ferruleTop = h * 0.28f
    val tipTop = h * 0.06f

    // Cylindrical White Stylus Body with 3D lighting
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFE2E8F0), Color(0xFFFFFFFF), Color(0xFFCBD5E1))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Brushed Aluminum Metallic Ferrule
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF64748B), Color(0xFFE2E8F0), Color(0xFF94A3B8))
        ),
        topLeft = Offset(w * 0.06f, ferruleTop),
        size = Size(w * 0.88f, bodyTop - ferruleTop)
    )

    // Ferrule accent ring
    drawLine(
        color = Color(0x60000000),
        start = Offset(w * 0.06f, ferruleTop + (bodyTop - ferruleTop) * 0.5f),
        end = Offset(w * 0.94f, ferruleTop + (bodyTop - ferruleTop) * 0.5f),
        strokeWidth = 1f
    )

    // Modern Angled Chisel Rubber Eraser Head (Apple Pencil Bevel)
    val wedgePath = Path().apply {
        moveTo(w * 0.14f, ferruleTop)
        lineTo(w * 0.86f, ferruleTop)
        lineTo(w * 0.82f, tipTop + (h * 0.06f)) // Angled right corner
        lineTo(w * 0.24f, tipTop)               // High left chisel tip
        close()
    }
    drawPath(
        path = wedgePath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFFF472B6), Color(0xFFFDE8E8), Color(0xFFEC4899))
        )
    )

    // Highlight ridge on beveled edge
    drawLine(
        color = Color.White.copy(alpha = 0.8f),
        start = Offset(w * 0.24f, tipTop),
        end = Offset(w * 0.82f, tipTop + (h * 0.06f)),
        strokeWidth = 1.2f,
        cap = StrokeCap.Round
    )
}

/**
 * 5b. Apple Precision Lasso / Selection Stylus (Marching Ants Ring & Metallic Wand)
 */
private fun DrawScope.drawRealisticLasso(w: Float, h: Float, isSelected: Boolean) {
    val bodyTop = h * 0.38f
    val neckTop = h * 0.18f
    val loopCenterY = h * 0.09f

    // Sleek Stylus Body
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFF0F172A))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Metallic Collar Band
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF94A3B8), Color(0xFFF1F5F9), Color(0xFF64748B))
        ),
        topLeft = Offset(w * 0.12f, bodyTop - (h * 0.04f)),
        size = Size(w * 0.76f, h * 0.04f)
    )

    // Slender Stylus Shaft
    val shaftPath = Path().apply {
        moveTo(w * 0.25f, bodyTop - (h * 0.04f))
        lineTo(w * 0.75f, bodyTop - (h * 0.04f))
        lineTo(w * 0.58f, neckTop)
        lineTo(w * 0.42f, neckTop)
        close()
    }
    drawPath(
        path = shaftPath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF64748B), Color(0xFFE2E8F0), Color(0xFF475569))
        )
    )

    // Apple Blue Dashed Marching Loop Head
    drawCircle(
        color = Color(0xFF007AFF),
        radius = w * 0.36f,
        center = Offset(w * 0.5f, loopCenterY),
        style = Stroke(
            width = 1.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
        )
    )

    // Inner Core Accent
    drawCircle(
        color = Color(0xFF38BDF8),
        radius = w * 0.12f,
        center = Offset(w * 0.5f, loopCenterY)
    )
}

/**
 * 6. Apple Laser Pointer Stylus (Ruby Emitter & Tactical Titanium Body)
 */
private fun DrawScope.drawRealisticLaser(w: Float, h: Float, isSelected: Boolean) {
    val bodyTop = h * 0.36f
    val lensTop = h * 0.14f
    val emitterTop = h * 0.04f

    // Dark Titanium Body
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF1E1E2A), Color(0xFF2E2E3E), Color(0xFF14141E))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Glowing Crimson Collar Ring
    drawRect(
        color = Color(0xFFEF4444),
        topLeft = Offset(w * 0.1f, bodyTop - (h * 0.04f)),
        size = Size(w * 0.8f, h * 0.04f)
    )

    // Tapered Optic Lens Cone
    val conePath = Path().apply {
        moveTo(w * 0.15f, bodyTop - (h * 0.04f))
        lineTo(w * 0.85f, bodyTop - (h * 0.04f))
        lineTo(w * 0.55f, lensTop)
        lineTo(w * 0.45f, lensTop)
        close()
    }
    drawPath(
        path = conePath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF334155), Color(0xFF64748B), Color(0xFF1E293B))
        )
    )

    // Ruby Laser Emitter Head with Neon Glow
    drawCircle(
        color = Color(0xFFFF2A6D),
        radius = w * 0.24f,
        center = Offset(w * 0.5f, emitterTop + (h * 0.03f))
    )
    drawCircle(
        color = Color.White,
        radius = w * 0.10f,
        center = Offset(w * 0.5f, emitterTop + (h * 0.03f))
    )
}

/**
 * 7. Pan / Scroll Natural Gesture Tool
 */
private fun DrawScope.drawRealisticPan(w: Float, h: Float, isSelected: Boolean) {
    val bodyTop = h * 0.36f
    val handTop = h * 0.10f

    // Frosted Minimalist Body
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF334155), Color(0xFF475569), Color(0xFF1E293B))
        ),
        topLeft = Offset(0f, bodyTop),
        size = Size(w, h - bodyTop),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Sleek Natural Palm / Scroll Contour
    val palmPath = Path().apply {
        moveTo(w * 0.2f, bodyTop)
        lineTo(w * 0.8f, bodyTop)
        lineTo(w * 0.7f, handTop)
        lineTo(w * 0.3f, handTop)
        close()
    }
    drawPath(
        path = palmPath,
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF334155))
        )
    )

    // Directional scroll indicator glyph
    drawCircle(
        color = if (isSelected) Color(0xFFEF4444) else Color.White,
        radius = w * 0.14f,
        center = Offset(w * 0.5f, handTop + (h * 0.04f))
    )
}
