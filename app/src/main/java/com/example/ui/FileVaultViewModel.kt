package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.FileCategory
import com.example.data.model.VaultFile
import com.example.data.repository.FileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)")
}

data class FileVaultUiState(
    val files: List<VaultFile> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: FileCategory = FileCategory.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val selectedFileIds: Set<Long> = emptySet(),
    val isUploading: Boolean = false,
    val isDownloading: Boolean = false,
    val activePreviewFile: VaultFile? = null,
    val previewTextContent: String? = null,
    val detailsFile: VaultFile? = null,
    val showCreateTextDialog: Boolean = false,
    val snackbarMessage: String? = null,
    val totalStorageBytes: Long = 0L,
    val categoryCounts: Map<FileCategory, Int> = emptyMap(),
    val pendingDownloadFile: VaultFile? = null
) {
    val isSelectionMode: Boolean get() = selectedFileIds.isNotEmpty()
}

class FileVaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FileRepository

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow(FileCategory.ALL)
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isUploading = MutableStateFlow(false)
    private val _isDownloading = MutableStateFlow(false)
    private val _activePreviewFile = MutableStateFlow<VaultFile?>(null)
    private val _previewTextContent = MutableStateFlow<String?>(null)
    private val _detailsFile = MutableStateFlow<VaultFile?>(null)
    private val _showCreateTextDialog = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _pendingDownloadFile = MutableStateFlow<VaultFile?>(null)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = FileRepository(application, database.fileDao())

        viewModelScope.launch {
            repository.seedInitialSampleFilesIfEmpty()
        }
    }

    val uiState: StateFlow<FileVaultUiState> = combine(
        repository.allFiles,
        _searchQuery,
        _selectedCategory,
        _sortOrder,
        _selectedFileIds,
        _isUploading,
        _isDownloading,
        _activePreviewFile,
        _previewTextContent,
        _detailsFile,
        _showCreateTextDialog,
        _snackbarMessage,
        _pendingDownloadFile
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val allFiles = params[0] as List<VaultFile>
        val query = params[1] as String
        val category = params[2] as FileCategory
        val sort = params[3] as SortOrder
        @Suppress("UNCHECKED_CAST")
        val selectedIds = params[4] as Set<Long>
        val uploading = params[5] as Boolean
        val downloading = params[6] as Boolean
        val preview = params[7] as VaultFile?
        val previewText = params[8] as String?
        val details = params[9] as VaultFile?
        val showCreateDialog = params[10] as Boolean
        val snackbar = params[11] as String?
        val pendingDownload = params[12] as VaultFile?

        val totalBytes = allFiles.sumOf { it.sizeBytes }

        // Category counts
        val counts = mutableMapOf<FileCategory, Int>()
        counts[FileCategory.ALL] = allFiles.size
        FileCategory.values().forEach { cat ->
            if (cat != FileCategory.ALL) {
                counts[cat] = allFiles.count { it.category == cat.name }
            }
        }

        // Filter by category
        val categoryFiltered = if (category == FileCategory.ALL) {
            allFiles
        } else {
            allFiles.filter { it.category == category.name }
        }

        // Filter by search query
        val searchFiltered = if (query.isBlank()) {
            categoryFiltered
        } else {
            val q = query.trim().lowercase()
            categoryFiltered.filter {
                it.displayName.lowercase().contains(q) ||
                it.extension.lowercase().contains(q) ||
                it.note.lowercase().contains(q)
            }
        }

        // Sort
        val sortedFiles = when (sort) {
            SortOrder.NEWEST -> searchFiltered.sortedByDescending { it.uploadedAt }
            SortOrder.OLDEST -> searchFiltered.sortedBy { it.uploadedAt }
            SortOrder.NAME_ASC -> searchFiltered.sortedBy { it.displayName.lowercase() }
            SortOrder.NAME_DESC -> searchFiltered.sortedByDescending { it.displayName.lowercase() }
            SortOrder.SIZE_DESC -> searchFiltered.sortedByDescending { it.sizeBytes }
            SortOrder.SIZE_ASC -> searchFiltered.sortedBy { it.sizeBytes }
        }

        FileVaultUiState(
            files = sortedFiles,
            searchQuery = query,
            selectedCategory = category,
            sortOrder = sort,
            selectedFileIds = selectedIds,
            isUploading = uploading,
            isDownloading = downloading,
            activePreviewFile = preview,
            previewTextContent = previewText,
            detailsFile = details,
            showCreateTextDialog = showCreateDialog,
            snackbarMessage = snackbar,
            totalStorageBytes = totalBytes,
            categoryCounts = counts,
            pendingDownloadFile = pendingDownload
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FileVaultUiState()
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: FileCategory) {
        _selectedCategory.value = category
    }

    fun onSortOrderChanged(order: SortOrder) {
        _sortOrder.value = order
    }

    fun uploadFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isUploading.value = true
            var uploadedCount = 0
            for (uri in uris) {
                val result = repository.uploadFromUri(uri)
                if (result != null) {
                    uploadedCount++
                }
            }
            _isUploading.value = false
            showSnackbar(
                if (uploadedCount == 1) "File uploaded successfully!"
                else "$uploadedCount files uploaded successfully!"
            )
        }
    }

    fun createTextFile(title: String, content: String, extension: String) {
        viewModelScope.launch {
            val result = repository.createTextFile(title, content, extension)
            _showCreateTextDialog.value = false
            if (result != null) {
                showSnackbar("File created & saved to vault: ${result.displayName}")
            } else {
                showSnackbar("Failed to create file.")
            }
        }
    }

    fun quickExportToDownloads(file: VaultFile) {
        viewModelScope.launch {
            _isDownloading.value = true
            val result = repository.exportToPublicDownloads(file)
            _isDownloading.value = false
            result.onSuccess { msg ->
                showSnackbar("✓ Downloaded: ${file.displayName}")
            }.onFailure { err ->
                showSnackbar("Download failed: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun prepareSaveAsDownload(file: VaultFile) {
        _pendingDownloadFile.value = file
    }

    fun clearPendingDownload() {
        _pendingDownloadFile.value = null
    }

    fun saveToTargetUri(targetUri: Uri) {
        val file = _pendingDownloadFile.value ?: return
        viewModelScope.launch {
            _isDownloading.value = true
            val success = repository.exportToTargetUri(file, targetUri)
            _isDownloading.value = false
            _pendingDownloadFile.value = null
            if (success) {
                showSnackbar("✓ Downloaded to selected folder: ${file.displayName}")
            } else {
                showSnackbar("Failed to save file to chosen location.")
            }
        }
    }

    fun toggleSelection(fileId: Long) {
        _selectedFileIds.update { current ->
            if (current.contains(fileId)) {
                current - fileId
            } else {
                current + fileId
            }
        }
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
    }

    fun selectAll() {
        val currentFiles = uiState.value.files
        _selectedFileIds.value = currentFiles.map { it.id }.toSet()
    }

    fun deleteSelectedFiles() {
        val selectedIds = _selectedFileIds.value
        val allCurrentFiles = uiState.value.files
        val filesToDelete = allCurrentFiles.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            repository.deleteMultipleFiles(filesToDelete)
            clearSelection()
            showSnackbar("${filesToDelete.size} file(s) deleted.")
        }
    }

    fun deleteFile(file: VaultFile) {
        viewModelScope.launch {
            repository.deleteFile(file)
            if (_activePreviewFile.value?.id == file.id) {
                _activePreviewFile.value = null
            }
            if (_detailsFile.value?.id == file.id) {
                _detailsFile.value = null
            }
            showSnackbar("Deleted ${file.displayName}")
        }
    }

    fun toggleFavorite(file: VaultFile) {
        viewModelScope.launch {
            repository.toggleFavorite(file)
        }
    }

    fun openPreview(file: VaultFile) {
        _activePreviewFile.value = file
        _previewTextContent.value = null
        if (file.category == FileCategory.DOCUMENTS.name ||
            file.extension in listOf("TXT", "MD", "JSON", "CSV", "XML", "KT", "JAVA", "HTML", "LOG")
        ) {
            viewModelScope.launch {
                val text = repository.readTextContent(file)
                _previewTextContent.value = text
            }
        }
    }

    fun closePreview() {
        _activePreviewFile.value = null
        _previewTextContent.value = null
    }

    fun showDetails(file: VaultFile) {
        _detailsFile.value = file
    }

    fun closeDetails() {
        _detailsFile.value = null
    }

    fun setShowCreateTextDialog(show: Boolean) {
        _showCreateTextDialog.value = show
    }

    fun showSnackbar(message: String) {
        _snackbarMessage.value = message
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun getRepository(): FileRepository = repository
}
