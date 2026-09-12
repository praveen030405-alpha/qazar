package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.qazar.pdfviewer.theme.*

/**
 * Next-Generation Reading Comfort & Themes Bottom Sheet for Meridian.
 *
 * Implements linear-light substrate transforms, circadian melatonin protection,
 * OLED zero-backlight dark mode, and anti-glare Carta paperwhite emulation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewingComfortModalSheet(
    currentMode: ViewingMode,
    onSelectMode: (ViewingMode) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161622),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0x40FFFFFF))
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row matching Home Screen aesthetic
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x28DC2626))
                        .border(1.dp, Color(0x40EF4444), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveRedEye,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reading Comfort & Themes",
                        fontSize = 18.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Linear substrates â€¢ Circadian blue reduction â€¢ OLED dark",
                        fontSize = 12.sp,
                        fontFamily = GoogleSansTextFamily,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            HorizontalDivider(color = Color(0x18FFFFFF), modifier = Modifier.padding(vertical = 4.dp))

            // 5 Flagship Comfort Reading Themes
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ViewingMode.values().forEach { mode ->
                    val isSelected = (mode == currentMode)
                    val itemFraction = if (isLandscape) 0.48f else 1f

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectMode(mode)
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) Color(0xFF221626) else Color(0xFF1A1A24),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFFEF4444) else Color(0x18FFFFFF)
                        ),
                        shadowElevation = if (isSelected) 8.dp else 0.dp,
                        modifier = Modifier.fillMaxWidth(itemFraction)
                    ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Mini A4 Document Swatch Preview
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(52.dp)
                                .shadow(4.dp, RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(mode.swatchColor)
                                .border(
                                    width = 1.dp,
                                    color = if (mode == ViewingMode.NIGHT) Color(0x40EF4444) else Color(0x20000000),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(3.dp)
                                        .background(mode.textColor.copy(alpha = 0.85f), RoundedCornerShape(1.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(3.dp)
                                        .background(mode.textColor.copy(alpha = 0.70f), RoundedCornerShape(1.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.85f)
                                        .height(3.dp)
                                        .background(mode.textColor.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(3.dp)
                                        .background(mode.textColor.copy(alpha = 0.40f), RoundedCornerShape(1.dp))
                                )
                            }
                        }

                        // Theme Details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.label,
                                fontSize = 15.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFFFF6B6B) else Color.White
                            )

                            Text(
                                text = mode.description,
                                fontSize = 12.sp,
                                fontFamily = GoogleSansTextFamily,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // CCT Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x20FFFFFF))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = mode.cct,
                                        fontSize = 10.sp,
                                        fontFamily = GoogleSansTextFamily,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }

                                // Blue Reduction Badge
                                if (mode.blueReduction != "0%") {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (mode == ViewingMode.NIGHT) Color(0x28DC2626) else Color(0x2810B981))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "-${mode.blueReduction} Blue",
                                            fontSize = 10.sp,
                                            fontFamily = GoogleSansTextFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = if (mode == ViewingMode.NIGHT) Color(0xFFEF4444) else Color(0xFF10B981)
                                        )
                                    }
                                }
                            }
                        }

                        // Selection Indicator
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFFEF4444) else Color(0x18FFFFFF))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFFEF4444) else Color(0x35FFFFFF),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
