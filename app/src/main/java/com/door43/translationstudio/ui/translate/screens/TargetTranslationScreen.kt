package com.door43.translationstudio.ui.translate.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.ui.components.ConfirmDialog
import com.door43.translationstudio.ui.components.ProgressDialog
import com.door43.translationstudio.ui.translate.components.NoSourceScreen
import com.door43.translationstudio.ui.translate.components.SourceSelectionDialog
import com.door43.translationstudio.ui.translate.components.TranslateSideBar
import com.door43.translationstudio.ui.translate.components.TranslateSideBarAction
import com.door43.translationstudio.ui.translate.read.ReadModeScreen
import com.door43.translationstudio.ui.viewmodels.RCItem
import com.door43.translationstudio.ui.viewmodels.TargetTranslationModel
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun TargetTranslationScreen(
    viewModel: TargetTranslationViewModel = koinViewModel(),
    onHomeClick: () -> Unit,
    onNavigateToDraft: () -> Unit,
    onProjectPreview: () -> Unit,
    onUploadExport: () -> Unit,
    onPrint: () -> Unit,
    onFeedback: () -> Unit,
    onSearch: () -> Unit,
    onChunksDone: () -> Unit,
    onSettings: () -> Unit
) {
    val model: TargetTranslationModel by viewModel.model.collectAsStateWithLifecycle()

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val draftExistsStr = stringResource(R.string.draft_translation_exists)
    val draftPreview = stringResource(R.string.preview)

    val menuItems = remember { mutableStateListOf<TranslateSideBarAction>() }

    val menuActionHome = stringResource(R.string.action_translations)
    val menuActionDrafts = stringResource(R.string.view_available_drafts)
    val menuActionPreview = stringResource(R.string.title_review)
    val menuActionUpload = stringResource(R.string.menu_upload_export)
    val menuActionPrint = stringResource(R.string.print)
    val menuActionFeedback = stringResource(R.string.feedback)
    val menuActionSearch = stringResource(R.string.action_search)
    val menuActionChunksDone = stringResource(R.string.mark_chunks_done)
    val menuActionSettings = stringResource(R.string.action_settings)

    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var sourceToDownload by rememberSaveable { mutableStateOf<RCItem?>(null) }
    var sourceToDelete by rememberSaveable { mutableStateOf<RCItem?>(null) }

    LaunchedEffect(Unit) {
        ContainerCache.empty()
        viewModel.setSelectedResourceContainer()
    }

    LaunchedEffect(model.draftAvailable, model.viewMode) {
        menuItems.clear()
        menuItems.add(
            TranslateSideBarAction(
                title = menuActionHome,
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                onClick = onHomeClick
            )
        )
        if (model.draftAvailable) {
            menuItems.add(
                TranslateSideBarAction(
                    title = menuActionDrafts,
                    icon = Icons.Default.Translate,
                    onClick = onNavigateToDraft
                )
            )
        }
        menuItems.addAll(
            listOf(
                TranslateSideBarAction(
                    title = menuActionPreview,
                    icon = Icons.Default.DoneAll,
                    onClick = onProjectPreview
                ),
                TranslateSideBarAction(
                    title = menuActionUpload,
                    icon = Icons.Default.Upload,
                    onClick = onUploadExport
                ),
                TranslateSideBarAction(
                    title = menuActionPrint,
                    icon = Icons.Default.Print,
                    onClick = onPrint
                ),
                TranslateSideBarAction(
                    title = menuActionFeedback,
                    icon = Icons.Default.Feedback,
                    onClick = onFeedback
                )
            )
        )
        if (model.viewMode == TranslationViewMode.REVIEW) {
            menuItems.addAll(
                listOf(
                    TranslateSideBarAction(
                        title = menuActionSearch,
                        icon = Icons.Default.Search,
                        onClick = onSearch
                    ),
                    TranslateSideBarAction(
                        title = menuActionChunksDone,
                        icon = Icons.Default.Check,
                        onClick = onChunksDone
                    )
                )
            )
        }
        menuItems.add(
            TranslateSideBarAction(
                title = menuActionSettings,
                icon = Icons.Default.Settings,
                onClick = onSettings
            )
        )
    }

    LaunchedEffect(model.snackBarMessage) {
        model.snackBarMessage?.let { message ->
            scope.launch {
                snackBarHostState.showSnackbar(message)
            }
        }
    }

    LaunchedEffect(model.showDraftAvailable) {
        if (model.showDraftAvailable) {
            scope.launch {
                val result = snackBarHostState.showSnackbar(
                    message = draftExistsStr,
                    actionLabel = draftPreview,
                    duration = SnackbarDuration.Long
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        onNavigateToDraft()
                    }
                    else -> {}
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackBarHostState) {
                Snackbar(
                    snackbarData = it,
                    actionColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier.padding(paddingValues)
        ) {
            TranslateSideBar(
                currentViewMode = model.viewMode,
                showMergeConflict = false, // TODO model.items.any { it.hasMergeConflicts },
                onReadClick = {
                    viewModel.setLastViewMode(TranslationViewMode.READ)
                },
                onChunkClick = {
                    viewModel.setLastViewMode(TranslationViewMode.CHUNK)
                },
                onReviewClick = {
                    // TODO Should reset conflict items filter
                    viewModel.setLastViewMode(TranslationViewMode.REVIEW)
                },
                onMergeConflictClick = {
                    // TODO Should toggle conflict items filter
                    viewModel.setLastViewMode(TranslationViewMode.REVIEW)
                },
                onSliderValueChange = {},
                actions = menuItems
            )

            Box(modifier = Modifier.weight(1f)) {
                if (model.items.isEmpty()) {
                    val project = viewModel.getProject()
                    val projectTitle = "${project?.name} - ${viewModel.targetTranslation.targetLanguageName}"
                    NoSourceScreen(
                        projectTitle = projectTitle,
                        onAddSourceClick = {
                            viewModel.loadAvailableSources()
                            showSourceDialog = true
                        }
                    )
                } else {
                    when (model.viewMode) {
                        TranslationViewMode.READ -> {
                            ReadModeScreen(
                                items = model.items,
                                sourceTabs = model.sourceTabs,
                                selectedSourceId = model.resourceContainer?.slug,
                                lastFocusChapterId = viewModel.getLastFocusChapterId(),
                                onSourceTabClick = viewModel::setSelectedResourceContainer,
                                onAddNewSourceClick = {
                                    viewModel.loadAvailableSources()
                                    showSourceDialog = true
                                },
                                onRemoveSourceClick = viewModel::removeOpenSourceTranslation,
                                onScrollToChapterId = {
                                    viewModel.setLastFocus(it, null)
                                }
                            )
                        }
                        TranslationViewMode.CHUNK -> {
                            ChunkModeScreen()
                        }
                        TranslationViewMode.REVIEW -> {
                            ReviewModeScreen()
                        }
                    }
                }
            }
        }
    }

    if (showSourceDialog) {
        SourceSelectionDialog(
            sources = model.availableSources,
            onDismissRequest = { showSourceDialog = false },
            onConfirm = {
                showSourceDialog = false
                viewModel.confirmSelectedSources()
            },
            onUpdate = { },
            onToggleSelection = viewModel::toggleSourceSelection,
            onTriggerDownload = { sourceToDownload = it },
            onTriggerDelete = { sourceToDelete = it }
        )
    }

    sourceToDownload?.let { source ->
        ConfirmDialog(
            title = stringResource(R.string.title_download_source_language),
            message = stringResource(R.string.download_source_language, source.title),
            onConfirm = {
                sourceToDownload = null
                viewModel.downloadResourceContainer(source)
            },
            onDismiss = { sourceToDownload = null }
        )
    }

    sourceToDelete?.let { source ->
        ConfirmDialog(
            title = stringResource(R.string.label_delete),
            message = stringResource(R.string.confirm_delete_project),
            onConfirm = {
                sourceToDelete = null
                viewModel.deleteResourceContainer(source)
            },
            onDismiss = { sourceToDelete = null }
        )
    }

    model.progress?.let {
        ProgressDialog(
            message = it.message ?: stringResource(R.string.loading),
            progressValue = it.progress.toFloat()
        )
    }
}