package com.door43.translationstudio.ui.translate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.components.LocalSnackbarHostState
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ExportDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.translate.chunk.ChunkModeSection
import com.door43.translationstudio.ui.translate.components.NoSourceScreen
import com.door43.translationstudio.ui.translate.components.TranslateSideBar
import com.door43.translationstudio.ui.translate.components.rememberMenuItems
import com.door43.translationstudio.ui.translate.dialogs.SourceSelectionDialog
import com.door43.translationstudio.ui.translate.read.ReadModeSection
import com.door43.translationstudio.ui.translate.review.ReviewModeSection
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.io.File

@Composable
fun TargetTranslationScreen(
    viewModel: TargetTranslationViewModel = koinViewModel(),
    startWithMergeFilter: Boolean,
    onHomeClick: () -> Unit,
    onNavigateToDraft: () -> Unit,
    onProjectPreview: () -> Unit,
    onSettings: () -> Unit,
    onRestartAutoCommitTimer: () -> Unit,
    onUpdateSources: () -> Unit,
    onExportToApp: (File) -> Unit,
    onLoginClick: () -> Unit,
    onLogout: () -> Unit
) {
    val typography: Typography = koinInject()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedState by viewModel.sharedState.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showUpdateSourcesDialog by rememberSaveable { mutableStateOf(false) }
    var showSelectSourceDialog by rememberSaveable { mutableStateOf(false) }
    var searchRequested by remember { mutableStateOf(false) }
    var mergeConflictFilterOn by rememberSaveable { mutableStateOf(startWithMergeFilter) }
    var chunksDoneRequested by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showPrintDialog by rememberSaveable { mutableStateOf(false) }
    var showFeedbackDialog by rememberSaveable { mutableStateOf(false) }

    var hasMergeConflicts by remember { mutableStateOf(false) }

    val scrollCoordinator = rememberScrollCoordinator(
        chunks = sharedState.chunks,
        lastFocusChapterId = state.lastFocusChapterId,
        lastFocusFrameId = state.lastFocusFrameId,
        viewModel = viewModel
    )

    val menuItems = rememberMenuItems(
        viewMode = state.viewMode,
        draftAvailable = state.draftAvailable,
        onHomeClick = onHomeClick,
        onNavigateToDraft = onNavigateToDraft,
        onProjectPreview = onProjectPreview,
        onUploadExport = { showExportDialog = true },
        onPrint = {
            showExportDialog = true
            showPrintDialog = true
        },
        onFeedback = { showFeedbackDialog = true },
        onChunksDone = { chunksDoneRequested = true },
        onSettings = onSettings,
        onSearchRequested = { searchRequested = true }
    )

    // Collect one-shot events
    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is TargetEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                TargetEvent.RestartAutoCommitTimer -> onRestartAutoCommitTimer()
            }
        }
    }

    // Draft available snackbar
    val draftExistsStr = stringResource(R.string.draft_translation_exists)
    val draftPreview = stringResource(R.string.preview)
    LaunchedEffect(state.showDraftAvailable) {
        if (state.showDraftAvailable) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = draftExistsStr,
                    actionLabel = draftPreview,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onNavigateToDraft()
                }
            }
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) {
                    Snackbar(
                        snackbarData = it,
                        actionColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        ) { paddingValues ->
            Row(modifier = Modifier.padding(paddingValues)) {
                TranslateSideBar(
                    currentViewMode = state.viewMode,
                    showMergeConflict = hasMergeConflicts,
                    mergeConflictFilterOn = mergeConflictFilterOn,
                    onReadClick = {
                        mergeConflictFilterOn = false
                        if (state.viewMode != TranslationViewMode.READ) {
                            viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.READ))
                        }
                    },
                    onChunkClick = {
                        mergeConflictFilterOn = false
                        if (state.viewMode != TranslationViewMode.CHUNK) {
                            viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.CHUNK))
                        }
                    },
                    onReviewClick = {
                        mergeConflictFilterOn = false
                        if (state.viewMode != TranslationViewMode.REVIEW) {
                            viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.REVIEW))
                        }
                    },
                    onMergeConflictClick = {
                        mergeConflictFilterOn = !mergeConflictFilterOn
                        if (state.viewMode != TranslationViewMode.REVIEW) {
                            viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.REVIEW))
                        }
                    },
                    onSliderValueChange = {
                        scrollCoordinator.onSliderChange(it, sharedState.chunks)
                    },
                    sliderValue = scrollCoordinator.sliderValue.value,
                    chapterLabel = scrollCoordinator.sliderChapterLabel,
                    actions = menuItems
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (sharedState.resourceContainer == null) {
                        NoSourceScreen(
                            projectTitle = state.projectTitle ?: "",
                            onAddSourceClick = { showSelectSourceDialog = true }
                        )
                    } else {
                        when (state.viewMode) {
                            TranslationViewMode.READ -> ReadModeSection(
                                viewModel = viewModel,
                                typography = typography,
                                listState = scrollCoordinator.listState,
                                onSourceDialogOpen = { showSelectSourceDialog = true },
                                onHasMergeConflicts = { hasMergeConflicts = it },
                                onBeginTranslation = {
                                    scrollCoordinator.pendingScrollChapter = PendingScrollItem(
                                        chapterId = it
                                    )
                                    viewModel.onAction(
                                        TargetAction.SaveLastViewMode(TranslationViewMode.CHUNK)
                                    )
                                }
                            )
                            TranslationViewMode.CHUNK -> ChunkModeSection(
                                viewModel = viewModel,
                                typography = typography,
                                listState = scrollCoordinator.listState,
                                onSourceDialogOpen = { showSelectSourceDialog = true },
                                onHasMergeConflicts = { hasMergeConflicts = it },
                                onConflictClick = { chapterId, chunkId ->
                                    scrollCoordinator.pendingScrollChapter = PendingScrollItem(
                                        chapterId = chapterId,
                                        chunkId = chunkId
                                    )
                                    viewModel.onAction(
                                        TargetAction.SaveLastViewMode(TranslationViewMode.REVIEW)
                                    )
                                }
                            )
                            TranslationViewMode.REVIEW -> ReviewModeSection(
                                translationViewModel = viewModel,
                                typography = typography,
                                listState = scrollCoordinator.listState,
                                searchRequested = searchRequested,
                                onSearchConsumed = { searchRequested = false },
                                onSourceDialogOpen = { showSelectSourceDialog = true },
                                onHasMergeConflicts = { hasMergeConflicts = it },
                                mergeConflictFilterOn = mergeConflictFilterOn,
                                onMergeConflictFilterReset = { mergeConflictFilterOn = false },
                                chunksDoneRequested = chunksDoneRequested,
                                onChunksDoneConsumed = { chunksDoneRequested = false }
                            )
                        }
                    }
                }
            }
        }

        // Dialogs
        if (showSelectSourceDialog) {
            SourceSelectionDialog(
                targetTranslation = viewModel.targetTranslation,
                onDismissRequest = { showSelectSourceDialog = false },
                onConfirm = {
                    showSelectSourceDialog = false
                    viewModel.onAction(TargetAction.ConfirmSelectedSources(it))
                },
                onUpdateSources = { showUpdateSourcesDialog = true }
            )
        }

        if (showUpdateSourcesDialog) {
            ConfirmDialog(
                title = stringResource(R.string.warning_title),
                message = stringResource(R.string.update_warning),
                onConfirm = {
                    showUpdateSourcesDialog = false
                    onUpdateSources()
                },
                onDismiss = { showUpdateSourcesDialog = false }
            )
        }

        if (showExportDialog) {
            ExportDialog(
                targetTranslation = viewModel.targetTranslation,
                openPrint = showPrintDialog,
                onExportToApp = onExportToApp,
                onLogout = onLogout,
                onMergeConflict = {
                    mergeConflictFilterOn = true
                    viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.REVIEW))
                },
                onLoginClick = onLoginClick,
                onDismiss = {
                    showExportDialog = false
                    showPrintDialog = false
                }
            )
        }

        if (showFeedbackDialog) {
            FeedbackDialog(
                onDismiss = { showFeedbackDialog = false }
            )
        }

        progress?.let {
            ProgressDialog(
                message = it.message,
                progress = it.value
            )
        }
    }
}
