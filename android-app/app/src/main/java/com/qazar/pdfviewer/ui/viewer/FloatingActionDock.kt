package com.qazar.pdfviewer.ui.viewer

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.theme.*

/**
 * Dock Display State for Liquid Motion.
 */
enum class DockMode {
    EXPANDED,            // Full controls (zoom, pager, fit)
    COMPACT_SPEEDOMETER, // Fast fling mode: minimal pill showing page and scroll velocity
    MINIMAL_READING      // Dimmed minimal reading chip
}

/**
 * Next-Generation "Morphing Island" Floating Action Dock for Meridian.
 *
 * Features:
 * - Liquid Material Motion: Smooth morphing via animateContentSize & spring physics.
 * - Velocity & Fling Responsiveness: Shrinks during rapid flings to avoid visual obstruction.
 * - Hardware Glassmorphism: API 31+ RenderEffect backdrop blur with elegant frosted border.
 * - Tactile Micro-Haptics: Haptic click feedback on page changes, zoom steps, and fit buttons.
 * - Interactive Scrub Pager: Clickable page badge with direct dialog launcher.
 */
@Composable
fun FloatingActionDock(
    currentPage: Int,
    totalPages: Int,
    scaleProvider: () -> Float = { 1.0f },
    displayZoomPercentProvider: () -> Int = { 100 },
    scrollVelocity: Float = 0f,
    isFlinging: Boolean = false,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitWidth: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onClickPageIndicator: () -> Unit = {},
    showArrows: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val dockBgBrush = Brush.linearGradient(listOf(Color(0xFF2A0000), Color(0xFF000000)))
    val dockBorderBrush = Brush.linearGradient(listOf(Color(0x80EF4444), Color(0x1AEF4444)))
    val dockShadowSpot = Color(0x33EF4444)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArrows) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = dockShadowSpot)
                    .clip(CircleShape)
                    .background(dockBgBrush)
                    .border(1.dp, dockBorderBrush, CircleShape)
                    .appleClickEffect {
                        if (currentPage > 1) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPrevPage()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous Page",
                    tint = if (currentPage > 1) Color(0xFFEF4444) else Color(0x50EF4444),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Center Pills Group: Page Pill
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 2. Page Number Pill (Page 1 / 5 with Red current page number)
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(36.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp), spotColor = dockShadowSpot)
                    .clip(RoundedCornerShape(18.dp))
                    .background(dockBgBrush)
                    .border(1.dp, dockBorderBrush, RoundedCornerShape(18.dp))
                    .appleClickEffect {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClickPageIndicator()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Page ",
                        fontSize = 13.sp,
                        fontFamily = GoogleSansTextFamily,
                        color = Color(0xFFEF4444),
                        maxLines = 1
                    )
                    Text(
                        text = "$currentPage",
                        fontSize = 13.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444),
                        maxLines = 1
                    )
                    Text(
                        text = "/$totalPages",
                        fontSize = 13.sp,
                        fontFamily = GoogleSansTextFamily,
                        color = Color(0xFFEF4444),
                        maxLines = 1
                    )
                }
            }
        }

        // 4. Right Side Arrow Button (>) on right bottom corner
        if (showArrows) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, spotColor = dockShadowSpot)
                    .clip(CircleShape)
                    .background(dockBgBrush)
                    .border(1.dp, dockBorderBrush, CircleShape)
                    .appleClickEffect {
                        if (currentPage < totalPages) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNextPage()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next Page",
                    tint = if (currentPage < totalPages) Color(0xFFEF4444) else Color(0x50EF4444),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
