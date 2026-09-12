package com.qazar.pdfviewer.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.data.RecentDocItem
import com.qazar.pdfviewer.theme.*

@Composable
fun RecentDocumentsScreen(
    recents: List<RecentDocItem>,
    onBack: () -> Unit,
    onOpenPdf: (filePath: String, documentTitle: String) -> Unit,
    onMoreClick: (QuickActionDoc) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val filteredRecents = remember(recents, searchQuery) {
        val distinctList = recents.distinctBy { it.path }
        if (searchQuery.isBlank()) distinctList
        else distinctList.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C12))
    ) {
        // Ambient crimson background glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x22DC2626), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(800f, 150f),
                        radius = 700f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Liquid Glass Header (Back button, Title, Search Icon)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isSearchExpanded,
                    transitionSpec = {
                        (fadeIn(tween(200)) + expandHorizontally()).togetherWith(fadeOut(tween(150)) + shrinkHorizontally())
                    },
                    label = "RecentsHeaderSearch"
                ) { searchOpen ->
                    if (searchOpen) {
                        // Expanded Glass Search Pill
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .shadow(elevation = 8.dp, shape = RoundedCornerShape(23.dp), spotColor = Color(0x30000000))
                                .clip(RoundedCornerShape(23.dp))
                                .background(Color(0xD9161622)) // 85% Charcoal Glass
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(23.dp))
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    isSearchExpanded = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = GoogleSansTextFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(Color(0xFFEF4444)),
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search all recent documents...",
                                            fontSize = 14.sp,
                                            fontFamily = GoogleSansTextFamily,
                                            color = Color(0x99FFFFFF)
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xCCFFFFFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard Header: Back + Title + Search Icon
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // Glassy Back Pill
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x20000000))
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Color(0x33EF4444), Color(0x11EF4444))))
                                        .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                                        .clickable { onBack() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "Recent Documents",
                                        fontSize = 20.sp,
                                        fontFamily = GoogleSansFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${recents.size} files available",
                                        fontSize = 12.sp,
                                        fontFamily = GoogleSansTextFamily,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Glassy Search Icon Button
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x20000000))
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0x33EF4444), Color(0x11EF4444))))
                                    .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                                    .clickable { isSearchExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search Recents",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Documents List with imePadding
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredRecents.isEmpty()) {
                    item {
                        EmptyGalleryState(
                            message = if (searchQuery.isNotEmpty()) "No recent documents match '$searchQuery'"
                            else "No recently opened documents found."
                        )
                    }
                } else {
                    items(
                        items = filteredRecents,
                        key = { it.path },
                        contentType = { "recent_card" }
                    ) { item ->
                        DarkDocCard(
                            title = item.title,
                            sizeBytes = item.sizeBytes,
                            timestamp = item.timestamp,
                            onClick = {
                                onOpenPdf(item.path, item.title)
                            },
                            onMoreClick = {
                                onMoreClick(
                                    QuickActionDoc(
                                        title = item.title,
                                        path = item.path,
                                        sizeBytes = item.sizeBytes,
                                        timestamp = item.timestamp
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
