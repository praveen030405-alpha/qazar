package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.theme.GoogleSansFamily

/**
 * Apple Notes PencilKit-Grade Precision Toolbar in Obsidian Black & Crimson Red.
 * Docked directly beneath the TopCommandBar header:
 * - Direct attachment: Seamlessly transitions and hides/shows synchronously with the Header
 * - Realistic physical Apple writing instruments (Pen, Chisel Highlighter, Marker, Pencil, Eraser, Laser, Pan)
 * - 8dp spring elevation on selection with tactile haptic response
 * - Long-press or tap-again on Pen / Highlighter / Tools summons Apple-style subtle quick color & pt palette popover
 * - Auto-dismisses upon selection and Done
 * - Circular Undo & Redo controls with reactive enabled states
 * - 6-circle color palette with concentric target-ring halo on active selection + Rainbow Wheel
 * - Clear Page & 3-dots Studio drawer actions
 * - Minimalist 'âœ•' Close button to cleanly exit annotation mode
 * - Smooth horizontal scroll for edge-to-edge responsiveness on all screens
 */
@Composable
fun FloatingQuickToolBar(
    activeTool: AnnotationTool,
    quickTools: List<AnnotationTool>,
    selectedColor: Color,
    onSelectTool: (AnnotationTool) -> Unit,
    onSelectColor: (Color) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onClearPage: () -> Unit,
    onOpenStudio: () -> Unit,
    onClose: () -> Unit,
    isCollapsedToBall: Boolean,
    onToggleExpandBall: () -> Unit,
    strokeWidth: Float = 3.0f,
    onSelectStrokeWidth: ((Float) -> Unit)? = null,
    eraserRadius: Float = 24.0f,
    onSelectEraserRadius: ((Float) -> Unit)? = null,
    isObjectEraser: Boolean = false,
    onToggleEraserMode: ((Boolean) -> Unit)? = null,
    getToolColor: ((AnnotationTool) -> Color)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Apple-style Tool Palette Popover (summons on long-press or tap on active tool)
    var popoverTool by remember { mutableStateOf<AnnotationTool?>(null) }

    AnimatedContent(
        targetState = isCollapsedToBall,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f))
                .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.85f))
        },
        label = "ToolbarBallTransition"
    ) { collapsed ->
        if (collapsed) {
            // ðŸ”´ Sleek Glowing Annotate Ball (Gesture Orb)
            val infiniteTransition = rememberInfiniteTransition(label = "BallPulse")
            val pulseGlow by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "GlowAnim"
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleExpandBall()
                },
                shape = CircleShape,
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(
                    1.8.dp,
                    Color(0xFFEF4444).copy(alpha = pulseGlow)
                ),
                shadowElevation = 18.dp,
                modifier = modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF240E16), Color(0xFF0E0E16))
                        )
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getToolIcon(activeTool),
                        contentDescription = "Expand Annotation Toolbar",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(22.dp)
                    )
                    // Tiny active color indicator dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 5.dp, end = 5.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(selectedColor)
                            .border(0.8.dp, Color.White, CircleShape)
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier
            ) {
                // ðŸŽ¨ Apple-Grade Subtle Quick Palette Popover (Long-press / Tap Active Tool)
                AnimatedVisibility(
                    visible = popoverTool != null,
                    enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(160))
                ) {
                    if (popoverTool != null) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xF712121A), // 97% Deep Charcoal Glass
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFEF4444)),
                            shadowElevation = 18.dp,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .widthIn(min = 280.dp, max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Header: Tool Title + Done Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = getToolIcon(popoverTool!!),
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (popoverTool == AnnotationTool.ERASER) "Eraser Size" else (popoverTool?.displayName ?: "Tool"),
                                            fontFamily = GoogleSansFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }

                                    // Subtle Done Button
                                    Surface(
                                        onClick = { popoverTool = null },
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0x33EF4444),
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFEF4444))
                                    ) {
                                        Text(
                                            text = "Done",
                                            fontSize = 11.sp,
                                            fontFamily = GoogleSansFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFEF4444),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                if (popoverTool == AnnotationTool.ERASER) {
                                    // Segmented Toggle: Pixel Eraser vs Object Eraser (Apple Notes Style)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x20FFFFFF))
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onToggleEraserMode?.invoke(false)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (!isObjectEraser) Color(0xFFEF4444) else Color.Transparent,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                                                Text(
                                                    text = "Pixel Eraser",
                                                    fontSize = 11.sp,
                                                    fontFamily = GoogleSansFamily,
                                                    fontWeight = if (!isObjectEraser) FontWeight.Bold else FontWeight.Normal,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Surface(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onToggleEraserMode?.invoke(true)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isObjectEraser) Color(0xFFEF4444) else Color.Transparent,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 5.dp)) {
                                                Text(
                                                    text = "Object Eraser",
                                                    fontSize = 11.sp,
                                                    fontFamily = GoogleSansFamily,
                                                    fontWeight = if (isObjectEraser) FontWeight.Bold else FontWeight.Normal,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    if (isObjectEraser) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0x18EF4444))
                                                .border(0.8.dp, Color(0x35EF4444), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Object Eraser deletes entire strokes or shapes with a single tap or brush.",
                                                fontSize = 10.sp,
                                                color = Color(0xDDE2E8F0),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    } else {
                                        // ERASER SIZING CONTROLS
                                        val eraserPresets = listOf(
                                            Triple("Fine", 8.0f, "8px"),
                                            Triple("Medium", 24.0f, "24px"),
                                            Triple("Broad", 48.0f, "48px")
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            eraserPresets.forEach { (label, r, sizeStr) ->
                                                val isSelectedR = kotlin.math.abs(eraserRadius - r) < 2.0f
                                                Surface(
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSelectEraserRadius?.invoke(r)
                                                    },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isSelectedR) Color(0x33EF4444) else Color(0x18FFFFFF),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        0.8.dp,
                                                        if (isSelectedR) Color(0xFFEF4444) else Color(0x25FFFFFF)
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (isSelectedR) Color(0xFFEF4444) else Color(0xCCFFFFFF)
                                                        )
                                                        Text(
                                                            text = sizeStr,
                                                            fontSize = 9.sp,
                                                            color = Color(0x80FFFFFF)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Continuous Eraser Slider 6px to 64px with live reticle preview
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Slider(
                                                value = eraserRadius.coerceIn(6f, 64f),
                                                onValueChange = { newR ->
                                                    onSelectEraserRadius?.invoke(newR)
                                                },
                                                valueRange = 6f..64f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color(0xFFEF4444),
                                                    activeTrackColor = Color(0xFFEF4444),
                                                    inactiveTrackColor = Color(0x25FFFFFF)
                                                ),
                                                modifier = Modifier.weight(1f).height(24.dp)
                                            )

                                            // Live Reticle Orb Preview
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val previewSize = ((eraserRadius / 64f) * 28f).coerceIn(6f, 28f).dp
                                                Box(
                                                    modifier = Modifier
                                                        .size(previewSize)
                                                        .clip(CircleShape)
                                                        .background(Color(0x33FF69B4))
                                                        .border(1.2.dp, Color(0xFFFF69B4), CircleShape)
                                                )
                                            }
                                        }
                                    }
                                } else if (popoverTool == AnnotationTool.LASER_POINTER) {
                                    // Fixed Laser Pointer Info
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x20EF4444))
                                            .border(0.8.dp, Color(0x50EF4444), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF240E16))
                                                .border(1.2.dp, Color(0xFFEF4444), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFFF1E44))
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Keynote Laser Pointer",
                                                fontFamily = GoogleSansFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Optical ruby neon beam with 0.5s trail dissolution. Color & size are fixed.",
                                                fontSize = 9.5.sp,
                                                color = Color(0xCCFFFFFF),
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                } else if (popoverTool == AnnotationTool.LASSO_SELECT) {
                                    // Select & Move Info
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0x20007AFF))
                                            .border(0.8.dp, Color(0x50007AFF), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.HighlightAlt,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Select & Move Tool",
                                                fontFamily = GoogleSansFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Tap any stroke or shape to select it, then drag to reposition.",
                                                fontSize = 9.5.sp,
                                                color = Color(0xCCFFFFFF),
                                                lineHeight = 13.sp
                                            )
                                        }
                                    }
                                } else {
                                    // DRAWING INSTRUMENT: Colors + 0.1px to 24px Slider
                                    // 1. 7 Quick Color Swatches
                                    val quickColors = listOf(
                                        Color(0xFF000000), // Black
                                        Color(0xFFFFFFFF), // White
                                        Color(0xFF2563EB), // Blue
                                        Color(0xFF16A34A), // Green
                                        Color(0xFFEAB308), // Yellow
                                        Color(0xFFF97316), // Orange
                                        Color(0xFFEF4444)  // Crimson Red
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        quickColors.forEach { color ->
                                            val isSelectedColor = (selectedColor.value == color.value)
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(
                                                        width = if (isSelectedColor) 2.2.dp else 0.8.dp,
                                                        color = if (isSelectedColor) Color(0xFFEF4444) else Color(0x40FFFFFF),
                                                        shape = CircleShape
                                                    )
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSelectColor(color)
                                                    }
                                            )
                                        }
                                    }

                                    // 2. Preset Chips: 0.5, 1.5, 3.0, 6.0, 12.0, 24.0
                                    val strokePresets = listOf(0.5f, 1.5f, 3.0f, 6.0f, 12.0f, 24.0f)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        strokePresets.forEach { w ->
                                            val isSelectedW = kotlin.math.abs(strokeWidth - w) < 0.3f
                                            Surface(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSelectStrokeWidth?.invoke(w)
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isSelectedW) Color(0x33EF4444) else Color(0x18FFFFFF),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    0.8.dp,
                                                    if (isSelectedW) Color(0xFFEF4444) else Color.Transparent
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier.padding(vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = if (w == 0.5f) "0.5" else w.toInt().toString(),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = if (isSelectedW) Color(0xFFEF4444) else Color(0xCCFFFFFF)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 3. Continuous 0.1px to 24px Slider with live preview orb
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Slider(
                                            value = strokeWidth.coerceIn(0.1f, 24.0f),
                                            onValueChange = { newW ->
                                                onSelectStrokeWidth?.invoke(newW)
                                            },
                                            valueRange = 0.1f..24.0f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFFEF4444),
                                                activeTrackColor = Color(0xFFEF4444),
                                                inactiveTrackColor = Color(0x25FFFFFF)
                                            ),
                                            modifier = Modifier.weight(1f).height(24.dp)
                                        )

                                        // Text badge + Live Dot Orb
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = String.format("%.1f", strokeWidth),
                                                fontSize = 11.sp,
                                                fontFamily = GoogleSansFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFEF4444)
                                            )

                                            // Live Orb showing stroke thickness in selected color
                                            Box(
                                                modifier = Modifier.size(26.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val dotSize = ((strokeWidth / 24f) * 22f).coerceIn(2f, 22f).dp
                                                Box(
                                                    modifier = Modifier
                                                        .size(dotSize)
                                                        .clip(CircleShape)
                                                        .background(selectedColor)
                                                        .border(0.5.dp, Color.White, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ðŸŽ› Full Apple Notes PencilKit Tray in Obsidian & Crimson Red
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFFEF4444)),
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF160E14), // Subtle Crimson Top Sheen
                                    Color(0xFF0F0F17), // Solid Obsidian Base
                                    Color(0xFF0A0A0E)  // Deep Charcoal Bottom
                                )
                            )
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        // Top Drag/Dock Indicator Pill
                        Box(
                            modifier = Modifier
                                .padding(bottom = 3.dp)
                                .width(32.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(Color(0x35FFFFFF))
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(scrollState),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 1. Left Controls: Circular Undo & Redo Buttons
                            QuickToolButton(
                                icon = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                enabled = canUndo,
                                onClick = onUndo
                            )

                            QuickToolButton(
                                icon = Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                enabled = canRedo,
                                onClick = onRedo
                            )

                            // Thin Crimson Separator
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(Color(0x35EF4444))
                            )

                            // 2. Center: Apple PencilKit Realistic Instruments Rack
                            val displayTools = remember(quickTools, activeTool) {
                                val baseList = mutableListOf(
                                    AnnotationTool.PAN,
                                    AnnotationTool.LASSO_SELECT,
                                    AnnotationTool.PEN,
                                    AnnotationTool.HIGHLIGHTER,
                                    AnnotationTool.MARKER,
                                    AnnotationTool.PENCIL,
                                    AnnotationTool.ERASER,
                                    AnnotationTool.LASER_POINTER
                                )
                                if (!baseList.contains(activeTool)) {
                                    baseList.add(activeTool)
                                }
                                baseList
                            }

                            displayTools.forEach { tool ->
                                val isSelected = (activeTool == tool)
                                val toolColor = getToolColor?.invoke(tool) ?: if (tool == activeTool) selectedColor else Color(0xFFEF4444)
                                ApplePencilToolItem(
                                    tool = tool,
                                    activeColor = toolColor,
                                    isSelected = isSelected,
                                    onSelect = {
                                        if (isSelected && tool != AnnotationTool.PAN) {
                                            // Tapping active tool opens quick palette popover
                                            popoverTool = if (popoverTool == tool) null else tool
                                        } else {
                                            onSelectTool(tool)
                                            popoverTool = null
                                        }
                                    },
                                    onLongClick = {
                                        // Long-press on tool summons palette popover exactly matching Apple's
                                        onSelectTool(tool)
                                        popoverTool = tool
                                    }
                                )
                            }

                            // Thin Crimson Separator
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(Color(0x35EF4444))
                            )

                            // 3. Right: Color Swatches or Tool Mode Status
                            if (activeTool == AnnotationTool.LASER_POINTER) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x25EF4444),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF1E44))
                                        )
                                        Text(
                                            text = "Fixed Laser",
                                            fontSize = 10.sp,
                                            fontFamily = GoogleSansFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFFF6B81)
                                        )
                                    }
                                }
                            } else if (activeTool == AnnotationTool.LASSO_SELECT) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0x25007AFF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF007AFF))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF007AFF))
                                        )
                                        Text(
                                            text = "Select & Move",
                                            fontSize = 10.sp,
                                            fontFamily = GoogleSansFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }
                            } else {
                                // Color Swatches Palette (5 Colors + Apple Halo Ring + Rainbow Wheel)
                                val paletteColors = listOf(
                                    Color(0xFF000000), // Black
                                    Color(0xFF2563EB), // Blue
                                    Color(0xFF16A34A), // Green
                                    Color(0xFFEAB308), // Yellow
                                    Color(0xFFEF4444)  // Crimson Red
                                )

                                paletteColors.forEach { color ->
                                    val isColorSelected = (selectedColor.value == color.value)
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onSelectColor(color)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isColorSelected) {
                                            // Apple concentric halo target ring
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .border(1.8.dp, color, CircleShape)
                                                    .padding(3.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .border(0.8.dp, Color(0x50FFFFFF), CircleShape)
                                            )
                                        }
                                    }
                                }

                                // Rainbow Color Wheel Picker Button
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color.Red, Color.Yellow, Color.Green,
                                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                                )
                                            )
                                        )
                                        .border(1.dp, Color(0x60FFFFFF), CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onOpenStudio()
                                        }
                                )
                            }

                            // Thin Crimson Separator
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(32.dp)
                                    .background(Color(0x35EF4444))
                            )

                            // Clear Page Markings Button (Trash)
                            QuickToolButton(
                                icon = Icons.Default.DeleteOutline,
                                contentDescription = "Clear Page",
                                onClick = onClearPage
                            )

                            // More Tools (3-Dots to open full studio drawer)
                            QuickToolButton(
                                icon = Icons.Default.MoreHoriz,
                                contentDescription = "More Tools",
                                onClick = onOpenStudio
                            )

                            // Minimalist 'âœ•' Close Button
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x28DC2626))
                                    .border(1.dp, Color(0x70EF4444), CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClose()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Annotation Mode",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tactical Button with spring scale micro-animation on press.
 */
@Composable
private fun QuickToolButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ButtonScale"
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color(0xFF161622))
            .border(0.8.dp, if (enabled) Color(0x30FFFFFF) else Color(0x10FFFFFF), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color(0x40FFFFFF),
            modifier = Modifier.size(16.dp)
        )
    }
}

fun getToolIcon(tool: AnnotationTool): ImageVector = when (tool) {
    AnnotationTool.PAN -> Icons.Default.PanTool
    AnnotationTool.LASSO_SELECT -> Icons.Default.HighlightAlt
    AnnotationTool.PEN -> Icons.Default.Edit
    AnnotationTool.HIGHLIGHTER -> Icons.Default.Brush
    AnnotationTool.MARKER -> Icons.Default.BorderColor
    AnnotationTool.FOUNTAIN_PEN -> Icons.Default.Draw
    AnnotationTool.PENCIL -> Icons.Default.Create
    AnnotationTool.LASER_POINTER -> Icons.Default.Flare
    AnnotationTool.ERASER -> Icons.Default.AutoFixHigh
    AnnotationTool.LINE -> Icons.Default.HorizontalRule
    AnnotationTool.ARROW -> Icons.Default.TrendingFlat
    AnnotationTool.DOUBLE_ARROW -> Icons.Default.CompareArrows
    AnnotationTool.RECTANGLE -> Icons.Default.CropSquare
    AnnotationTool.ROUNDED_RECT -> Icons.Default.Crop169
    AnnotationTool.CIRCLE -> Icons.Default.RadioButtonUnchecked
    AnnotationTool.ELLIPSE -> Icons.Default.Lens
    AnnotationTool.POLYGON -> Icons.Default.Hexagon
    AnnotationTool.POLYLINE -> Icons.Default.Timeline
    AnnotationTool.CLOUD -> Icons.Default.CloudQueue
    AnnotationTool.TEXT_BOX -> Icons.Default.TextFields
    AnnotationTool.STICKY_NOTE -> Icons.Default.StickyNote2
    AnnotationTool.CALLOUT -> Icons.Default.ChatBubbleOutline
    AnnotationTool.COMMENT_BUBBLE -> Icons.Default.Comment
    AnnotationTool.TEXT_HIGHLIGHT -> Icons.Default.FormatColorFill
    AnnotationTool.STRIKETHROUGH -> Icons.Default.FormatStrikethrough
    AnnotationTool.UNDERLINE -> Icons.Default.FormatUnderlined
    AnnotationTool.SQUIGGLY -> Icons.Default.Waves
    AnnotationTool.CARET -> Icons.Default.KeyboardArrowUp
    AnnotationTool.STAMP -> Icons.Default.Approval
    AnnotationTool.RULER -> Icons.Default.Straighten
    AnnotationTool.AREA_MEASURE -> Icons.Default.AspectRatio
    AnnotationTool.PERIMETER -> Icons.Default.SquareFoot
    AnnotationTool.ANGLE -> Icons.Default.RotateRight
    AnnotationTool.SIGNATURE -> Icons.Default.HistoryEdu
    AnnotationTool.CERTIFICATE_ID -> Icons.Default.VerifiedUser
    AnnotationTool.REDACTION -> Icons.Default.VisibilityOff
    AnnotationTool.WATERMARK -> Icons.Default.BrandingWatermark
}
