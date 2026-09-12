package com.qazar.pdfviewer.ui.viewer

import androidx.compose.ui.graphics.Color

enum class AnnotationTool(val displayName: String, val category: String) {
    // Navigation & Selection
    PAN("Pan", "Navigate"),
    LASSO_SELECT("Select", "Navigate"),

    // Drawing & Ink
    PEN("Pen", "Draw & Ink"),
    HIGHLIGHTER("Highlight", "Draw & Ink"),
    MARKER("Marker", "Draw & Ink"),
    FOUNTAIN_PEN("Fountain", "Draw & Ink"),
    PENCIL("Pencil", "Draw & Ink"),
    LASER_POINTER("Laser", "Draw & Ink"),
    ERASER("Eraser", "Draw & Ink"),

    // Shapes
    LINE("Line", "Shapes"),
    ARROW("Arrow", "Shapes"),
    DOUBLE_ARROW("2-Way Arrow", "Shapes"),
    RECTANGLE("Rectangle", "Shapes"),
    ROUNDED_RECT("Round Rect", "Shapes"),
    CIRCLE("Circle", "Shapes"),
    ELLIPSE("Ellipse", "Shapes"),
    POLYGON("Polygon", "Shapes"),
    POLYLINE("Polyline", "Shapes"),
    CLOUD("Cloud", "Shapes"),

    // Text & Notes
    TEXT_BOX("Text Box", "Text & Note"),
    STICKY_NOTE("Sticky Note", "Text & Note"),
    CALLOUT("Callout", "Text & Note"),
    COMMENT_BUBBLE("Comment", "Text & Note"),

    // Markup
    TEXT_HIGHLIGHT("Highlight", "Markup"),
    STRIKETHROUGH("Strikethrough", "Markup"),
    UNDERLINE("Underline", "Markup"),
    SQUIGGLY("Squiggle", "Markup"),
    CARET("Caret", "Markup"),
    STAMP("Stamp", "Markup"),

    // Measure
    RULER("Ruler", "Measure"),
    AREA_MEASURE("Area", "Measure"),
    PERIMETER("Perimeter", "Measure"),
    ANGLE("Angle", "Measure"),

    // Security & Sign
    SIGNATURE("Signature", "Security"),
    CERTIFICATE_ID("Cert ID", "Security"),
    REDACTION("Redact", "Security"),
    WATERMARK("Watermark", "Security");

    val isDrawingTool: Boolean
        get() = this in listOf(
            PEN, HIGHLIGHTER, MARKER, FOUNTAIN_PEN, PENCIL, LASER_POINTER,
            SIGNATURE, POLYLINE, POLYGON, CLOUD, LINE, ARROW, DOUBLE_ARROW,
            TEXT_HIGHLIGHT, STRIKETHROUGH, UNDERLINE, SQUIGGLY, CARET,
            RULER, AREA_MEASURE, PERIMETER, ANGLE
        )

    val isShapeTool: Boolean
        get() = this in listOf(
            RECTANGLE, ROUNDED_RECT, CIRCLE, TEXT_BOX, STICKY_NOTE,
            CALLOUT, COMMENT_BUBBLE, STAMP, REDACTION, WATERMARK, CERTIFICATE_ID
        )

    val isEraser: Boolean
        get() = this == ERASER
}

data class ToolSettings(
    val color: Color,
    val strokeWidth: Float,
    val opacity: Float = 1.0f
)

fun defaultToolSettings(): Map<AnnotationTool, ToolSettings> = mapOf(
    AnnotationTool.PAN to ToolSettings(Color(0xFF38BDF8), 2.0f),
    AnnotationTool.LASSO_SELECT to ToolSettings(Color(0xFF007AFF), 2.0f),
    AnnotationTool.PEN to ToolSettings(Color(0xFFEF4444), 3.0f),
    AnnotationTool.HIGHLIGHTER to ToolSettings(Color(0xFFFACC15), 14.0f, opacity = 0.35f),
    AnnotationTool.MARKER to ToolSettings(Color(0xFF2563EB), 6.0f),
    AnnotationTool.FOUNTAIN_PEN to ToolSettings(Color(0xFF1E3A8A), 2.5f),
    AnnotationTool.PENCIL to ToolSettings(Color(0xFF64748B), 1.5f, opacity = 0.75f),
    AnnotationTool.LASER_POINTER to ToolSettings(Color(0xFFFF1E44), 5.0f),
    AnnotationTool.ERASER to ToolSettings(Color.White, 24.0f),
    AnnotationTool.LINE to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.ARROW to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.DOUBLE_ARROW to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.RECTANGLE to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.ROUNDED_RECT to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.CIRCLE to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.POLYGON to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.POLYLINE to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.CLOUD to ToolSettings(Color(0xFFEF4444), 2.5f),
    AnnotationTool.TEXT_BOX to ToolSettings(Color(0xFFEF4444), 2.0f),
    AnnotationTool.STICKY_NOTE to ToolSettings(Color(0xFFEAB308), 2.0f),
    AnnotationTool.CALLOUT to ToolSettings(Color(0xFFEF4444), 2.0f),
    AnnotationTool.COMMENT_BUBBLE to ToolSettings(Color(0xFFEF4444), 2.0f),
    AnnotationTool.TEXT_HIGHLIGHT to ToolSettings(Color(0xFFFACC15), 12.0f, opacity = 0.35f),
    AnnotationTool.STRIKETHROUGH to ToolSettings(Color(0xFFEF4444), 2.0f),
    AnnotationTool.UNDERLINE to ToolSettings(Color(0xFFEF4444), 2.0f),
    AnnotationTool.SQUIGGLY to ToolSettings(Color(0xFFEF4444), 2.0f),
    AnnotationTool.CARET to ToolSettings(Color(0xFFEF4444), 2.0f),
    AnnotationTool.STAMP to ToolSettings(Color(0xFFEF4444), 3.0f),
    AnnotationTool.RULER to ToolSettings(Color(0xFF007AFF), 2.0f),
    AnnotationTool.AREA_MEASURE to ToolSettings(Color(0xFF007AFF), 2.0f),
    AnnotationTool.PERIMETER to ToolSettings(Color(0xFF007AFF), 2.0f),
    AnnotationTool.ANGLE to ToolSettings(Color(0xFF007AFF), 2.0f),
    AnnotationTool.SIGNATURE to ToolSettings(Color(0xFF1E3A8A), 2.5f),
    AnnotationTool.CERTIFICATE_ID to ToolSettings(Color(0xFF10B981), 2.0f),
    AnnotationTool.REDACTION to ToolSettings(Color.Black, 2.0f),
    AnnotationTool.WATERMARK to ToolSettings(Color(0xFFEF4444), 2.0f)
)

data class AnnotationStudioState(
    val activeTool: AnnotationTool = AnnotationTool.PAN,
    val selectedColor: Color = Color(0xFFEF4444),
    val strokeWidth: Float = 3.0f,
    val opacity: Float = 1.0f,
    val eraserRadius: Float = 24.0f,
    val isObjectEraser: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val toolSettings: Map<AnnotationTool, ToolSettings> = defaultToolSettings()
) {
    fun getToolColor(tool: AnnotationTool): Color {
        return toolSettings[tool]?.color ?: if (tool == activeTool) selectedColor else Color(0xFFEF4444)
    }

    fun getToolWidth(tool: AnnotationTool): Float {
        return toolSettings[tool]?.strokeWidth ?: if (tool == activeTool) strokeWidth else 3.0f
    }

    fun withToolColor(tool: AnnotationTool, color: Color): AnnotationStudioState {
        val current = toolSettings[tool] ?: ToolSettings(color, strokeWidth)
        val updated = toolSettings + (tool to current.copy(color = color))
        return copy(
            selectedColor = if (tool == activeTool) color else selectedColor,
            toolSettings = updated
        )
    }

    fun withToolWidth(tool: AnnotationTool, width: Float): AnnotationStudioState {
        val current = toolSettings[tool] ?: ToolSettings(selectedColor, width)
        val updated = toolSettings + (tool to current.copy(strokeWidth = width))
        return copy(
            strokeWidth = if (tool == activeTool) width else strokeWidth,
            toolSettings = updated
        )
    }

    fun selectTool(newTool: AnnotationTool): AnnotationStudioState {
        val cfg = toolSettings[newTool] ?: defaultToolSettings()[newTool] ?: ToolSettings(selectedColor, strokeWidth)
        return copy(
            activeTool = newTool,
            selectedColor = cfg.color,
            strokeWidth = cfg.strokeWidth,
            opacity = cfg.opacity
        )
    }
}
