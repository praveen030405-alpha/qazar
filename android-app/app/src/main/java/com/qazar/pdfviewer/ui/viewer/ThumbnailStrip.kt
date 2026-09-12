package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.data.RealPdfLoader
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.ViewingMode
import kotlinx.coroutines.launch

@Composable
fun ThumbnailStrip(
    isVisible: Boolean,
    filePath: String,
    pages: List<PdfPageModel>,
    viewingMode: ViewingMode,
    currentPageIndex: Int,
    onPageSelected: (Int) -> Unit,
    onClose: () -> Unit,
    topPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val halfScreenWidth = (configuration.screenWidthDp.dp * 0.5f).coerceAtLeast(180.dp)
    val totalWidth = halfScreenWidth + 18.dp // Half-screen panel + 18dp for close button overhang

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

        LaunchedEffect(isVisible, currentPageIndex) {
            if (isVisible && pages.isNotEmpty() && !gridState.isScrollInProgress) {
                coroutineScope.launch {
                    gridState.scrollToItem(currentPageIndex.coerceIn(0, pages.size - 1))
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Frosted charcoal glass drawer panel covering half the screen, aligned to the right
            Box(
                modifier = Modifier
                    .width(halfScreenWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .shadow(24.dp, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF140D12), Color(0xFF0D0D15), Color(0xFF08080D))
                        )
                    )
                    .drawBehind {
                        // Start (Left) border only
                        drawLine(
                            color = Color(0xFFEF4444),
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { /* Intercept clicks so outside tap dismisses */ }
                    }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header label inside panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp, bottom = 8.dp, start = 12.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pages (${pages.size})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Double Column Clearly Visible Thumbnails Grid
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 8.dp, end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(pages.size) { index ->
                            val isSelected = index == currentPageIndex

                            var bmp by remember(index, viewingMode) {
                                mutableStateOf(RealPdfLoader.getCachedThumbnail(filePath, index, viewingMode))
                            }

                            LaunchedEffect(index, viewingMode) {
                                if (bmp == null) {
                                    RealPdfLoader.requestThumbnail(filePath, index, viewingMode)
                                    // Reactive observer: check cache 3 times with increasing delay, then settle
                                    var attempts = 0
                                    while (bmp == null && attempts < 8) {
                                        kotlinx.coroutines.delay(if (attempts < 3) 50L else 200L)
                                        bmp = RealPdfLoader.getCachedThumbnail(filePath, index, viewingMode)
                                        attempts++
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.70f)
                                    .shadow(if (isSelected) 8.dp else 3.dp, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(viewingMode.swatchColor)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color(0xFFDC2626) else Color(0x33000000),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        onPageSelected(index)
                                        coroutineScope.launch {
                                            gridState.animateScrollToItem(index)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp!!.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
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

                                // Page number badge
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
            } // End of frosted glass Box

            // Close button overlay ergonomically positioned on the left edge
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
                    contentDescription = "Close Thumbnails",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
