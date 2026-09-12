package com.qazar.pdfviewer.ui.viewer

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.theme.*

/**
 * Gutter Radar â€” Next-Gen Tantivy Visual Heatmap Scrollbar.
 *
 * Implements:
 * - Vertical track aligned along the right edge of the viewport.
 * - Tantivy Search Hit density visualization: Glowing amber heat beads mapped accurately by page.
 * - Bookmark visualization: Cobalt blue markers on bookmarked pages.
 * - Current Viewport Indicator: Smooth sliding pill showing current position.
 * - Interactive Scrubbing: Drag along the track with magnetic snapping and haptic notch ticks.
 * - Floating Scrub Tooltip: Glassmorphic preview pill showing target page & hit count near finger.
 */
@Composable
fun GutterRadar(
    totalPages: Int,
    currentPage: Int,
    scrollFraction: Float = 0f,
    scrollFractionProvider: (() -> Float)? = null,
    searchHits: List<SearchHitModel> = emptyList(),
    bookmarks: Set<Int> = emptySet(),
    isScrolling: Boolean = false,
    onScrubToPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalPages <= 1) return

    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var scrubPage by remember { mutableStateOf(currentPage) }
    var scrubYOffset by remember { mutableStateOf(0f) }

    // Auto-fade track when idle unless dragging or scrolling
    val trackAlpha by animateFloatAsState(
        targetValue = if (isDragging || isScrolling) 1f else 0.45f,
        animationSpec = tween(durationMillis = 250),
        label = "TrackAlpha"
    )

    // Pre-calculate page distribution of Tantivy search hits
    val hitsByPage = remember(searchHits) {
        searchHits.groupingBy { it.pageIndex + 1 }.eachCount()
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(44.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        // 1. Floating Tooltip while scrubbing
        if (isDragging) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val scrubYOffsetDp = with(density) { scrubYOffset.toDp() }
            val hitCountForScrub = hitsByPage[scrubPage] ?: 0
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-42).dp, y = (scrubYOffsetDp - 12.dp).coerceAtLeast(16.dp))
                    .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Color(0x330F172A))
                    .background(Color(0xF00F172A), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$scrubPage / $totalPages",
                        fontSize = 12.sp,
                        fontFamily = GoogleSansTextFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (hitCountForScrub > 0) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEAB308), CircleShape)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "$hitCountForScrub hits",
                                fontSize = 10.sp,
                                fontFamily = GoogleSansTextFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF422006)
                            )
                        }
                    }
                }
            }
        }

        // 2. The Interactive Radar Track Canvas
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 56.dp, horizontal = 10.dp)
                .pointerInput(totalPages) {
                    detectTapGestures { offset ->
                        val trackHeight = size.height
                        if (trackHeight > 0) {
                            val fraction = (offset.y / trackHeight).coerceIn(0f, 1f)
                            val target = (fraction * totalPages).toInt().coerceIn(1, totalPages)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onScrubToPage(target)
                        }
                    }
                }
                .pointerInput(totalPages) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            scrubYOffset = offset.y
                            val trackHeight = size.height
                            if (trackHeight > 0) {
                                val fraction = (offset.y / trackHeight).coerceIn(0f, 1f)
                                scrubPage = (fraction * totalPages).toInt().coerceIn(1, totalPages)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onScrubToPage(scrubPage)
                            }
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            scrubYOffset = change.position.y
                            val trackHeight = size.height
                            if (trackHeight > 0) {
                                val fraction = (change.position.y / trackHeight).coerceIn(0f, 1f)
                                val newPage = (fraction * totalPages).toInt().coerceIn(1, totalPages)
                                if (newPage != scrubPage) {
                                    scrubPage = newPage
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onScrubToPage(newPage)
                                }
                            }
                        }
                    )
                }
        ) {
            val trackW = 4.dp.toPx()
            val trackH = size.height
            val trackX = size.width - trackW

            // Draw translucent rail track
            drawRoundRect(
                color = Color(0x220F172A).copy(alpha = 0.12f * trackAlpha),
                topLeft = Offset(trackX, 0f),
                size = Size(trackW, trackH),
                cornerRadius = CornerRadius(trackW / 2, trackW / 2)
            )

            // Draw Bookmarked Pages (Cobalt Blue Tick Marks)
            bookmarks.forEach { bmPage ->
                if (bmPage in 1..totalPages) {
                    val y = ((bmPage - 0.5f) / totalPages) * trackH
                    drawCircle(
                        color = Color(0xFF2563EB).copy(alpha = 0.9f * trackAlpha),
                        radius = 3.5.dp.toPx(),
                        center = Offset(trackX + trackW / 2, y)
                    )
                }
            }

            // Draw Tantivy Search Hit Heat Beads (Glowing Amber/Yellow)
            hitsByPage.forEach { (pageIndex, count) ->
                if (pageIndex in 1..totalPages) {
                    val y = ((pageIndex - 0.5f) / totalPages) * trackH
                    val beadRadius = when {
                        count >= 5 -> 4.5.dp.toPx()
                        count >= 2 -> 3.5.dp.toPx()
                        else -> 2.5.dp.toPx()
                    }
                    // Outer heat glow
                    drawCircle(
                        color = Color(0x66EAB308).copy(alpha = 0.4f * trackAlpha),
                        radius = beadRadius * 1.6f,
                        center = Offset(trackX + trackW / 2, y)
                    )
                    // Inner heat core
                    drawCircle(
                        color = Color(0xFFEAB308).copy(alpha = 0.95f * trackAlpha),
                        radius = beadRadius,
                        center = Offset(trackX + trackW / 2, y)
                    )
                }
            }

            // Draw Current Viewport Indicator Capsule with continuous sub-pixel sliding
            val rawFrac = scrollFractionProvider?.invoke() ?: scrollFraction
            val continuousFrac = if (isDragging) {
                ((scrubPage - 1f) / (totalPages - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            } else {
                rawFrac.coerceIn(0f, 1f)
            }
            val indicatorHeight = (trackH / totalPages).coerceIn(20.dp.toPx(), 44.dp.toPx())
            val availableTravel = (trackH - indicatorHeight).coerceAtLeast(0f)
            val indicatorTop = continuousFrac * availableTravel

            // Active sliding indicator
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFDC2626),
                        Color(0xFFF87171)
                    )
                ),
                topLeft = Offset(trackX - 1.5.dp.toPx(), indicatorTop),
                size = Size(trackW + 3.dp.toPx(), indicatorHeight),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
        }
    }
}
