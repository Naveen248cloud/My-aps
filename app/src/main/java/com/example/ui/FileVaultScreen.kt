package com.example.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FileCategory
import com.example.data.model.VaultFile
import com.example.ui.components.BatchActionBar
import com.example.ui.components.CreateFileDialog
import com.example.ui.components.EmptyVaultView
import com.example.ui.components.FileCard
import com.example.ui.components.FileDetailsDialog
import com.example.ui.components.FileFilterBar
import com.example.ui.components.FilePreviewDialog
import com.example.ui.components.StorageOverviewCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileVaultScreen(
    viewModel: FileVaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // System file picker for uploading any file
    val uploadPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadFiles(uris)
        }
    }

    // System save picker for "Save As..." custom folder download
    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.saveToTargetUri(uri)
        } else {
            viewModel.clearPendingDownload()
        }
    }

    // Handle back button
    BackHandler(
        enabled = uiState.isSelectionMode ||
                uiState.activePreviewFile != null ||
                uiState.detailsFile != null ||
                uiState.showCreateTextDialog ||
                uiState.searchQuery.isNotEmpty()
    ) {
        when {
            uiState.activePreviewFile != null -> viewModel.closePreview()
            uiState.detailsFile != null -> viewModel.closeDetails()
            uiState.showCreateTextDialog -> viewModel.setShowCreateTextDialog(false)
            uiState.isSelectionMode -> viewModel.clearSelection()
            uiState.searchQuery.isNotEmpty() -> viewModel.onSearchQueryChanged("")
        }
    }

    // Observe snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = "Logo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "FileVault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Upload & Download Hub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Quick Action: Create Doc
                    IconButton(
                        onClick = { viewModel.setShowCreateTextDialog(true) },
                        modifier = Modifier.testTag("appbar_create_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = "New Document",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Quick Action: Upload File
                    IconButton(
                        onClick = { uploadPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.testTag("appbar_upload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.UploadFile,
                            contentDescription = "Upload File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { uploadPickerLauncher.launch(arrayOf("*/*")) },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null
                        )
                    },
                    text = { Text("Upload File", fontWeight = FontWeight.SemiBold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_upload_file")
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                BatchActionBar(
                    selectedCount = uiState.selectedFileIds.size,
                    onSelectAll = { viewModel.selectAll() },
                    onDownloadSelected = {
                        val selectedFiles = uiState.files.filter { uiState.selectedFileIds.contains(it.id) }
                        selectedFiles.forEach { file ->
                            viewModel.quickExportToDownloads(file)
                        }
                        viewModel.clearSelection()
                    },
                    onDeleteSelected = {
                        viewModel.deleteSelectedFiles()
                    },
                    onClearSelection = { viewModel.clearSelection() }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Uploading / Downloading Progress Bar
                if (uiState.isUploading || uiState.isDownloading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("files_list"),
                    contentPadding = PaddingValues(
                        bottom = if (uiState.isSelectionMode) 90.dp else 84.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top Storage Overview Card
                    item(key = "storage_overview") {
                        StorageOverviewCard(
                            totalFiles = uiState.categoryCounts[FileCategory.ALL] ?: 0,
                            totalStorageBytes = uiState.totalStorageBytes,
                            onUploadClick = { uploadPickerLauncher.launch(arrayOf("*/*")) },
                            onCreateDocClick = { viewModel.setShowCreateTextDialog(true) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Search & Category Filter Bar
                    item(key = "filter_bar") {
                        FileFilterBar(
                            searchQuery = uiState.searchQuery,
                            onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            selectedCategory = uiState.selectedCategory,
                            onCategorySelected = { viewModel.onCategorySelected(it) },
                            categoryCounts = uiState.categoryCounts,
                            sortOrder = uiState.sortOrder,
                            onSortOrderChanged = { viewModel.onSortOrderChanged(it) },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Section header: "Uploaded Files (count)"
                    item(key = "files_count_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.selectedCategory == FileCategory.ALL) "Uploaded Files"
                                else "${uiState.selectedCategory.label} Files",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "${uiState.files.size} items",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Empty State or Files List
                    if (uiState.files.isEmpty()) {
                        item(key = "empty_state") {
                            EmptyVaultView(
                                isSearching = uiState.searchQuery.isNotBlank(),
                                onUploadClick = { uploadPickerLauncher.launch(arrayOf("*/*")) },
                                onCreateDocClick = { viewModel.setShowCreateTextDialog(true) }
                            )
                        }
                    } else {
                        items(
                            items = uiState.files,
                            key = { it.id }
                        ) { file ->
                            FileCard(
                                file = file,
                                isSelected = uiState.selectedFileIds.contains(file.id),
                                isSelectionMode = uiState.isSelectionMode,
                                onCardClick = { viewModel.toggleSelection(file.id) },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.toggleSelection(file.id)
                                    }
                                },
                                onDownloadClick = {
                                    viewModel.quickExportToDownloads(file)
                                },
                                onSaveAsClick = {
                                    viewModel.prepareSaveAsDownload(file)
                                    saveAsLauncher.launch(file.displayName)
                                },
                                onPreviewClick = {
                                    viewModel.openPreview(file)
                                },
                                onShareClick = {
                                    val shareIntent = viewModel.getRepository().createShareIntent(file)
                                    if (shareIntent != null) {
                                        try {
                                            context.startActivity(Intent.createChooser(shareIntent, "Share ${file.displayName}"))
                                        } catch (e: Exception) {
                                            viewModel.showSnackbar("Cannot share file.")
                                        }
                                    } else {
                                        viewModel.showSnackbar("File not found on device.")
                                    }
                                },
                                onOpenExternalClick = {
                                    val viewIntent = viewModel.getRepository().createViewIntent(file)
                                    if (viewIntent != null) {
                                        try {
                                            context.startActivity(Intent.createChooser(viewIntent, "Open with"))
                                        } catch (e: Exception) {
                                            viewModel.showSnackbar("No app found to open this file type.")
                                        }
                                    } else {
                                        viewModel.showSnackbar("File not found on device.")
                                    }
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(file)
                                },
                                onDeleteClick = {
                                    viewModel.deleteFile(file)
                                },
                                onDetailsClick = {
                                    viewModel.showDetails(file)
                                },
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // File Preview Dialog
    uiState.activePreviewFile?.let { previewFile ->
        FilePreviewDialog(
            file = previewFile,
            textContent = uiState.previewTextContent,
            onDismiss = { viewModel.closePreview() },
            onQuickDownload = {
                viewModel.quickExportToDownloads(previewFile)
            },
            onSaveAs = {
                viewModel.prepareSaveAsDownload(previewFile)
                saveAsLauncher.launch(previewFile.displayName)
            },
            onOpenExternal = {
                val viewIntent = viewModel.getRepository().createViewIntent(previewFile)
                if (viewIntent != null) {
                    try {
                        context.startActivity(Intent.createChooser(viewIntent, "Open with"))
                    } catch (e: Exception) {
                        viewModel.showSnackbar("No app found to open this file.")
                    }
                }
            },
            onShare = {
                val shareIntent = viewModel.getRepository().createShareIntent(previewFile)
                if (shareIntent != null) {
                    try {
                        context.startActivity(Intent.createChooser(shareIntent, "Share ${previewFile.displayName}"))
                    } catch (e: Exception) {
                        viewModel.showSnackbar("Cannot share file.")
                    }
                }
            }
        )
    }

    // Create File Dialog
    if (uiState.showCreateTextDialog) {
        CreateFileDialog(
            onDismiss = { viewModel.setShowCreateTextDialog(false) },
            onCreateFile = { title, content, ext ->
                viewModel.createTextFile(title, content, ext)
            }
        )
    }

    // File Details Dialog
    uiState.detailsFile?.let { detailsFile ->
        FileDetailsDialog(
            file = detailsFile,
            onDismiss = { viewModel.closeDetails() },
            onCopyHash = { msg -> viewModel.showSnackbar(msg) }
        )
    }
}
