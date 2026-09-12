package com.qazar.pdfviewer.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.qazar.pdfviewer.data.QazarStorageManager
import com.qazar.pdfviewer.data.converter.PdfToExcelConverter
import com.qazar.pdfviewer.data.converter.PdfToPptConverter
import com.qazar.pdfviewer.data.converter.PdfToWordConverter
import com.qazar.pdfviewer.data.converter.WordToPdfConverter
import com.qazar.pdfviewer.data.converter.ExcelToPdfConverter
import com.qazar.pdfviewer.data.converter.PptToPdfConverter
import com.qazar.pdfviewer.data.tools.*
import com.qazar.pdfviewer.theme.GoogleSansFamily
import com.qazar.pdfviewer.theme.GoogleSansTextFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat

enum class ToolType(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val category: String
) {
    QSCAN("Q-Scan", "Camera to Multi-Page PDF", Icons.Outlined.DocumentScanner, Color(0xFFEF4444), "Create & Scan"),
    PDF_TO_WORD("PDF to Word", "Export ECMA-376 .docx document", Icons.Outlined.Description, Color(0xFF2563EB), "Convert"),
    WORD_TO_PDF("Word to PDF", "Convert .docx to standard PDF", Icons.Outlined.Description, Color(0xFF2563EB), "Convert"),
    PDF_TO_EXCEL("PDF to Excel", "Extract tables into .xlsx spreadsheet", Icons.Outlined.TableView, Color(0xFF16A34A), "Convert"),
    EXCEL_TO_PDF("Excel to PDF", "Convert .xlsx sheets to PDF", Icons.Outlined.TableView, Color(0xFF16A34A), "Convert"),
    PDF_TO_PPT("PDF to PPT", "Convert pages to .pptx presentation", Icons.Outlined.Slideshow, Color(0xFFEA580C), "Convert"),
    PPT_TO_PDF("PPT to PDF", "Convert .pptx slides to PDF", Icons.Outlined.Slideshow, Color(0xFFEA580C), "Convert"),
    MERGE_PDF("PDF Merger", "Combine multi-PDFs into one stream", Icons.Outlined.CallMerge, Color(0xFF9333EA), "Organize"),
    RESUME_MAKER("Resume Maker", "ATS-friendly vector templates", Icons.Outlined.Badge, Color(0xFFD97706), "Create & Scan"),
    COMPRESS_PDF("PDF Compressor", "Reduce file size up to 80%", Icons.Outlined.Compress, Color(0xFF0D9488), "Optimize"),
    PROTECT_PDF("Protect / Vault", "AES-256 encryption & unlock", Icons.Outlined.Lock, Color(0xFFCA8A04), "Security"),
    PDF_EDITOR("PDF Editor", "Modify text, add stamps & rotate", Icons.Outlined.EditNote, Color(0xFFE11D48), "Organize")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHubScreen(
    onNavigateBack: () -> Unit,
    onLaunchQScan: () -> Unit,
    onLaunchResumeMaker: () -> Unit,
    onOpenGeneratedPdf: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var activeDialogTool by remember { mutableStateOf<ToolType?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }

    // Pending file action states for SaveFileNameDialog
    var pendingSingleUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSingleTool by remember { mutableStateOf<ToolType?>(null) }
    var pendingSingleOrigName by remember { mutableStateOf("") }
    var pendingSingleExt by remember { mutableStateOf("pdf") }
    var pendingSingleDefaultName by remember { mutableStateOf("") }

    var pendingMergeUris by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingMergeFirstDocName by remember { mutableStateOf("") }

    // File pickers
    val singlePdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            activeDialogTool?.let { tool ->
                val origName = getOriginalFileName(context, pickedUri)
                val ext = when (tool) {
                    ToolType.PDF_TO_WORD -> "docx"
                    ToolType.PDF_TO_EXCEL -> "xlsx"
                    ToolType.PDF_TO_PPT -> "pptx"
                    ToolType.WORD_TO_PDF, ToolType.EXCEL_TO_PDF, ToolType.PPT_TO_PDF -> "pdf"
                    ToolType.COMPRESS_PDF, ToolType.PDF_EDITOR -> "pdf"
                    ToolType.PROTECT_PDF -> "qzar"
                    else -> "pdf"
                }
                val defaultName = when (tool) {
                    ToolType.PDF_TO_WORD -> "${origName}_converted"
                    ToolType.WORD_TO_PDF -> "${origName}_pdf"
                    ToolType.PDF_TO_EXCEL -> "${origName}_sheets"
                    ToolType.EXCEL_TO_PDF -> "${origName}_pdf"
                    ToolType.PDF_TO_PPT -> "${origName}_slides"
                    ToolType.PPT_TO_PDF -> "${origName}_pdf"
                    ToolType.COMPRESS_PDF -> "${origName}_compressed"
                    ToolType.PROTECT_PDF -> "${origName}_vault"
                    ToolType.PDF_EDITOR -> "${origName}_edited"
                    else -> "${origName}_out"
                }

                pendingSingleUri = pickedUri
                pendingSingleTool = tool
                pendingSingleOrigName = origName
                pendingSingleExt = ext
                pendingSingleDefaultName = defaultName
            }
        }
    }

    var previewReorderUris by remember { mutableStateOf<List<Uri>?>(null) }

    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.size >= 2) {
            previewReorderUris = uris
        } else if (uris.isNotEmpty()) {
            statusMessage = "Please select at least 2 valid PDF files to merge."
        }
    }

    // Page Reorder & Preview Dialog before Merge
    previewReorderUris?.let { selectedList ->
        PageReorderPreviewDialog(
            initialUris = selectedList,
            onDismiss = { previewReorderUris = null },
            onConfirm = { reorderedList ->
                previewReorderUris = null
                if (reorderedList.size >= 2) {
                    val firstDocName = getOriginalFileName(context, reorderedList[0])
                    pendingMergeFirstDocName = firstDocName
                    pendingMergeUris = reorderedList
                }
            }
        )
    }

    var completedConversionFile by remember { mutableStateOf<File?>(null) }

    // Save & Name Dialog for Single Tools
    if (pendingSingleUri != null && pendingSingleTool != null) {
        val tool = pendingSingleTool!!
        val uri = pendingSingleUri!!
        SaveFileNameDialog(
            initialName = pendingSingleDefaultName,
            targetExtension = pendingSingleExt,
            sourceOriginalName = pendingSingleOrigName,
            destinationFolder = "Internal Storage > Qazar > Exports",
            onDismiss = {
                pendingSingleUri = null
                pendingSingleTool = null
            },
            onConfirm = { enteredName ->
                pendingSingleUri = null
                pendingSingleTool = null
                handleSingleFileTool(
                    context = context,
                    uri = uri,
                    tool = tool,
                    targetFileName = enteredName,
                    onProgress = { processingProgress = it },
                    onStart = { isProcessing = true; statusMessage = "Processing ${tool.title}..." },
                    onComplete = { resultFile, msg ->
                        isProcessing = false
                        statusMessage = msg
                        if (resultFile != null && resultFile.exists()) {
                            completedConversionFile = resultFile
                        }
                    }
                )
            }
        )
    }

    // Universal Conversion / Processing Result Dialog (Open & Share)
    completedConversionFile?.let { file ->
        ConversionResultDialog(
            file = file,
            onDismiss = { completedConversionFile = null },
            onOpenInViewer = { pdfFile ->
                completedConversionFile = null
                onOpenGeneratedPdf(pdfFile)
            }
        )
    }

    // Save & Name Dialog for PDF Merge
    if (pendingMergeUris != null) {
        val uris = pendingMergeUris!!
        SaveFileNameDialog(
            initialName = "${pendingMergeFirstDocName}_merged",
            targetExtension = "pdf",
            sourceOriginalName = pendingMergeFirstDocName,
            destinationFolder = "Internal Storage > Qazar > Exports",
            onDismiss = { pendingMergeUris = null },
            onConfirm = { enteredName ->
                pendingMergeUris = null
                coroutineScope.launch {
                    isProcessing = true
                    statusMessage = "Merging ${uris.size} PDFs..."
                    val inputFiles = withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri ->
                            val tempFile = File(context.cacheDir, "merge_in_${System.currentTimeMillis()}_${File(uri.path ?: "").name}.pdf")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }
                            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
                        }
                    }

                    if (inputFiles.size < 2) {
                        isProcessing = false
                        statusMessage = "Please select at least 2 valid PDF files to merge."
                        return@launch
                    }

                    val finalFileName = if (enteredName.endsWith(".pdf", ignoreCase = true)) enteredName else "$enteredName.pdf"
                    val targetFile = QazarStorageManager.getExportsDir(context).resolve(finalFileName)
                    val res = withContext(Dispatchers.IO) {
                        PdfMergerEngine.mergePdfs(context, inputFiles, targetFile) { p ->
                            processingProgress = p
                        }
                    }

                    isProcessing = false
                    if (res.isSuccess && targetFile.exists()) {
                        QazarStorageManager.indexFile(context, targetFile)
                        statusMessage = "Merged successfully into Exports/${targetFile.name}"
                        completedConversionFile = targetFile
                    } else {
                        statusMessage = "Merge failed or stream corrupt."
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            // Liquid Glass Header (Back button, Title)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0C0C12))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(64.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Glassy Back Pill
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .shadow(elevation = 6.dp, shape = CircleShape, spotColor = Color(0x20000000))
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0x33EF4444), Color(0x11EF4444))))
                            .border(0.5.dp, Color(0x33FFFFFF), CircleShape)
                            .clickable { onNavigateBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Toolkit",
                            fontFamily = GoogleSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Industrial Offline PDF Engineering Suite",
                            fontFamily = GoogleSansTextFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF08080C)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    // Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(listOf(Color(0xFFEF4444), Color(0xFF9333EA))),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.OfflineBolt,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "100% Offline & Private",
                                    color = Color.White,
                                    fontFamily = GoogleSansFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "All conversions, merges, compression & encryption run strictly on-device without cloud dependencies.",
                                    color = Color(0xFF94A3B8),
                                    fontFamily = GoogleSansTextFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // Group by categories
                val categories = listOf("Create & Scan", "Convert", "Organize", "Optimize", "Security")
                categories.forEach { category ->
                    val toolsInCat = ToolType.values().filter { it.category == category }
                    if (toolsInCat.isNotEmpty()) {
                        item {
                            Text(
                                text = category.uppercase(),
                                color = Color(0xFF64748B),
                                fontFamily = GoogleSansFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(toolsInCat) { tool ->
                            ToolCard(
                                tool = tool,
                                onClick = {
                                    when (tool) {
                                        ToolType.QSCAN -> onLaunchQScan()
                                        ToolType.RESUME_MAKER -> onLaunchResumeMaker()
                                        ToolType.MERGE_PDF -> {
                                            activeDialogTool = tool
                                            multiPdfPicker.launch("application/pdf")
                                        }
                                        ToolType.COMPRESS_PDF,
                                        ToolType.PROTECT_PDF,
                                        ToolType.PDF_EDITOR -> {
                                            activeDialogTool = tool
                                            singlePdfPicker.launch("application/pdf")
                                        }
                                        ToolType.PDF_TO_WORD,
                                        ToolType.PDF_TO_EXCEL,
                                        ToolType.PDF_TO_PPT -> {
                                            activeDialogTool = tool
                                            singlePdfPicker.launch("application/pdf")
                                        }
                                        ToolType.WORD_TO_PDF -> {
                                            activeDialogTool = tool
                                            singlePdfPicker.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                        }
                                        ToolType.EXCEL_TO_PDF -> {
                                            activeDialogTool = tool
                                            singlePdfPicker.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                        }
                                        ToolType.PPT_TO_PDF -> {
                                            activeDialogTool = tool
                                            singlePdfPicker.launch("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Processing Overlay (Modern Obsidian & Crimson Progress Dialog)
            if (isProcessing) {
                ConversionProgressDialog(
                    title = "Generating Document",
                    status = statusMessage ?: "Processing high-definition conversion...",
                    progress = processingProgress
                )
            }

            // SnackBar feedback
            statusMessage?.let { msg ->
                if (!isProcessing) {
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(3500)
                        statusMessage = null
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
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
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = msg,
                                    color = Color.White,
                                    fontFamily = GoogleSansTextFamily,
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
}

@Composable
private fun ToolCard(
    tool: ToolType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22222E))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .padding(end = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = tool.title,
                    tint = tool.accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.title,
                    color = Color.White,
                    fontFamily = GoogleSansFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = tool.subtitle,
                    color = Color(0xFF94A3B8),
                    fontFamily = GoogleSansTextFamily,
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF475569),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun handleSingleFileTool(
    context: android.content.Context,
    uri: Uri,
    tool: ToolType,
    targetFileName: String,
    onProgress: (Float) -> Unit,
    onStart: () -> Unit,
    onComplete: (File?, String) -> Unit
) {
    onStart()
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    scope.launch {
        try {
            // Copy uri to temp file
            val extPrefix = when (tool) {
                ToolType.WORD_TO_PDF -> "docx"
                ToolType.EXCEL_TO_PDF -> "xlsx"
                ToolType.PPT_TO_PDF -> "pptx"
                else -> "pdf"
            }
            val tempFile = File(context.cacheDir, "tool_in_${System.currentTimeMillis()}.$extPrefix")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                withContext(Dispatchers.Main) {
                    onComplete(null, "Failed to read input PDF file.")
                }
                return@launch
            }

            val baseName = targetFileName.trim().ifBlank { "Converted_Document" }

            when (tool) {
                ToolType.PDF_TO_WORD -> {
                    val finalName = if (baseName.endsWith(".docx", true)) baseName else "$baseName.docx"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = PdfToWordConverter.convert(context, tempFile, target) { p ->
                        scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Converted to Word: Exports/${target.name}")
                        } else onComplete(null, "Failed to convert PDF to Word.")
                    }
                }
                ToolType.PDF_TO_EXCEL -> {
                    val finalName = if (baseName.endsWith(".xlsx", true)) baseName else "$baseName.xlsx"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = PdfToExcelConverter.convert(context, tempFile, target) { p ->
                        scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Extracted to Excel: Exports/${target.name}")
                        } else onComplete(null, "Failed to convert PDF to Excel.")
                    }
                }
                ToolType.PDF_TO_PPT -> {
                    val finalName = if (baseName.endsWith(".pptx", true)) baseName else "$baseName.pptx"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = PdfToPptConverter.convert(context, tempFile, target) { p ->
                        scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Exported to PPT: Exports/${target.name}")
                        } else onComplete(null, "Failed to convert PDF to PowerPoint.")
                    }
                }
                ToolType.WORD_TO_PDF -> {
                    val finalName = if (baseName.endsWith(".pdf", true)) baseName else "$baseName.pdf"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = WordToPdfConverter.convert(context, tempFile, target) { p ->
                        scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Converted Word to PDF: Exports/${target.name}")
                        } else onComplete(null, "Failed to convert Word to PDF.")
                    }
                }
                ToolType.EXCEL_TO_PDF -> {
                    val finalName = if (baseName.endsWith(".pdf", true)) baseName else "$baseName.pdf"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = ExcelToPdfConverter.convert(context, tempFile, target) { p ->
                        scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Converted Excel to PDF: Exports/${target.name}")
                        } else onComplete(null, "Failed to convert Excel to PDF.")
                    }
                }
                ToolType.PPT_TO_PDF -> {
                    val finalName = if (baseName.endsWith(".pdf", true)) baseName else "$baseName.pdf"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = PptToPdfConverter.convert(context, tempFile, target) { p ->
                        scope.launch(Dispatchers.Main) { onProgress(p) }
                    }
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Converted PPT to PDF: Exports/${target.name}")
                        } else onComplete(null, "Failed to convert PPT to PDF.")
                    }
                }
                ToolType.COMPRESS_PDF -> {
                    val finalName = if (baseName.endsWith(".pdf", true)) baseName else "$baseName.pdf"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = PdfCompressorEngine.compressPdf(
                        context = context,
                        inputFile = tempFile,
                        outputFile = target,
                        profile = CompressionProfile.RECOMMENDED
                    ) { p -> scope.launch(Dispatchers.Main) { onProgress(p) } }
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            val compRes = res.getOrThrow()
                            QazarStorageManager.indexFile(context, target)
                            val df = DecimalFormat("#.##")
                            val origMb = df.format(compRes.originalSizeBytes / (1024.0 * 1024.0))
                            val compMb = df.format(compRes.compressedSizeBytes / (1024.0 * 1024.0))
                            onComplete(target, "Compressed (${compRes.savingsPercent}% saved: ${origMb}MB -> ${compMb}MB in Exports)")
                        } else onComplete(null, "Compression failed.")
                    }
                }
                ToolType.PROTECT_PDF -> {
                    val finalName = if (baseName.endsWith(".qzar", true)) baseName else "$baseName.qzar"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = PdfProtectionEngine.encryptPdf(context, tempFile, target, "qazar123")
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Protected with AES-256 Vault: Exports/${target.name}")
                        } else onComplete(null, "Vault encryption failed.")
                    }
                }
                ToolType.PDF_EDITOR -> {
                    val finalName = if (baseName.endsWith(".pdf", true)) baseName else "$baseName.pdf"
                    val target = QazarStorageManager.getExportsDir(context).resolve(finalName)
                    val res = PdfEditorEngine.duplicatePage(context, tempFile, target, 0)
                    withContext(Dispatchers.Main) {
                        if (res.isSuccess && target.exists()) {
                            QazarStorageManager.indexFile(context, target)
                            onComplete(target, "Page edited & saved to Exports/${target.name}")
                        } else onComplete(null, "Editor failed.")
                    }
                }
                else -> {
                    withContext(Dispatchers.Main) {
                        onComplete(null, "Action not supported directly.")
                    }
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onComplete(null, "Error: ${e.localizedMessage}")
            }
        }
    }
}

fun getOriginalFileName(context: android.content.Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) {
                        result = cursor.getString(idx)
                    }
                }
            }
        } catch (_: Exception) {}
    }
    if (result == null) {
        result = uri.lastPathSegment?.substringAfterLast('/')
    }
    return result?.substringBeforeLast('.') ?: "Document"
}
