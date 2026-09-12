package com.qazar.pdfviewer.ui.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import com.qazar.pdfviewer.ui.tools.SaveFileNameDialog
import com.qazar.pdfviewer.ui.tools.getOriginalFileName
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qazar.pdfviewer.R
import com.qazar.pdfviewer.data.RecentDocItem
import com.qazar.pdfviewer.data.RecentFilesManager
import com.qazar.pdfviewer.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class GalleryTab {
    RECENTS,
    DEVICE_FILES,
    SAMPLE_CORPUS
}

data class DevicePdfItem(
    val path: String,
    val name: String,
    val length: Long,
    val lastModified: Long
)

/**
 * Touch Bounce Modifier with physical spring response (optimized without composed overhead).
 */
@Composable
fun Modifier.huaweiTouchBounce(
    targetScale: Float = 0.975f,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "TouchScale"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
}

/**
 * Redesigned HomeScreen faithfully reproducing new-homescreen.jpg:
 * - Ambient dark crimson canvas (#0C0C12)
 * - Header: Hamburger on left edge, "Qazar - PDF Viewer" beside it (no logo), Gemini-border avatar on right
 * - Hero banner: "SIMPLE â€¢ SECURE â€¢ POWERFUL", "Your PDFs, Your Way.", glowing document cards
 * - Sleek dark search filter input
 * - Quick Action Card: "Open PDF - Tap to browse, import or scan"
 * - Recent Documents with "See All >" link
 * - Dark document cards with red icons, file size, timestamp, and 3-dots
 * - "Keep things organized" card
 * - Bottom Navigation Bar (Home, Files, Search, Settings)
 * - Floating "+" FAB on the bottom right screen
 * - Native-level dark frosted glass navigation sidebar
 */
@Composable
fun HomeScreen(
    onOpenPdf: (filePath: String, documentTitle: String) -> Unit,
    onNavigateToQScan: () -> Unit = {},
    onNavigateToResumeMaker: () -> Unit = {},
    onNavigateToToolsHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var isLoading by remember { mutableStateOf(false) }
    var shortcutProcessing by remember { mutableStateOf(false) }
    var shortcutProgress by remember { mutableStateOf(0f) }
    var shortcutStatusMsg by remember { mutableStateOf<String?>(null) }
    var activeShortcutTool by remember { mutableStateOf<com.qazar.pdfviewer.ui.tools.ToolType?>(null) }

    val libraryDocs by com.qazar.pdfviewer.data.PdfLibraryManager.getAllDocuments(context).collectAsState(initial = emptyList())
    val recents = remember(libraryDocs) {
        libraryDocs.distinctBy { it.uriOrPath }.map { 
            RecentDocItem(
                title = it.displayName,
                path = it.uriOrPath,
                sizeBytes = it.sizeBytes,
                timestamp = it.lastOpenedTimestamp
            )
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var isHeaderSearchExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(GalleryTab.RECENTS) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var showAllRecentDocuments by remember { mutableStateOf(false) }
    var quickActionDoc by remember { mutableStateOf<QuickActionDoc?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTargetDoc by remember { mutableStateOf<QuickActionDoc?>(null) }
    var renameInputName by remember { mutableStateOf("") }
    var favoriteDocPaths by remember { mutableStateOf(setOf<String>()) }

    androidx.activity.compose.BackHandler(enabled = showAboutScreen || showAllRecentDocuments) {
        if (showAboutScreen) {
            showAboutScreen = false
        } else if (showAllRecentDocuments) {
            showAllRecentDocuments = false
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            coroutineScope.launch {
                val (path, name) = copyUriToCache(context, uri)
                isLoading = false
                if (path != null) {
                    val file = File(path)
                    coroutineScope.launch {
                        com.qazar.pdfviewer.data.PdfLibraryManager.trackExternalDocument(context, path, name, file.length())
                    }
                    onOpenPdf(path, name)
                }
            }
        }
    }

    // Pending shortcut states for SaveFileNameDialog
    var pendingShortcutUri by remember { mutableStateOf<Uri?>(null) }
    var pendingShortcutTool by remember { mutableStateOf<com.qazar.pdfviewer.ui.tools.ToolType?>(null) }
    var pendingShortcutOrigName by remember { mutableStateOf("") }
    var pendingShortcutExt by remember { mutableStateOf("pdf") }
    var pendingShortcutDefaultName by remember { mutableStateOf("") }

    var pendingShortcutMergeUris by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingShortcutMergeFirstDocName by remember { mutableStateOf("") }

    // Direct single PDF picker for Quick Tools (Word, Excel, PPT, Compress, Protect)
    val singleShortcutPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val tool = activeShortcutTool ?: return@rememberLauncherForActivityResult
            val origName = getOriginalFileName(context, uri)
            val ext = when (tool) {
                com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_WORD -> "docx"
                com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_EXCEL -> "xlsx"
                com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_PPT -> "pptx"
                com.qazar.pdfviewer.ui.tools.ToolType.COMPRESS_PDF -> "pdf"
                com.qazar.pdfviewer.ui.tools.ToolType.PROTECT_PDF -> "qzar"
                else -> "pdf"
            }
            val defaultName = when (tool) {
                com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_WORD -> "${origName}_converted"
                com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_EXCEL -> "${origName}_sheets"
                com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_PPT -> "${origName}_slides"
                com.qazar.pdfviewer.ui.tools.ToolType.COMPRESS_PDF -> "${origName}_compressed"
                com.qazar.pdfviewer.ui.tools.ToolType.PROTECT_PDF -> "${origName}_vault"
                else -> "${origName}_out"
            }
            pendingShortcutUri = uri
            pendingShortcutTool = tool
            pendingShortcutOrigName = origName
            pendingShortcutExt = ext
            pendingShortcutDefaultName = defaultName
        }
    }

    var previewReorderShortcutUris by remember { mutableStateOf<List<Uri>?>(null) }

    // Direct multi PDF picker for Quick Tools (Merge)
    val multiShortcutPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.size >= 2) {
            previewReorderShortcutUris = uris
        } else if (uris.isNotEmpty()) {
            shortcutStatusMsg = "Please select at least 2 PDF files to merge."
        }
    }

    // Page Reorder & Preview Dialog before Quick Merge
    previewReorderShortcutUris?.let { selectedList ->
        com.qazar.pdfviewer.ui.tools.PageReorderPreviewDialog(
            initialUris = selectedList,
            onDismiss = { previewReorderShortcutUris = null },
            onConfirm = { reorderedList ->
                previewReorderShortcutUris = null
                if (reorderedList.size >= 2) {
                    val firstDocName = getOriginalFileName(context, reorderedList[0])
                    pendingShortcutMergeFirstDocName = firstDocName
                    pendingShortcutMergeUris = reorderedList
                }
            }
        )
    }

    var completedShortcutFile by remember { mutableStateOf<File?>(null) }

    // Universal Working Open and Share Dialog for HomeScreen Quick Tools
    completedShortcutFile?.let { file ->
        com.qazar.pdfviewer.ui.tools.ConversionResultDialog(
            file = file,
            onDismiss = { completedShortcutFile = null },
            onOpenInViewer = { pdfFile ->
                completedShortcutFile = null
                onOpenPdf(pdfFile.absolutePath, pdfFile.name)
            }
        )
    }

    // Save & Name Dialog for Quick Single Tools
    if (pendingShortcutUri != null && pendingShortcutTool != null) {
        val tool = pendingShortcutTool!!
        val uri = pendingShortcutUri!!
        SaveFileNameDialog(
            initialName = pendingShortcutDefaultName,
            targetExtension = pendingShortcutExt,
            sourceOriginalName = pendingShortcutOrigName,
            destinationFolder = "Internal Storage > Qazar > Exports",
            onDismiss = {
                pendingShortcutUri = null
                pendingShortcutTool = null
            },
            onConfirm = { enteredName ->
                pendingShortcutUri = null
                pendingShortcutTool = null
                shortcutProcessing = true
                shortcutProgress = 0f
                shortcutStatusMsg = "Executing ${tool.title}..."
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val tempIn = File(context.cacheDir, "quick_in_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            tempIn.outputStream().use { output -> input.copyTo(output) }
                        }
                        val baseName = enteredName.trim().ifBlank { "Converted_Document" }

                        when (tool) {
                            com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_WORD -> {
                                val finalName = if (baseName.endsWith(".docx", true)) baseName else "$baseName.docx"
                                val out = com.qazar.pdfviewer.data.QazarStorageManager.getExportsDir().resolve(finalName)
                                val res = com.qazar.pdfviewer.data.converter.PdfToWordConverter.convert(context, tempIn, out) { p -> shortcutProgress = p }
                                withContext(Dispatchers.Main) {
                                    shortcutProcessing = false
                                    if (res.isSuccess && out.exists()) {
                                        com.qazar.pdfviewer.data.QazarStorageManager.indexFile(context, out)
                                        shortcutStatusMsg = "Converted to Word: Exports/${out.name}"
                                        completedShortcutFile = out
                                    } else shortcutStatusMsg = "Word conversion failed."
                                }
                            }
                            com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_EXCEL -> {
                                val finalName = if (baseName.endsWith(".xlsx", true)) baseName else "$baseName.xlsx"
                                val out = com.qazar.pdfviewer.data.QazarStorageManager.getExportsDir().resolve(finalName)
                                val res = com.qazar.pdfviewer.data.converter.PdfToExcelConverter.convert(context, tempIn, out) { p -> shortcutProgress = p }
                                withContext(Dispatchers.Main) {
                                    shortcutProcessing = false
                                    if (res.isSuccess && out.exists()) {
                                        com.qazar.pdfviewer.data.QazarStorageManager.indexFile(context, out)
                                        shortcutStatusMsg = "Extracted to Excel: Exports/${out.name}"
                                        completedShortcutFile = out
                                    } else shortcutStatusMsg = "Excel extraction failed."
                                }
                            }
                            com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_PPT -> {
                                val finalName = if (baseName.endsWith(".pptx", true)) baseName else "$baseName.pptx"
                                val out = com.qazar.pdfviewer.data.QazarStorageManager.getExportsDir().resolve(finalName)
                                val res = com.qazar.pdfviewer.data.converter.PdfToPptConverter.convert(context, tempIn, out) { p -> shortcutProgress = p }
                                withContext(Dispatchers.Main) {
                                    shortcutProcessing = false
                                    if (res.isSuccess && out.exists()) {
                                        com.qazar.pdfviewer.data.QazarStorageManager.indexFile(context, out)
                                        shortcutStatusMsg = "Exported to PPT: Exports/${out.name}"
                                        completedShortcutFile = out
                                    } else shortcutStatusMsg = "PowerPoint conversion failed."
                                }
                            }
                            com.qazar.pdfviewer.ui.tools.ToolType.COMPRESS_PDF -> {
                                val finalName = if (baseName.endsWith(".pdf", true)) baseName else "$baseName.pdf"
                                val out = com.qazar.pdfviewer.data.QazarStorageManager.getExportsDir().resolve(finalName)
                                val res = com.qazar.pdfviewer.data.tools.PdfCompressorEngine.compressPdf(context, tempIn, out, com.qazar.pdfviewer.data.tools.CompressionProfile.RECOMMENDED) { p -> shortcutProgress = p }
                                withContext(Dispatchers.Main) {
                                    shortcutProcessing = false
                                    if (res.isSuccess && out.exists()) {
                                        val compRes = res.getOrThrow()
                                        com.qazar.pdfviewer.data.QazarStorageManager.indexFile(context, out)
                                        val df = java.text.DecimalFormat("#.##")
                                        val origMb = df.format(compRes.originalSizeBytes / (1024.0 * 1024.0))
                                        val compMb = df.format(compRes.compressedSizeBytes / (1024.0 * 1024.0))
                                        shortcutStatusMsg = "Compressed (${compRes.savingsPercent}% saved: ${origMb}MB -> ${compMb}MB in Exports)"
                                        completedShortcutFile = out
                                    } else shortcutStatusMsg = "Compression failed."
                                }
                            }
                            com.qazar.pdfviewer.ui.tools.ToolType.PROTECT_PDF -> {
                                val finalName = if (baseName.endsWith(".qzar", true)) baseName else "$baseName.qzar"
                                val out = com.qazar.pdfviewer.data.QazarStorageManager.getExportsDir().resolve(finalName)
                                val res = com.qazar.pdfviewer.data.tools.PdfProtectionEngine.encryptPdf(context, tempIn, out, "qazar123")
                                withContext(Dispatchers.Main) {
                                    shortcutProcessing = false
                                    if (res.isSuccess && out.exists()) {
                                        com.qazar.pdfviewer.data.QazarStorageManager.indexFile(context, out)
                                        shortcutStatusMsg = "Protected with AES-256 Vault: Exports/${out.name}"
                                        completedShortcutFile = out
                                    } else shortcutStatusMsg = "Vault protection failed."
                                }
                            }
                            else -> {
                                withContext(Dispatchers.Main) {
                                    shortcutProcessing = false
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            shortcutProcessing = false
                            shortcutStatusMsg = "Error: ${e.localizedMessage}"
                        }
                    }
                }
            }
        )
    }

    // Save & Name Dialog for Quick Merge
    if (pendingShortcutMergeUris != null) {
        val uris = pendingShortcutMergeUris!!
        SaveFileNameDialog(
            initialName = "${pendingShortcutMergeFirstDocName}_merged",
            targetExtension = "pdf",
            sourceOriginalName = pendingShortcutMergeFirstDocName,
            destinationFolder = "Internal Storage > Qazar > Exports",
            onDismiss = { pendingShortcutMergeUris = null },
            onConfirm = { enteredName ->
                pendingShortcutMergeUris = null
                shortcutProcessing = true
                shortcutProgress = 0f
                shortcutStatusMsg = "Merging ${uris.size} PDFs..."
                coroutineScope.launch(Dispatchers.IO) {
                    val inputFiles = uris.mapNotNull { uri ->
                        val temp = File(context.cacheDir, "merge_in_${System.currentTimeMillis()}_${File(uri.path ?: "").name}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        }
                        if (temp.exists() && temp.length() > 0) temp else null
                    }
                    val finalFileName = if (enteredName.endsWith(".pdf", ignoreCase = true)) enteredName else "$enteredName.pdf"
                    val targetFile = com.qazar.pdfviewer.data.QazarStorageManager.getExportsDir().resolve(finalFileName)
                    val res = com.qazar.pdfviewer.data.tools.PdfMergerEngine.mergePdfs(context, inputFiles, targetFile) { p ->
                        shortcutProgress = p
                    }
                    withContext(Dispatchers.Main) {
                        shortcutProcessing = false
                        if (res.isSuccess && targetFile.exists()) {
                            com.qazar.pdfviewer.data.QazarStorageManager.indexFile(context, targetFile)
                            shortcutStatusMsg = "Merged successfully into Exports/${targetFile.name}"
                            completedShortcutFile = targetFile
                        } else {
                            shortcutStatusMsg = "Merge failed or stream corrupt."
                        }
                    }
                }
            }
        )
    }

    // Discover real PDFs from device Downloads asynchronously
    var availablePdfs by remember { mutableStateOf<List<DevicePdfItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = mutableListOf<File>()
            val downloadDirs = listOf(
                File("/storage/emulated/0/Download"),
                File("/sdcard/Download"),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            )
            for (dir in downloadDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val pdfs = dir.listFiles { _, name -> name.endsWith(".pdf", ignoreCase = true) }
                    if (pdfs != null) {
                        for (f in pdfs) {
                            if (list.none { it.name.equals(f.name, ignoreCase = true) }) {
                                list.add(f)
                            }
                        }
                    }
                }
            }
            val cacheFiles = context.cacheDir.listFiles { _, name ->
                name.endsWith(".pdf", ignoreCase = true)
            }
            if (cacheFiles != null) {
                for (f in cacheFiles) {
                    if (list.none { it.name.equals(f.name, ignoreCase = true) }) {
                        list.add(f)
                    }
                }
            }
            
            // MAP TO DATA CLASS ON THE IO THREAD TO PREVENT SCROLL LAG!
            availablePdfs = list.map { file ->
                DevicePdfItem(
                    path = file.absolutePath,
                    name = file.name,
                    length = file.length(),
                    lastModified = file.lastModified()
                )
            }
        }
    }

    val filteredRecents = remember(recents, searchQuery) {
        val list = if (searchQuery.isBlank()) recents
        else recents.filter { it.title.contains(searchQuery, ignoreCase = true) }
        list.distinctBy { it.path }
    }

    val filteredDevicePdfs = remember(availablePdfs, searchQuery) {
        val list = if (searchQuery.isBlank()) availablePdfs
        else availablePdfs.filter { it.name.contains(searchQuery, ignoreCase = true) }
        list.distinctBy { it.path }
    }

    val listState = rememberLazyListState()

    // Red Accent Sweep Gradient for Profile Avatar Border
    val redBorderBrush = remember {
        Brush.sweepGradient(
            colors = listOf(
                Color(0xFFEF4444),
                Color(0xFFDC2626),
                Color(0xFF991B1B),
                Color(0xFFEF4444)
            )
        )
    }

    // Native High-End Navigation Sidebar (Seamlessly Integrated)
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xF70C0C14), // Deep frosted glass aesthetic
                drawerShape = RoundedCornerShape(0.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
            ) {
                // Premium User Profile Header with Aura
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0x33DC2626), Color.Transparent),
                                startY = 0f,
                                endY = 400f
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, redBorderBrush, CircleShape)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B1B26)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "PK",
                                    fontSize = 18.sp,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                            }

                            Column {
                                Text(
                                    text = "Praveen Kumar",
                                    fontSize = 18.sp,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "praveen@local.device",
                                    fontSize = 13.sp,
                                    fontFamily = GoogleSansTextFamily,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0x0CFFFFFF), thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // CATEGORY: MY DOCUMENTS
                    item {
                        Text(
                            text = "MY DOCUMENTS",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 8.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = null, tint = if (selectedTab == GalleryTab.RECENTS) Color(0xFFEF4444) else Color(0xFF94A3B8), modifier = Modifier.size(22.dp)) },
                            label = { Text("Recent Documents", fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                            selected = selectedTab == GalleryTab.RECENTS,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); selectedTab = GalleryTab.RECENTS; coroutineScope.launch { drawerState.close() } },
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0x22DC2626), selectedTextColor = Color.White, unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.FolderOpen, contentDescription = null, tint = if (selectedTab == GalleryTab.DEVICE_FILES) Color(0xFFEF4444) else Color(0xFF94A3B8), modifier = Modifier.size(22.dp)) },
                            label = { Text("Device Files", fontFamily = GoogleSansFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) },
                            selected = selectedTab == GalleryTab.DEVICE_FILES,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); selectedTab = GalleryTab.DEVICE_FILES; coroutineScope.launch { drawerState.close() } },
                            colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = Color(0x22DC2626), selectedTextColor = Color.White, unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }

                    // CATEGORY: TOOLS
                    item {
                        Text(
                            text = "TOOLS",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp)) },
                            label = { Text("Q-Scan Camera", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) },
                            selected = false,
                            onClick = { coroutineScope.launch { drawerState.close() }; onNavigateToQScan() },
                            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(22.dp)) },
                            label = { Text("Resume Builder", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) },
                            selected = false,
                            onClick = { coroutineScope.launch { drawerState.close() }; onNavigateToResumeMaker() },
                            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.CallMerge, contentDescription = null, tint = Color(0xFF9333EA), modifier = Modifier.size(22.dp)) },
                            label = { Text("Merge PDFs", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) },
                            selected = false,
                            onClick = { coroutineScope.launch { drawerState.close() }; multiShortcutPicker.launch("application/pdf") },
                            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Compress, contentDescription = null, tint = Color(0xFF0D9488), modifier = Modifier.size(22.dp)) },
                            label = { Text("Compress Size", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) },
                            selected = false,
                            onClick = { 
                                coroutineScope.launch { drawerState.close() }
                                activeShortcutTool = com.qazar.pdfviewer.ui.tools.ToolType.COMPRESS_PDF
                                singleShortcutPicker.launch("application/pdf")
                            },
                            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Apps, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(22.dp)) },
                            label = { Text("All Tools Suite", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) },
                            selected = false,
                            onClick = { coroutineScope.launch { drawerState.close() }; onNavigateToToolsHub() },
                            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }

                    // CATEGORY: SETTINGS
                    item {
                        Text(
                            text = "PREFERENCES",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(start = 12.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }

                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(22.dp)) },
                            label = { Text("Settings", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) },
                            selected = false,
                            onClick = { coroutineScope.launch { drawerState.close() }; showSettingsDialog = true },
                            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }
                    
                    item {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(22.dp)) },
                            label = { Text("About Qazar", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp) },
                            selected = false,
                            onClick = { coroutineScope.launch { drawerState.close() }; showAboutScreen = true },
                            colors = NavigationDrawerItemDefaults.colors(unselectedTextColor = Color(0xFFE2E8F0)),
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0x18FFFFFF), thickness = 1.dp)

                // Slim Minimal Classic Version Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Qazar - PDF Viewer",
                            fontSize = 13.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "v2.4.0 â€¢ Fast, Fluid & Private",
                            fontSize = 11.sp,
                            fontFamily = GoogleSansTextFamily,
                            color = Color(0xFF64748B)
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    ) {
        // Ambient subtle top-right crimson radial glow cached as a static brush
        val ambientCrimsonGlow = remember {
            Brush.radialGradient(
                colors = listOf(Color(0x25DC2626), Color(0x00DC2626)),
                center = androidx.compose.ui.geometry.Offset(900f, 200f),
                radius = 800f
            )
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.TopCenter
        ) {

            // Main Scrollable Home Canvas
            var isSearchFocused by remember { mutableStateOf(false) }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(
                    top = 60.dp, // Comfortably below transparent header
                    bottom = 96.dp // Above bottom navigation bar
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // 1. Hero Section matching new-homescreen.jpg (hidden while actively typing to bring search results to top)
                item(key = "hero_section") {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isSearchFocused && searchQuery.isEmpty(),
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            Text(
                                text = "SIMPLE  â€¢  SECURE  â€¢  POWERFUL",
                                fontSize = 11.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.8.sp,
                                color = Color(0xFFEF4444)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Your PDFs, Your Way.",
                                        fontSize = 28.sp,
                                        fontFamily = GoogleSansFamily,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Open, read and manage your documents with a clean, focused experience.",
                                        fontSize = 13.sp,
                                        fontFamily = GoogleSansTextFamily,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Tools Horizontal Rack (Adobe Acrobat style with top-notch material icons & aligned colors)
                item(key = "quick_tools_rack") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "QUICK TOOLS",
                                fontSize = 11.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.6.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "All Tools >",
                                fontSize = 12.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFEF4444),
                                modifier = Modifier.clickable { onNavigateToToolsHub() }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {

                            // Q-Scan (Ruby Red)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.DocumentScanner,
                                    label = "Q-Scan",
                                    color = Color(0xFFEF4444),
                                    onClick = onNavigateToQScan
                                )
                            }
                            // PDF to Word (Microsoft Blue)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.Description,
                                    label = "To Word",
                                    color = Color(0xFF2563EB),
                                    onClick = {
                                        activeShortcutTool = com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_WORD
                                        singleShortcutPicker.launch("application/pdf")
                                    }
                                )
                            }
                            // PDF to Excel (Microsoft Green)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.TableChart,
                                    label = "To Excel",
                                    color = Color(0xFF16A34A),
                                    onClick = {
                                        activeShortcutTool = com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_EXCEL
                                        singleShortcutPicker.launch("application/pdf")
                                    }
                                )
                            }
                            // PDF to PPT (Microsoft Orange)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.Slideshow,
                                    label = "To PPT",
                                    color = Color(0xFFEA580C),
                                    onClick = {
                                        activeShortcutTool = com.qazar.pdfviewer.ui.tools.ToolType.PDF_TO_PPT
                                        singleShortcutPicker.launch("application/pdf")
                                    }
                                )
                            }
                            // Merge PDF (Deep Purple)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.CallMerge,
                                    label = "Merge",
                                    color = Color(0xFF9333EA),
                                    onClick = {
                                        multiShortcutPicker.launch("application/pdf")
                                    }
                                )
                            }
                            // Resume Maker (Amber Gold)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.Badge,
                                    label = "Resume",
                                    color = Color(0xFFD97706),
                                    onClick = onNavigateToResumeMaker
                                )
                            }
                            // Compress PDF (Teal)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.Compress,
                                    label = "Compress",
                                    color = Color(0xFF0D9488),
                                    onClick = {
                                        activeShortcutTool = com.qazar.pdfviewer.ui.tools.ToolType.COMPRESS_PDF
                                        singleShortcutPicker.launch("application/pdf")
                                    }
                                )
                            }
                            // Protect / Vault (Yellow/Gold)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.Lock,
                                    label = "Protect",
                                    color = Color(0xFFCA8A04),
                                    onClick = {
                                        activeShortcutTool = com.qazar.pdfviewer.ui.tools.ToolType.PROTECT_PDF
                                        singleShortcutPicker.launch("application/pdf")
                                    }
                                )
                            }
                            // More Tools (Slate/Red)
                            item {
                                QuickToolPill(
                                    icon = Icons.Default.Apps,
                                    label = "More",
                                    color = Color(0xFFEF4444),
                                    onClick = onNavigateToToolsHub
                                )
                            }
                        }
                    }
                }



                // 3. Quick Action Card: "Open PDF" Card (hidden during active search)
                item(key = "quick_action_card") {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isSearchFocused && searchQuery.isEmpty(),
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Top),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0A0A0E))
                                .border(
                                    1.2.dp,
                                    Brush.linearGradient(listOf(Color(0x80EF4444), Color(0x20EF4444))),
                                    RoundedCornerShape(20.dp)
                                )
                                .huaweiTouchBounce(targetScale = 0.98f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    filePickerLauncher.launch(arrayOf("application/pdf"))
                                }
                                .padding(vertical = 20.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Text(
                                    text = "Open PDF",
                                    fontSize = 16.sp,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Text(
                                    text = "Tap to browse, import or scan",
                                    fontSize = 12.sp,
                                    fontFamily = GoogleSansTextFamily,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // 4. "Recent Documents" Section Header with "See All >" link
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "Recent Documents" else "Search Results (${if (selectedTab == GalleryTab.RECENTS) filteredRecents.size else filteredDevicePdfs.size})",
                            fontSize = 17.sp,
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        if (searchQuery.isEmpty()) {
                            Row(
                                modifier = Modifier.clickable {
                                    showAllRecentDocuments = true
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "See All",
                                    fontSize = 13.sp,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFEF4444)
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 5. Document Cards matching new-homescreen.jpg (starts showing immediately as you type)
                val displayList = if (selectedTab == GalleryTab.RECENTS) filteredRecents else filteredDevicePdfs
                if (displayList.isEmpty()) {
                    item {
                        EmptyGalleryState(
                            message = if (searchQuery.isNotEmpty()) "No documents matching '$searchQuery'"
                            else "No recently opened documents. Tap '+' or 'Open PDF' to begin reading."
                        )
                    }
                } else {
                    if (selectedTab == GalleryTab.RECENTS) {
                        items(
                            items = if (searchQuery.isNotEmpty()) filteredRecents else filteredRecents.take(10),
                            key = { it.path },
                            contentType = { "recent_card" }
                        ) { item ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                DarkDocCard(
                                    title = item.title,
                                    sizeBytes = item.sizeBytes,
                                    timestamp = item.timestamp,
                                    onClick = {
                                        coroutineScope.launch {
                                            com.qazar.pdfviewer.data.PdfLibraryManager.trackExternalDocument(context, item.path, item.title, item.sizeBytes)
                                        }
                                        onOpenPdf(item.path, item.title)
                                    },
                                    onMoreClick = {
                                        quickActionDoc = QuickActionDoc(
                                            title = item.title,
                                            path = item.path,
                                            sizeBytes = item.sizeBytes,
                                            timestamp = item.timestamp
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        items(
                            items = if (searchQuery.isNotEmpty()) filteredDevicePdfs else filteredDevicePdfs.take(15),
                            key = { it.path },
                            contentType = { "device_card" }
                        ) { file ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                                DarkDocCard(
                                    title = file.name,
                                    sizeBytes = file.length,
                                    timestamp = file.lastModified,
                                    onClick = {
                                        coroutineScope.launch {
                                            com.qazar.pdfviewer.data.PdfLibraryManager.trackExternalDocument(context, file.path, file.name, file.length)
                                        }
                                        onOpenPdf(file.path, file.name)
                                    },
                                    onMoreClick = {
                                        quickActionDoc = QuickActionDoc(
                                            title = file.name,
                                            path = file.path,
                                            sizeBytes = file.length,
                                            timestamp = file.lastModified
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // 6. "Keep things organized" Promotion Card matching new-homescreen.jpg (hidden when searching)
                if (searchQuery.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF0A0A0E))
                                .border(
                                    1.2.dp,
                                    Brush.linearGradient(listOf(Color(0x60EF4444), Color(0x18EF4444))),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x25DC2626)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Layers,
                                            contentDescription = null,
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Keep things organized",
                                            fontSize = 14.sp,
                                            fontFamily = GoogleSansFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Use offline search to access your important files quickly.",
                                            fontSize = 11.sp,
                                            fontFamily = GoogleSansTextFamily,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Top Header Bar: Clean 3-lines Hamburger on left, Expandable Search Pill in center, Profile Avatar on right
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
            ) {
                AnimatedContent(
                    targetState = isHeaderSearchExpanded,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    },
                    label = "HeaderSearchExpand"
                ) { expanded ->
                    if (expanded) {
                        val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                        LaunchedEffect(Unit) {
                            searchFocusRequester.requestFocus()
                        }
                        // Expanded Search Field in Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF14141E))
                                .border(
                                    1.2.dp,
                                    Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0x60EF4444))),
                                    RoundedCornerShape(22.dp)
                                )
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    isHeaderSearchExpanded = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Collapse Search",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )

                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = GoogleSansTextFamily,
                                    color = Color.White
                                ),
                                cursorBrush = SolidColor(Color(0xFFEF4444)),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search documents...",
                                            fontSize = 14.sp,
                                            fontFamily = GoogleSansTextFamily,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                    innerTextField()
                                }
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // Collapsed Header: Hamburger Button + Search Pill Button + Profile Avatar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    coroutineScope.launch { drawerState.open() }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Open Navigation Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Spacer to push items to the right
                            Spacer(modifier = Modifier.weight(1f))

                            // Search Circular Button
                            Box(
                                modifier = Modifier
                                    .padding(end = 14.dp)
                                    .size(38.dp)
                                    .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0xD0EF4444))
                                    .clip(CircleShape)
                                    .border(
                                        1.8.dp,
                                        Brush.linearGradient(listOf(Color(0x80EF4444), Color(0x20EF4444))),
                                        CircleShape
                                    )
                                    .background(Color(0xFF14141E))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        isHeaderSearchExpanded = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Round Profile Avatar with thin red gradient border
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0xD0DC2626))
                                    .clip(CircleShape)
                                    .border(1.8.dp, redBorderBrush, CircleShape)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E1E28))
                                    .huaweiTouchBounce {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showAboutScreen = true
                                    },
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
                    }
                }
            }



            // Dedicated Animated AboutScreen Overlay
            AnimatedVisibility(
                visible = showAboutScreen,
                enter = fadeIn(tween(220)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)),
                exit = fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f))
            ) {
                com.qazar.pdfviewer.ui.about.AboutScreen(
                    onBack = { showAboutScreen = false }
                )
            }

            // Dedicated Animated RecentDocumentsScreen Overlay
            AnimatedVisibility(
                visible = showAllRecentDocuments,
                enter = fadeIn(tween(220)) + slideInHorizontally(initialOffsetX = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)),
                exit = fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX = { it }, animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f))
            ) {
                RecentDocumentsScreen(
                    recents = recents,
                    onBack = { showAllRecentDocuments = false },
                    onOpenPdf = onOpenPdf,
                    onMoreClick = { doc -> quickActionDoc = doc }
                )
            }

            // Shortcut Processing Overlay (Obsidian & Crimson Modern Card)
            if (shortcutProcessing) {
                com.qazar.pdfviewer.ui.tools.ConversionProgressDialog(
                    title = "Generating Document",
                    status = shortcutStatusMsg ?: "Processing high-definition conversion...",
                    progress = shortcutProgress
                )
            }

            // SnackBar feedback for completed shortcut actions
            shortcutStatusMsg?.let { msg ->
                if (!shortcutProcessing) {
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3500)
                        shortcutStatusMsg = null
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E1E2A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            shadowElevation = 8.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = msg,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Actions Modal Sheet matching Quick Actions Modal .png
    quickActionDoc?.let { doc ->
        QuickActionsModalSheet(
            doc = doc,
            isFavorite = favoriteDocPaths.contains(doc.path),
            onDismiss = { quickActionDoc = null },
            onOpen = {
                val path = doc.path
                val title = doc.title
                quickActionDoc = null
                RecentFilesManager.addRecent(context, path, title, doc.sizeBytes)
                onOpenPdf(path, title)
            },
            onToggleFavorite = {
                favoriteDocPaths = if (favoriteDocPaths.contains(doc.path)) {
                    favoriteDocPaths - doc.path
                } else {
                    favoriteDocPaths + doc.path
                }
                quickActionDoc = null
            },
            onMoveToFolder = {
                // Copy or organize file into Downloads / Documents
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val src = File(doc.path)
                        if (src.exists()) {
                            val docsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                            if (!docsDir.exists()) docsDir.mkdirs()
                            val dest = File(docsDir, src.name)
                            src.copyTo(dest, overwrite = true)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                quickActionDoc = null
            },
            onShare = {
                quickActionDoc = null
                try {
                    val file = File(doc.path)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, doc.title)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share ${doc.title}"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            },
            onRename = {
                renameTargetDoc = doc
                renameInputName = doc.title.removeSuffix(".pdf")
                showRenameDialog = true
                quickActionDoc = null
            },
            onCompress = {
                // Make a lightweight copy in cache
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val src = File(doc.path)
                        if (src.exists()) {
                            val copyFile = File(context.cacheDir, "copy_${src.name}")
                            src.copyTo(copyFile, overwrite = true)
                            RecentFilesManager.addRecent(context, copyFile.absolutePath, copyFile.name, copyFile.length())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                quickActionDoc = null
            },
            onDelete = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                RecentFilesManager.removeRecent(context, doc.path)
                quickActionDoc = null
            }
        )
    }

    // Rename Document Dialog
    if (showRenameDialog && renameTargetDoc != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                renameTargetDoc = null
            },
            title = {
                Text(
                    text = "Rename Document",
                    fontFamily = GoogleSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a new name for this file:",
                        fontFamily = GoogleSansTextFamily,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = renameInputName,
                        onValueChange = { renameInputName = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF4444),
                            unfocusedBorderColor = Color(0x35FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = renameTargetDoc
                        if (target != null && renameInputName.isNotBlank()) {
                            val finalName = if (renameInputName.endsWith(".pdf", ignoreCase = true)) renameInputName else "$renameInputName.pdf"
                            RecentFilesManager.renameRecent(context, target.path, finalName)
                        }
                        showRenameDialog = false
                        renameTargetDoc = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Save", fontFamily = GoogleSansFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        renameTargetDoc = null
                    }
                ) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E1E2A),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Settings Modal Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFFEF4444))
                    Text(
                        text = "Qazar Settings",
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Engine & Reading Configuration",
                        fontSize = 12.sp,
                        fontFamily = GoogleSansTextFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEF4444)
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E2A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "120Hz Hardware Pipeline",
                                fontSize = 13.sp,
                                fontFamily = GoogleSansFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Pure RenderNode GPU transform with zero recomposition lag.",
                                fontSize = 11.sp,
                                fontFamily = GoogleSansTextFamily,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Text(
                        text = "App Edition: v2.4.0 Quantum Release\nOffline-first architecture with zero external telemetry.",
                        fontSize = 12.sp,
                        fontFamily = GoogleSansTextFamily,
                        color = Color(0xFF94A3B8)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done", color = Color.White)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF161622)
        )
    }
}



@Composable
internal fun DarkDocCard(
    title: String,
    sizeBytes: Long,
    timestamp: Long,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {}
) {
    val cardBorderBrush = remember {
        Brush.linearGradient(listOf(Color(0x55EF4444), Color(0x15EF4444)))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0A0A0E), // Solid obsidian matching Header Theme
        border = androidx.compose.foundation.BorderStroke(1.2.dp, cardBorderBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .huaweiTouchBounce(targetScale = 0.98f, onClick = onClick)
            ) {
                // Red Themed Document Icon Container
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF14141C))
                        .border(1.dp, Color(0x50EF4444), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                val sizeStr = remember(sizeBytes) {
                    val sizeKb = (sizeBytes / 1024).coerceAtLeast(1)
                    if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
                }
                val relativeTime = remember(timestamp) { formatRelativeTime(timestamp) }

                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$sizeStr â€¢ $relativeTime",
                        fontSize = 11.sp,
                        fontFamily = GoogleSansTextFamily,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

data class QuickActionDoc(
    val title: String,
    val path: String,
    val sizeBytes: Long,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionsModalSheet(
    doc: QuickActionDoc,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveToFolder: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onCompress: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161622),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0x40FFFFFF))
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: Preview Card matching Quick Actions Modal .png
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x28DC2626))
                        .border(1.dp, Color(0x35EF4444), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title,
                        fontSize = 15.sp,
                        fontFamily = GoogleSansFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sizeKb = (doc.sizeBytes / 1024).coerceAtLeast(1)
                    val sizeStr = if (sizeKb > 1024) "${sizeKb / 1024} MB" else "$sizeKb KB"
                    val relativeTime = formatRelativeTime(doc.timestamp)
                    Text(
                        text = "$sizeStr â€¢ $relativeTime",
                        fontSize = 12.sp,
                        fontFamily = GoogleSansTextFamily,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            HorizontalDivider(color = Color(0x18FFFFFF))

            // Action Items
            QuickActionItem(
                icon = Icons.Default.FolderOpen,
                label = "Open",
                onClick = onOpen
            )
            QuickActionItem(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                onClick = onToggleFavorite
            )
            QuickActionItem(
                icon = Icons.Default.Folder,
                label = "Move to Folder",
                onClick = onMoveToFolder
            )
            QuickActionItem(
                icon = Icons.Default.Share,
                label = "Share",
                onClick = onShare
            )
            QuickActionItem(
                icon = Icons.Default.Edit,
                label = "Rename",
                onClick = onRename
            )
            QuickActionItem(
                icon = Icons.Default.ContentCopy,
                label = "Make a Copy / Compress",
                onClick = onCompress
            )
            QuickActionItem(
                icon = Icons.Default.DeleteOutline,
                label = "Delete",
                tint = Color(0xFFEF4444),
                onClick = onDelete
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontFamily = GoogleSansFamily,
            fontWeight = FontWeight.Medium,
            color = tint
        )
    }
}

@Composable
internal fun EmptyGalleryState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = message,
                fontSize = 13.sp,
                fontFamily = GoogleSansTextFamily,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Recently"
    val diffMs = System.currentTimeMillis() - timestamp
    val seconds = (diffMs / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Just now"
        minutes == 1L -> "1 min ago"
        minutes < 60 -> "$minutes min ago"
        hours == 1L -> "1 hour ago"
        hours < 24 -> "$hours hours ago"
        days == 1L -> "1 day ago"
        else -> "$days days ago"
    }
}

internal suspend fun copyUriToCache(context: Context, uri: Uri): Pair<String?, String> = withContext(Dispatchers.IO) {
    var displayName = "document.pdf"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                displayName = cursor.getString(nameIndex) ?: "document.pdf"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val cacheFile = File(context.cacheDir, displayName)
    try {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
            java.io.FileInputStream(pfd.fileDescriptor).use { fis ->
                FileOutputStream(cacheFile).use { fos ->
                    val inChannel = fis.channel
                    val outChannel = fos.channel
                    inChannel.transferTo(0, inChannel.size(), outChannel)
                }
            }
            pfd.close()
            Pair(cacheFile.absolutePath, displayName)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    val buffer = ByteArray(512 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            Pair(cacheFile.absolutePath, displayName)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Pair(null, displayName)
    }
}

@Composable
private fun QuickToolPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF13131A))
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0.2f))
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = GoogleSansTextFamily,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE2E8F0),
            maxLines = 1
        )
    }
}
