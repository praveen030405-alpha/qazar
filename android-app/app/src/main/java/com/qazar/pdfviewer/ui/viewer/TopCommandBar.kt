package com.qazar.pdfviewer.ui.viewer

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.theme.*

/**
 * Clean, Immersive Blended Top Command Bar for Meridian Android UI.
 * - Blends seamlessly into the system status bar with zero visible seam or border
 * - Proper status bar insets handling via Column to avoid squashing content
 * - Layout: [ Back Arrow ] [ Document Title (Google Sans) ] [ Bookmark ] [ Search SVG ] [ Overflow ]
 * - Search expands inline into a sleek search pill right in the header with hit count & navigation
 * - Three-dots menu includes Share Document and Bookmarks list
 */
@Composable
fun TopCommandBar(
    documentTitle: String,
    currentPage: Int = 1,
    totalPages: Int = 1,
    isSearchExpanded: Boolean,
    searchQuery: String,
    searchResultCount: Int = 0,
    currentSearchMatchIndex: Int = 0,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onPrevMatch: () -> Unit = {},
    onNextMatch: () -> Unit = {},
    onToggleSearchDrawer: () -> Unit = {},
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    isLoading: Boolean = false,
    isAnnotateActive: Boolean = false,
    viewingMode: ViewingMode = ViewingMode.DEFAULT,
    layoutMode: LayoutMode = LayoutMode.CONTINUOUS_VERTICAL,
    onBack: () -> Unit = {},
    onShareDocument: () -> Unit = {},
    onToggleAnnotate: () -> Unit = {},
    onToggleViewingMode: () -> Unit = {},
    onOpenViewingThemes: () -> Unit = onToggleViewingMode,
    onToggleLayoutMode: () -> Unit = {},
    isImportedLocalCopy: Boolean = true,
    onSaveToLibrary: () -> Unit = {},
    onSaveToDevice: () -> Unit = {},
    onOpenDetails: () -> Unit = {},
    onOpenRename: () -> Unit = {},
    onMakeCopy: () -> Unit = {},
    onOpenRedaction: () -> Unit = {},
    onPasswordProtect: () -> Unit = {},
    onOpenSignModal: () -> Unit = {},
    onOpenAuditLog: () -> Unit = {},
    onReadAloud: (() -> Unit)? = null,
    showToolbarToggle: Boolean = false,
    isToolbarExpanded: Boolean = true,
    onToggleToolbarExpand: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(isSearchExpanded) {
        if (isSearchExpanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            keyboardController?.hide()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121212)) // Solid Charcoal Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // ... (rest of the header will follow)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = isSearchExpanded,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300, delayMillis = 90)).togetherWith(
                            fadeOut(animationSpec = tween(90))
                        ).using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(300, easing = FastOutSlowInEasing) }))
                    },
                    label = "HeaderSearchTransition",
                    modifier = Modifier.fillMaxWidth()
                ) { searchOpen ->
                    if (searchOpen) {
                        // Expanded Search Pill spanning the header (85% Backdrop Charcoal Glass)
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x30000000))
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xD9161622)) // 85% Charcoal Glass
                                .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(22.dp))
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search in document...",
                                        fontSize = 14.sp,
                                        fontFamily = GoogleSansTextFamily,
                                        color = Color(0x99FFFFFF)
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = onSearchQueryChanged,
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontFamily = GoogleSansTextFamily,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    cursorBrush = SolidColor(Color(0xFFEF4444)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester)
                                )
                            }

                            // Match Counter Badge & Up/Down jump arrows
                            if (searchQuery.isNotEmpty()) {
                                if (searchResultCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0x33EF4444),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x99EF4444)),
                                        modifier = Modifier
                                            .padding(end = 2.dp)
                                            .clickable { onToggleSearchDrawer() }
                                    ) {
                                        Text(
                                            text = "${(currentSearchMatchIndex + 1).coerceAtMost(searchResultCount)} of $searchResultCount",
                                            fontSize = 11.sp,
                                            fontFamily = GoogleSansTextFamily,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFEF4444),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = onPrevMatch,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Previous Match",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = onNextMatch,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Next Match",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = onToggleSearchDrawer,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Search Results Drawer",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "0 matches",
                                        fontSize = 11.sp,
                                        fontFamily = GoogleSansTextFamily,
                                        color = Color(0x99FFFFFF),
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onSearchQueryChanged("") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Search",
                                        tint = Color(0xCCFFFFFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            
                            // Close Search text button on the right side
                            TextButton(
                                onClick = onCloseSearch,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Close",
                                    fontFamily = GoogleSansTextFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        // Standard Header Bar: [Circular Solid Back] [Title] [Circular Solid Search] [Circular Solid Share] [Circular Solid 3-Dots]
                        val headerBtnBg = Color(0xFF0A0A0E)
                        val headerBtnBorder = Brush.linearGradient(listOf(Color(0x99EF4444), Color(0x33EF4444)))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left: Solid Back Button with Red Icon + Document Title
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x40000000))
                                        .clip(CircleShape)
                                        .background(headerBtnBg)
                                        .border(1.2.dp, headerBtnBorder, CircleShape)
                                        .appleClickEffect { onBack() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back to Home",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Text(
                                    text = documentTitle,
                                    fontSize = 16.sp,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right: [Search] [Share PDF] [3-Dots Overflow]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Search Button
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x40000000))
                                        .clip(CircleShape)
                                        .background(headerBtnBg)
                                        .border(1.2.dp, headerBtnBorder, CircleShape)
                                        .appleClickEffect { 
                                            onToggleSearch() 
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search Document",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                 // Share PDF Button (Apple iOS SF Symbol square.and.arrow.up)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x40000000))
                                        .clip(CircleShape)
                                        .background(headerBtnBg)
                                        .border(1.2.dp, headerBtnBorder, CircleShape)
                                        .appleClickEffect { 
                                            onShareDocument() 
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AppleShareIcon(
                                        modifier = Modifier.size(19.dp),
                                        tint = Color(0xFFEF4444)
                                    )
                                }

                                // Direct Read Aloud (TTS) Button
                                onReadAloud?.let { readAction ->
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x40000000))
                                            .clip(CircleShape)
                                            .background(headerBtnBg)
                                            .border(1.2.dp, headerBtnBorder, CircleShape)
                                            .appleClickEffect { readAction() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Read Aloud",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // 3-Dots Overflow Button
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x40000000))
                                        .clip(CircleShape)
                                        .background(headerBtnBg)
                                        .border(1.2.dp, headerBtnBorder, CircleShape)
                                        .appleClickEffect { isMenuExpanded = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                if (isMenuExpanded) {
                                    ViewerOptionsBottomSheet(
                                        onDismiss = { isMenuExpanded = false },
                                        onReadAloud = onReadAloud,
                                        viewingMode = viewingMode,
                                        onOpenViewingThemes = onOpenViewingThemes,
                                        layoutMode = layoutMode,
                                        onToggleLayoutMode = onToggleLayoutMode,
                                        onSaveToDevice = onSaveToDevice,
                                        isAnnotateActive = isAnnotateActive,
                                        onToggleAnnotate = onToggleAnnotate,
                                        onOpenBookmarks = onOpenBookmarks,
                                        onOpenRename = onOpenRename,
                                        onShareDocument = onShareDocument,
                                        onPasswordProtect = onPasswordProtect,
                                        onOpenDetails = onOpenDetails
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Embedded Linear Progress Bar at bottom of header column â€” zero overlap with status bar
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = PrimaryCobalt,
                    trackColor = Color.Transparent
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(1.dp))

            // Header Bottom Edge: Red Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(Color(0xFFDC143C))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerOptionsBottomSheet(
    onDismiss: () -> Unit,
    onReadAloud: (() -> Unit)?,
    viewingMode: ViewingMode,
    onOpenViewingThemes: () -> Unit,
    layoutMode: LayoutMode,
    onToggleLayoutMode: () -> Unit,
    onSaveToDevice: () -> Unit,
    isAnnotateActive: Boolean,
    onToggleAnnotate: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenRename: () -> Unit,
    onShareDocument: () -> Unit,
    onPasswordProtect: () -> Unit,
    onOpenDetails: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101018),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x40FFFFFF))
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Document Options",
                fontFamily = GoogleSansFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            onReadAloud?.let { readAction ->
                SheetActionItem("Read Aloud (Text to Speech)", Icons.Default.VolumeUp) {
                    onDismiss(); readAction()
                }
            }
            SheetActionItem("Reading Comfort: ${viewingMode.label}", Icons.Default.WbSunny) {
                onDismiss(); onOpenViewingThemes()
            }
            SheetActionItem("Layout: ${if (layoutMode == LayoutMode.CONTINUOUS_VERTICAL) "Continuous" else "Single Page"}", Icons.Default.MenuBook) {
                onDismiss(); onToggleLayoutMode()
            }
            SheetActionItem("Save to device", Icons.Default.Download) {
                onDismiss(); onSaveToDevice()
            }
            SheetActionItem(if (isAnnotateActive) "Hide Annotation Tools" else "Show Annotation Tools", Icons.Default.Edit) {
                onDismiss(); onToggleAnnotate()
            }
            SheetActionItem("Bookmarks", Icons.Default.Bookmark) {
                onDismiss(); onOpenBookmarks()
            }
            SheetActionItem("Rename Document", Icons.Default.Edit) {
                onDismiss(); onOpenRename()
            }
            SheetActionItem("Share PDF", Icons.Default.Share) {
                onDismiss(); onShareDocument()
            }
            SheetActionItem("Password Protect", Icons.Default.Lock) {
                onDismiss(); onPasswordProtect()
            }
            SheetActionItem("Document Details", Icons.Default.Info) {
                onDismiss(); onOpenDetails()
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
fun SheetActionItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0x1DEF4444)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontFamily = GoogleSansTextFamily,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE2E8F0)
        )
    }
}

@Composable
fun AppleDropdownMenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appleClickEffect(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontFamily = GoogleSansTextFamily,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
fun Modifier.appleClickEffect(
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
    onClick: () -> Unit
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.91f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "AppleBounceScale"
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.72f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 120),
        label = "AppleBounceAlpha"
    )
    return this
        .scale(scale)
        .alpha(alpha)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Authentic Apple iOS SF Symbol `square.and.arrow.up` (Share Icon).
 * Pixel-accurate vector representation with rounded box and upward chevron arrow.
 */
@Composable
fun AppleShareIcon(
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFEF4444)
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokePx = 1.8.dp.toPx()

        // 1. Open container box with rounded bottom corners
        val boxLeft = w * 0.18f
        val boxRight = w * 0.82f
        val boxTop = h * 0.38f
        val boxBottom = h * 0.92f
        val cornerRadius = 3.dp.toPx()

        val boxPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(boxLeft, boxTop + h * 0.10f)
            lineTo(boxLeft, boxBottom - cornerRadius)
            quadraticBezierTo(boxLeft, boxBottom, boxLeft + cornerRadius, boxBottom)
            lineTo(boxRight - cornerRadius, boxBottom)
            quadraticBezierTo(boxRight, boxBottom, boxRight, boxBottom - cornerRadius)
            lineTo(boxRight, boxTop + h * 0.10f)
        }
        drawPath(
            path = boxPath,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokePx,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )

        // 2. Center vertical stem of arrow
        val midX = w * 0.5f
        val arrowTop = h * 0.08f
        val arrowBottom = h * 0.62f
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(midX, arrowBottom),
            end = androidx.compose.ui.geometry.Offset(midX, arrowTop),
            strokeWidth = strokePx,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // 3. Upward pointing chevron arrow head
        val headWidth = w * 0.22f
        val headHeight = h * 0.22f
        val headPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(midX - headWidth, arrowTop + headHeight)
            lineTo(midX, arrowTop)
            lineTo(midX + headWidth, arrowTop + headHeight)
        }
        drawPath(
            path = headPath,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokePx,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
    }
}

