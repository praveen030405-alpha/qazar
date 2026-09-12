package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.data.RealPdfLoader
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.ViewingMode
import kotlinx.coroutines.launch

/**
 * Slide-in Bookmark Drawer:
 * - Slides in from the right edge with a closing arrow in the middle
 * - Displays bookmarked pages as thumbnail cards with page indicators
 * - Tapping outside slides the drawer back
 * - Has no opening arrows on screen (accessed exclusively from 3-dot overflow menu)
 */
@Composable
fun BookmarkDrawer(
    isVisible: Boolean,
    filePath: String,
    pages: List<PdfPageModel>,
    bookmarkedPages: Set<Int>,
    viewingMode: ViewingMode,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    onClose: () -> Unit,
    topPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val halfScreenWidth = (configuration.screenWidthDp.dp * 0.5f).coerceAtLeast(180.dp)
    val totalWidth = halfScreenWidth + 18.dp

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(250)) + fadeOut(),
        modifier = modifier
            .padding(top = topPadding)
            .fillMaxHeight()
            .width(totalWidth)
            .navigationBarsPadding()
    ) {
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
        val coroutineScope = rememberCoroutineScope()
        val sortedBookmarks = remember(bookmarkedPages, pages.size) {
            bookmarkedPages.filter { it in pages.indices }.sorted()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Frosted charcoal glass drawer panel covering half the screen, aligned to the right
            Box(
                modifier = Modifier
                    .width(halfScreenWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .shadow(16.dp, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(Color(0xF2161622)) // 95% Deep Charcoal
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { /* Intercept clicks inside drawer so it doesn't dismiss */ }
                    }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header label inside panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 8.dp, start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bookmarks (${sortedBookmarks.size})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (sortedBookmarks.isEmpty()) {
                        // Empty state when no pages are bookmarked
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = Color(0x66EF4444),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "No Bookmarks",
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 12.sp,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Bookmark pages via header icon",
                                    color = Color(0x66FFFFFF),
                                    fontSize = 10.sp,
                                    fontFamily = GoogleSansFamily,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    } else {
                        // Double Column Grid of bookmarked pages
                        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                            state = gridState,
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 8.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(sortedBookmarks.size) { i ->
                                val index = sortedBookmarks[i]
                                val isSelected = index == currentPageIndex
                                var bmp by remember(index, viewingMode) {
                                    mutableStateOf(RealPdfLoader.getCachedThumbnail(filePath, index, viewingMode))
                                }

                                LaunchedEffect(index, viewingMode) {
                                    if (bmp == null) {
                                        RealPdfLoader.requestThumbnail(filePath, index, viewingMode)
                                        while (bmp == null) {
                                            kotlinx.coroutines.delay(100)
                                            bmp = RealPdfLoader.getCachedThumbnail(filePath, index, viewingMode)
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.70f)
                                        .shadow(if (isSelected) 8.dp else 3.dp, RoundedCornerShape(8.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) Color(0xFFDC2626) else Color(0x33000000),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            onPageSelected(index)
                                            coroutineScope.launch {
                                                gridState.animateScrollToItem(i)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp!!.asImageBitmap(),
                                            contentDescription = "Bookmarked Page ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = "${index + 1}",
                                            fontSize = 12.sp,
                                            fontFamily = GoogleSansFamily,
                                            color = Color.Gray
                                        )
                                    }

                                    // Bookmark red badge on top right
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                    )

                                    // Page number badge on bottom right
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isSelected) Color(0xFFDC2626) else Color(0x99000000))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontFamily = GoogleSansFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } // End of frosted glass Box

            // Close button overlay (Ergonomically aligned on left edge)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp)
                    .background(Color(0xFFDC2626), CircleShape)
                    .shadow(4.dp, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Close Bookmarks",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        } // End of parent Box
    }
}
