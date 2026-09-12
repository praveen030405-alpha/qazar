package com.qazar.pdfviewer.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.data.tools.TtsReaderManager
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.GoogleSansTextFamily

/**
 * Floating Audio HUD for Text-to-Speech (Read Aloud)
 */
@Composable
fun TtsPlaybackBar(
    ttsManager: TtsReaderManager,
    modifier: Modifier = Modifier
) {
    val state by ttsManager.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showVoicePicker by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = state.isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF2100B12), // 95% Deep Obsidian Glass
            border = BorderStroke(1.dp, Color(0xFFEF4444)), // Ultra-thin red border
            shadowElevation = 24.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Top info row: icon, sentence counter, speed pill, close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0x28DC2626)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFFFF3B56),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = "Read Aloud: Sentence ${state.currentSentenceIndex + 1} / ${state.totalSentences.coerceAtLeast(1)}",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Speed selector cycle (0.75x -> 1.0x -> 1.25x -> 1.5x -> 2.0x)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val nextSpeed = when (state.speechRate) {
                                    0.75f -> 1.0f
                                    1.0f -> 1.25f
                                    1.25f -> 1.5f
                                    1.5f -> 2.0f
                                    else -> 0.75f
                                }
                                ttsManager.setSpeed(nextSpeed)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x20EF4444),
                            border = BorderStroke(0.8.dp, Color(0x60EF4444))
                        ) {
                            Text(
                                text = "${state.speechRate}x",
                                fontSize = 11.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        // Close button
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                ttsManager.close()
                            },
                            shape = CircleShape,
                            color = Color(0x20FFFFFF),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                }

                // Sentence text preview
                if (state.currentSentence.isNotBlank()) {
                    Text(
                        text = "\"${state.currentSentence}\"",
                        fontSize = 11.5.sp,
                        fontFamily = GoogleSansTextFamily,
                        color = Color(0xFF94A3B8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous sentence
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ttsManager.previousSentence()
                        },
                        shape = CircleShape,
                        color = Color(0x15FFFFFF),
                        border = BorderStroke(0.8.dp, Color(0x30FFFFFF)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    // Main Play/Pause Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.isPlaying) {
                                ttsManager.pauseReading()
                            } else {
                                if (state.isPaused) ttsManager.resumeReading() else ttsManager.startReading()
                            }
                        },
                        shape = CircleShape,
                        color = Color(0xFFDC2626),
                        border = BorderStroke(1.dp, Color(0xFFFF5252)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(18.dp))

                    // Next sentence
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ttsManager.nextSentence()
                        },
                        shape = CircleShape,
                        color = Color(0x15FFFFFF),
                        border = BorderStroke(0.8.dp, Color(0x30FFFFFF)),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Voice selector chip — tap to open custom voice picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { showVoicePicker = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x12FFFFFF),
                        border = BorderStroke(0.6.dp, Color(0x25FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = state.selectedVoiceLabel.take(30),
                                fontSize = 10.sp,
                                fontFamily = GoogleSansTextFamily,
                                color = Color(0xFF94A3B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Change Voice",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Custom Voice Picker Dialog — obsidian & crimson themed
    if (showVoicePicker && state.isVisible) {
        Dialog(onDismissRequest = { showVoicePicker = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF14141E),
                border = BorderStroke(1.dp, Color(0x40EF4444)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(max = 440.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Custom Voices",
                                fontSize = 17.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Select a voice engine for Read Aloud",
                                fontSize = 12.sp,
                                fontFamily = GoogleSansTextFamily,
                                color = Color(0xFF64748B)
                            )
                        }
                        Surface(
                            onClick = { showVoicePicker = false },
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = Color(0x20FFFFFF),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (state.availableVoices.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 340.dp)
                        ) {
                            items(state.availableVoices) { voice ->
                                val isSelected = voice.name == state.selectedVoiceName
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        ttsManager.selectVoice(voice.name)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0x20EF4444) else Color(0x0AFFFFFF),
                                    border = BorderStroke(
                                        if (isSelected) 1.dp else 0.5.dp,
                                        if (isSelected) Color(0x60EF4444) else Color(0x15FFFFFF)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = voice.displayName,
                                                fontSize = 13.sp,
                                                fontFamily = GoogleSansFamily,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = voice.name,
                                                fontSize = 10.sp,
                                                fontFamily = GoogleSansTextFamily,
                                                color = Color(0xFF475569),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Quality tier badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (voice.qualityTier) {
                                                "Neural" -> Color(0x25EF4444)
                                                "HD" -> Color(0x2000E676)
                                                else -> Color(0x15FFFFFF)
                                            },
                                            border = BorderStroke(0.5.dp, when (voice.qualityTier) {
                                                "Neural" -> Color(0x40EF4444)
                                                "HD" -> Color(0x4000E676)
                                                else -> Color(0x20FFFFFF)
                                            })
                                        ) {
                                            Text(
                                                text = voice.qualityTier,
                                                fontSize = 9.sp,
                                                fontFamily = GoogleSansFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = when (voice.qualityTier) {
                                                    "Neural" -> Color(0xFFFF5252)
                                                    "HD" -> Color(0xFF69F0AE)
                                                    else -> Color(0xFF94A3B8)
                                                },
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No voices detected.\nCheck device TTS settings.",
                                fontSize = 12.sp,
                                fontFamily = GoogleSansTextFamily,
                                color = Color(0xFF64748B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
