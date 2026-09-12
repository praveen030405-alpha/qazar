package com.qazar.pdfviewer.ui.about

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.R
import com.qazar.pdfviewer.theme.*
import com.qazar.pdfviewer.ui.home.huaweiTouchBounce

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    val redBorderBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                Color(0xFFEF4444),
                Color(0xFFDC2626),
                Color(0xFFB91C1C),
                Color(0xFFEF4444)
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C12))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Native Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Back Button in Circular Charcoal Glass with Red Icon
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x40000000))
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A24))
                            .border(1.dp, Color(0x25FFFFFF), CircleShape)
                            .huaweiTouchBounce {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "About Qazar",
                        fontSize = 18.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Profile Avatar on Right with Red Outline
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, redBorderBrush, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PK",
                        fontSize = 13.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEF4444)
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Hero Card
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF14141E),
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, Color(0x1EFFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val logoShape = RoundedCornerShape(20.dp)
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .shadow(elevation = 12.dp, shape = logoShape, spotColor = Color(0x60DC2626))
                                .clip(logoShape)
                                .background(Color(0xFF1C1C28))
                                .border(1.dp, Color(0x30EF4444), logoShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_qazar_logo),
                                contentDescription = "Qazar Logo",
                                modifier = Modifier.fillMaxSize().clip(logoShape)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Qazar - PDF Viewer",
                            fontSize = 22.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Fast, Fluid & Private",
                            fontSize = 14.sp,
                            fontFamily = GoogleSansTextFamily,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0x24DC2626),
                            border = BorderStroke(1.dp, Color(0x35EF4444)),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "v2.4.0 â€¢ Release",
                                fontSize = 11.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // 2. Feature Cards Section
                Text(
                    text = "ENGINEERING EXCELLENCE",
                    fontSize = 11.sp,
                    fontFamily = GoogleSansFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444),
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )

                AboutFeatureCard(
                    icon = Icons.Default.Speed,
                    title = "Zero-Latency Performance",
                    description = "Coupled native Rust systems kernel with sub-pixel hardware compositor for instantaneous cold opens and stutter-free navigation."
                )

                AboutFeatureCard(
                    icon = Icons.Default.Shield,
                    title = "100% Offline & Private",
                    description = "Zero external network calls, zero tracking analytics, and zero data leakage. Your confidential documents never leave your physical device."
                )

                AboutFeatureCard(
                    icon = Icons.Default.Search,
                    title = "Universal Text Indexing",
                    description = "Sub-millisecond text search index paired with continuous visual Gutter Radar for fluid document-wide exploration."
                )

                AboutFeatureCard(
                    icon = Icons.Default.AutoStories,
                    title = "Adaptive Reading Substrates",
                    description = "Chromatic adaptation supporting Natural Paper (5500K) and Archival Sepia (4500K) for eye-comfort reading."
                )

                AboutFeatureCard(
                    icon = Icons.Default.EditNote,
                    title = "Vector Ink Annotations",
                    description = "Hardware-accelerated ink canvas with bezier smoothing, highlighter blending, and per-document undo/redo."
                )

                // 3. Credits & Architecture Card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF14141E),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color(0x1EFFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Architecture & Credits",
                                fontSize = 15.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Text(
                            text = "Engineered with precision by Quantum Labs. Powered by Jetpack Compose, PDFium C++ FFI, and hardware-accelerated rendering.",
                            fontSize = 13.sp,
                            fontFamily = GoogleSansTextFamily,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )

                        HorizontalDivider(color = Color(0x18FFFFFF), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        Text(
                            text = "Â© 2026 Qazar Quantum PDF â€¢ All Rights Reserved",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansTextFamily,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AboutFeatureCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF14141E),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0x18FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .huaweiTouchBounce {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x28DC2626))
                    .border(1.dp, Color(0x35EF4444), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontFamily = GoogleSansFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    fontFamily = GoogleSansTextFamily,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
