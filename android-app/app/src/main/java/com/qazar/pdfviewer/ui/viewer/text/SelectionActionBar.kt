package com.qazar.pdfviewer.ui.viewer.text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BarBackground = Color(0xF814141E)
private val BarBorder = Color(0x35FFFFFF)
private val TextWhite = Color(0xFFF4F4F5)
private val TextMuted = Color(0xFF94A3B8)
private val RedAccent = Color(0xFFEF4444)
private val DividerColor = Color(0x28FFFFFF)
private val DropdownBg = Color(0xFF1E1E2C)

/**
 * Enterprise-grade floating contextual action bar.
 * Presents Copy and Select All as the primary visible actions,
 * with Highlight, Share, Search, and Define nested inside the 3-dot overflow menu.
 */
@Composable
fun SelectionActionBar(
    onCopy: () -> Unit,
    onSelectAll: () -> Unit,
    onHighlight: () -> Unit,
    onShare: () -> Unit,
    onSearchInDoc: () -> Unit,
    onDefine: () -> Unit,
    onDismiss: () -> Unit,
    onReadAloud: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .wrapContentSize(),
        shape = RoundedCornerShape(18.dp),
        color = BarBackground,
        border = BorderStroke(1.dp, BarBorder),
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. Primary Action: Copy
            PrimaryBarItem(
                icon = Icons.Default.ContentCopy,
                label = "Copy",
                onClick = onCopy
            )

            // Divider
            VerticalBarDivider()

            // 2. Primary Action: Select all
            PrimaryBarItem(
                icon = Icons.Default.SelectAll,
                label = "Select all",
                onClick = onSelectAll
            )

            // Divider
            VerticalBarDivider()

            // 3. Overflow Menu (3-dot)
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { isMenuExpanded = !isMenuExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = TextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                    modifier = Modifier
                        .background(DropdownBg)
                        .border(1.dp, BarBorder, RoundedCornerShape(12.dp))
                ) {
                    onReadAloud?.let { readAction ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Read Aloud",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = RedAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                isMenuExpanded = false
                                readAction()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Highlight",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = RedAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onHighlight()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Share",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onShare()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Search in Doc",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onSearchInDoc()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Define",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = TextWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            isMenuExpanded = false
                            onDefine()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryBarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextWhite,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VerticalBarDivider() {
    Box(
        modifier = Modifier
            .height(18.dp)
            .width(1.dp)
            .background(DividerColor)
    )
}
