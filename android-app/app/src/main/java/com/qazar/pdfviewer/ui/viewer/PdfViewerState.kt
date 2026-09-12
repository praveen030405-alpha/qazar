package com.qazar.pdfviewer.ui.viewer

import android.net.Uri
import android.os.ParcelFileDescriptor
import com.qazar.pdfviewer.theme.ViewingMode
import com.qazar.pdfviewer.bridge.RedactionArea
import com.qazar.pdfviewer.bridge.SignatureInfo
import com.qazar.pdfviewer.bridge.AuditLogEntry

enum class LayoutMode {
    CONTINUOUS_VERTICAL,
    SINGLE_PAGE_HORIZONTAL,
    DOUBLE_PAGE_SPREAD
}

/**
 * Single source of truth for the PDF Viewer UI state.
 * Driven by [PdfViewerViewModel].
 */
data class PdfViewerState(
    val documentPath: String? = null,
    val documentUri: Uri? = null,
    val documentTitle: String = "Document.pdf",
    val documentPfd: ParcelFileDescriptor? = null,
    val isImportedLocalCopy: Boolean = true,

    val loadedPages: List<PdfPageModel> = emptyList(),
    val isDocLoaded: Boolean = false,
    
    val isUiVisible: Boolean = true,
    val viewingMode: ViewingMode = ViewingMode.DEFAULT,
    val layoutMode: LayoutMode = LayoutMode.CONTINUOUS_VERTICAL,
    
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchHits: List<SearchHitModel> = emptyList(),
    val currentSearchHitIndex: Int = 0,
    
    val bookmarkedPages: Set<Int> = emptySet(),
    
    val isAnnotateActive: Boolean = false,
    val isRadialAnnotExpanded: Boolean = false,
    
    val showGoToPageDialog: Boolean = false,
    val showBookmarksSheet: Boolean = false,
    val showDocInfoModal: Boolean = false,
    val showRenameDialog: Boolean = false,

    // Phase 5 State
    val showRedactionDialog: Boolean = false,
    val showSignatureModal: Boolean = false,
    val showAuditLogSheet: Boolean = false,
    val auditEntries: List<AuditLogEntry> = emptyList(),
    val activeRedactions: List<RedactionArea> = emptyList(),
    val activeSignatures: List<SignatureInfo> = emptyList()
)
