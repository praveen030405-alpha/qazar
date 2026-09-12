package com.qazar.pdfviewer.ui.viewer

import android.app.Application
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.qazar.pdfviewer.bridge.MeridianNativeBridge
import com.qazar.pdfviewer.data.BookmarkManager
import com.qazar.pdfviewer.data.PdfLibraryManager
import com.qazar.pdfviewer.theme.ViewingMode
import com.qazar.pdfviewer.ui.home.copyUriToCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * M1 Architecture Refactor: Extracts God-viewer logic into a ViewModel.
 */
class PdfViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "PdfViewerViewModel"

    private val _state = MutableStateFlow(PdfViewerState())
    val state: StateFlow<PdfViewerState> = _state.asStateFlow()

    fun loadDocument(uri: Uri?, path: String?, title: String?, pfd: ParcelFileDescriptor?, isImported: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var finalPath = ""
                var storedPfd: ParcelFileDescriptor? = pfd

                if (uri != null && uri.scheme == "content") {
                    val (copiedPath, _) = copyUriToCache(getApplication(), uri)
                    if (copiedPath != null) {
                        finalPath = copiedPath
                        val file = File(finalPath)
                        storedPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                } else if (path != null) {
                    finalPath = path
                    val file = File(finalPath)
                    if (file.exists() && storedPfd == null) {
                        storedPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                }

                val docTitle = title ?: "Document.pdf"
                
                // Track in library
                val sizeBytes = storedPfd?.statSize ?: 0L
                if (finalPath.isNotEmpty()) {
                    PdfLibraryManager.trackExternalDocument(getApplication(), finalPath, docTitle, sizeBytes)
                }

                val bookmarks = BookmarkManager.getBookmarks(getApplication(), finalPath.ifEmpty { docTitle })

                _state.update {
                    it.copy(
                        documentPath = finalPath.ifEmpty { null },
                        documentUri = uri,
                        documentTitle = docTitle,
                        documentPfd = storedPfd,
                        isImportedLocalCopy = isImported,
                        bookmarkedPages = bookmarks,
                        isDocLoaded = false, // Reset until bridge opens it
                    )
                }

                // Document loading and memory budgeting is coordinated directly by RealPdfLoader in PdfViewerScreen

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load document", e)
            }
        }
    }

    fun toggleUiVisibility() {
        _state.update { it.copy(isUiVisible = !it.isUiVisible) }
    }

    fun setViewingMode(mode: ViewingMode) {
        _state.update { it.copy(viewingMode = mode) }
    }

    fun toggleSearch() {
        _state.update { it.copy(isSearchActive = !it.isSearchActive, isUiVisible = true) }
    }

    fun setLayoutMode(mode: LayoutMode) {
        _state.update { it.copy(layoutMode = mode) }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.Default) {
                val hits = MeridianNativeBridge.searchText(query)
                _state.update {
                    it.copy(searchHits = hits, currentSearchHitIndex = 0)
                }
            }
        } else {
            _state.update { it.copy(searchHits = emptyList(), currentSearchHitIndex = 0) }
        }
    }

    fun nextSearchHit() {
        val currentHits = _state.value.searchHits
        if (currentHits.isNotEmpty()) {
            val nextIndex = (_state.value.currentSearchHitIndex + 1) % currentHits.size
            _state.update { it.copy(currentSearchHitIndex = nextIndex) }
        }
    }

    fun prevSearchHit() {
        val currentHits = _state.value.searchHits
        if (currentHits.isNotEmpty()) {
            val prevIndex = if (_state.value.currentSearchHitIndex > 0) _state.value.currentSearchHitIndex - 1 else currentHits.size - 1
            _state.update { it.copy(currentSearchHitIndex = prevIndex) }
        }
    }

    fun toggleBookmark(pageIndex: Int) {
        val docKey = _state.value.documentPath ?: _state.value.documentTitle
        val current = _state.value.bookmarkedPages.toMutableSet()
        if (current.contains(pageIndex)) {
            current.remove(pageIndex)
        } else {
            current.add(pageIndex)
        }
        BookmarkManager.toggleBookmark(getApplication(), docKey, pageIndex)
        _state.update { it.copy(bookmarkedPages = current) }
    }

    override fun onCleared() {
        super.onCleared()
        // Rust memory leak prevention: Clean up JNI resources
        try {
            _state.value.documentPfd?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing PFD on clear", e)
        }
    }
}

class PdfViewerViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PdfViewerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PdfViewerViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
