package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.GoogleSansTextFamily

data class ToolItem(
    val tool: AnnotationTool,
    val icon: ImageVector
)

val ALL_TOOLS: List<ToolItem> = listOf(
    // Navigate
    ToolItem(AnnotationTool.PAN, Icons.Default.PanTool),
    ToolItem(AnnotationTool.LASSO_SELECT, Icons.Default.HighlightAlt),

    // Draw & Ink
    ToolItem(AnnotationTool.PEN, Icons.Default.Edit),
    ToolItem(AnnotationTool.HIGHLIGHTER, Icons.Default.Brush),
    ToolItem(AnnotationTool.MARKER, Icons.Default.BorderColor),
    ToolItem(AnnotationTool.FOUNTAIN_PEN, Icons.Default.Draw),
    ToolItem(AnnotationTool.PENCIL, Icons.Default.Create),
    ToolItem(AnnotationTool.LASER_POINTER, Icons.Default.Flare),
    ToolItem(AnnotationTool.ERASER, Icons.Default.AutoFixHigh),

    // Shapes
    ToolItem(AnnotationTool.LINE, Icons.Default.HorizontalRule),
    ToolItem(AnnotationTool.ARROW, Icons.Default.TrendingFlat),
    ToolItem(AnnotationTool.DOUBLE_ARROW, Icons.Default.CompareArrows),
    ToolItem(AnnotationTool.RECTANGLE, Icons.Default.CropSquare),
    ToolItem(AnnotationTool.ROUNDED_RECT, Icons.Default.Crop169),
    ToolItem(AnnotationTool.CIRCLE, Icons.Default.RadioButtonUnchecked),
    ToolItem(AnnotationTool.POLYGON, Icons.Default.Hexagon),
    ToolItem(AnnotationTool.POLYLINE, Icons.Default.Timeline),
    ToolItem(AnnotationTool.CLOUD, Icons.Default.CloudQueue),

    // Text & Notes
    ToolItem(AnnotationTool.TEXT_BOX, Icons.Default.TextFields),
    ToolItem(AnnotationTool.STICKY_NOTE, Icons.Default.StickyNote2),
    ToolItem(AnnotationTool.CALLOUT, Icons.Default.ChatBubbleOutline),
    ToolItem(AnnotationTool.COMMENT_BUBBLE, Icons.Default.Comment),

    // Markup
    ToolItem(AnnotationTool.TEXT_HIGHLIGHT, Icons.Default.FormatColorFill),
    ToolItem(AnnotationTool.STRIKETHROUGH, Icons.Default.FormatStrikethrough),
    ToolItem(AnnotationTool.UNDERLINE, Icons.Default.FormatUnderlined),
    ToolItem(AnnotationTool.SQUIGGLY, Icons.Default.Waves),
    ToolItem(AnnotationTool.CARET, Icons.Default.KeyboardArrowUp),
    ToolItem(AnnotationTool.STAMP, Icons.Default.Approval),

    // Measure
    ToolItem(AnnotationTool.RULER, Icons.Default.Straighten),
    ToolItem(AnnotationTool.AREA_MEASURE, Icons.Default.AspectRatio),
    ToolItem(AnnotationTool.PERIMETER, Icons.Default.SquareFoot),
    ToolItem(AnnotationTool.ANGLE, Icons.Default.RotateRight),

    // Security
    ToolItem(AnnotationTool.SIGNATURE, Icons.Default.HistoryEdu),
    ToolItem(AnnotationTool.CERTIFICATE_ID, Icons.Default.VerifiedUser),
    ToolItem(AnnotationTool.REDACTION, Icons.Default.VisibilityOff),
    ToolItem(AnnotationTool.WATERMARK, Icons.Default.BrandingWatermark)
)

private val CURATED_PALETTE = listOf(
    Color(0xFFEF4444), // Crimson Red
    Color(0xFFDC2626), // Deep Cardinal
    Color(0xFFF97316), // Tangerine
    Color(0xFFF59E0B), // Amber Gold
    Color(0xFFFACC15), // Neon Yellow
    Color(0xFF10B981), // Emerald
    Color(0xFF06B6D4), // Cyan
    Color(0xFF3B82F6), // Cobalt
    Color(0xFF6366F1), // Indigo
    Color(0xFFA855F7), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFFFFFFFF), // White
    Color(0xFF64748B), // Slate
    Color(0xFF0F172A)  // Black
)

private val STROKE_PRESETS = listOf(
    Pair("1.5 pt", 1.5f),
    Pair("3.0 pt", 3.0f),
    Pair("6.0 pt", 6.0f),
    Pair("12 pt", 12.0f),
    Pair("24 pt", 24.0f)
)

private val CATEGORIES = listOf(
    "All",
    "Draw & Ink",
    "Shapes",
    "Text & Note",
    "Markup",
    "Measure",
    "Security"
)

/**
 * Side-sliding Annotation Studio Drawer matching ThumbnailStrip design.
 * Covers 3/4th screen width, sits below header bar, and is fully scrollable to the top.
 */
@Composable
fun AnnotationStudioDrawer(
    isVisible: Boolean,
    studioState: AnnotationStudioState,
    onStudioStateChange: (AnnotationStudioState) -> Unit,
    onClose: () -> Unit,
    topPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp.dp * 0.75f).coerceAtLeast(280.dp)
    val haptic = LocalHapticFeedback.current
    var selectedCategory by remember { mutableStateOf("All") }

    val filteredTools = remember(selectedCategory) {
        if (selectedCategory == "All") {
            ALL_TOOLS
        } else {
            ALL_TOOLS.filter { it.tool.category == selectedCategory }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(220)) + fadeOut(),
        modifier = modifier
            .padding(top = topPadding)
            .fillMaxHeight()
            .width(drawerWidth)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(24.dp, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF140D12), Color(0xFF0D0D15), Color(0xFF08080D))
                    )
                )
                .drawBehind {
                    // Start (Left) border
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                    // Bottom border
                    drawLine(
                        color = Color(0xFFEF4444),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { /* consume taps inside drawer */ }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // 1. Header: Four-Way Glyph Badge + Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0x35DC2626), Color(0x15991B1B))
                                    )
                                )
                                .border(1.dp, Color(0x50EF4444), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ControlCamera, // Four-way glyph
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Annotation Studio",
                                fontSize = 17.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${ALL_TOOLS.size} Precision Tools â€¢ Vector Canvas",
                                fontSize = 11.sp,
                                fontFamily = GoogleSansTextFamily,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Close Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onClose()
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0x18FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Studio",
                            tint = Color(0xFFE2E8F0),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = Color(0x15FFFFFF),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // 2. Color Palette Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "COLOR PALETTE",
                        fontSize = 10.5.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFFA1A1AA)
                    )

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(studioState.selectedColor)
                            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CURATED_PALETTE.forEach { color ->
                        val isSelected = studioState.selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, Color.White, CircleShape)
                                    } else {
                                        Modifier.border(0.5.dp, Color(0x30FFFFFF), CircleShape)
                                    }
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onStudioStateChange(studioState.copy(selectedColor = color))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = if (color == Color.White) Color.Black else Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Stroke Size Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "STROKE SIZE",
                        fontSize = 10.5.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = Color(0xFFA1A1AA)
                    )

                    // Live Preview Capsule
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x28DC2626))
                            .border(0.5.dp, Color(0x40EF4444), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(studioState.strokeWidth.coerceIn(1.5f, 14f).dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(studioState.selectedColor)
                        )
                        Text(
                            text = "%.1f pt".format(studioState.strokeWidth),
                            fontSize = 10.5.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Presets
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    STROKE_PRESETS.forEach { (label, width) ->
                        val isSelected = kotlin.math.abs(studioState.strokeWidth - width) < 0.2f
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFFDC2626) else Color(0x18FFFFFF),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFFEF4444) else Color(0x20FFFFFF)
                            ),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onStudioStateChange(studioState.copy(strokeWidth = width))
                            }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.5.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Slider
                Slider(
                    value = studioState.strokeWidth,
                    onValueChange = { newW ->
                        onStudioStateChange(studioState.copy(strokeWidth = newW))
                    },
                    valueRange = 1f..36f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFEF4444),
                        activeTrackColor = Color(0xFFDC2626),
                        inactiveTrackColor = Color(0x25FFFFFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                )

                HorizontalDivider(
                    color = Color(0x15FFFFFF),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // 4. Category Filter Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CATEGORIES.forEach { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFDC2626), Color(0xFF991B1B))
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(Color(0x18FFFFFF), Color(0x10FFFFFF))
                                        )
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFEF4444) else Color(0x15FFFFFF),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategory = category
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 11.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Tools Grid: 3 Columns inside 3/4th Drawer with SVGs and Labels
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp, max = 500.dp)
                        .padding(bottom = 20.dp)
                ) {
                    items(filteredTools) { item ->
                        val isSelected = studioState.activeTool == item.tool

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.verticalGradient(
                                            listOf(Color(0x40DC2626), Color(0x18DC2626))
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(Color(0xFF1C1C29), Color(0xFF161623))
                                        )
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isSelected) Color(0xFFEF4444) else Color(0x18FFFFFF),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStudioStateChange(studioState.copy(activeTool = item.tool))
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.tool.displayName,
                                    tint = if (isSelected) Color(0xFFEF4444) else Color(0xFFE2E8F0),
                                    modifier = Modifier.size(22.dp)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = item.tool.displayName,
                                    fontSize = 10.sp,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFFA1A1AA),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
