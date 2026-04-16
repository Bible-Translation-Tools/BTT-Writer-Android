package com.door43.translationstudio.ui.translate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.door43.translationstudio.ui.components.LocalSnackbarHostState
import com.door43.translationstudio.ui.components.rememberTranslateMenuItems
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ExportDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.translate.components.NoSourceScreen
import com.door43.translationstudio.ui.translate.components.TranslateSidebar
import com.door43.translationstudio.ui.translate.dialogs.SourceSelectionDialog
import kotlinx.coroutines.launch

@Composable
fun TargetTranslationScreen(
    component: TranslateComponent,
    startWithMergeFilter: Boolean
) {
    val state by component.state.collectAsStateWithLifecycle()
    val sharedState by component.sharedState.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()

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
        component = component
    )

    val menuItems = rememberTranslateMenuItems(
        viewMode = state.viewMode,
        draftAvailable = state.draftAvailable,
        onHomeClick = { component.openHome(false) },
        onNavigateToDraft = {
            component.openDraft(component.targetTranslation.id)
        },
        onProjectPreview = {
            component.openPublishProject(component.targetTranslation.id)
        },
        onUploadExport = { showExportDialog = true },
        onPrint = {
            showExportDialog = true
            showPrintDialog = true
        },
        onFeedback = { showFeedbackDialog = true },
        onChunksDone = { chunksDoneRequested = true },
        onSettings = component::openSettings,
        onSearchRequested = { searchRequested = true }
    )

    // Collect one-shot events
    LaunchedEffect(component) {
        component.event.collect { event ->
            when (event) {
                is TranslateComponent.Event.SnackbarMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is TranslateComponent.Event.RestartAutoCommitTimer -> {
                    component.restartAutoCommitTimer()
                }
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
                    component.openDraft(component.targetTranslation.id)
                }
            }
        }
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            }
        ) { paddingValues ->
            Row(modifier = Modifier.padding(paddingValues)) {
                TranslateSidebar(
                    currentViewMode = state.viewMode,
                    showMergeConflict = hasMergeConflicts,
                    mergeConflictFilterOn = mergeConflictFilterOn,
                    onReadClick = {
                        mergeConflictFilterOn = false
                        if (state.viewMode != TranslationViewMode.READ) {
                            component.onAction(TranslateComponent.Action.SaveLastViewMode(
                                TranslationViewMode.READ
                            ))
                        }
                    },
                    onChunkClick = {
                        mergeConflictFilterOn = false
                        if (state.viewMode != TranslationViewMode.CHUNK) {
                            component.onAction(TranslateComponent.Action.SaveLastViewMode(
                                TranslationViewMode.CHUNK
                            ))
                        }
                    },
                    onReviewClick = {
                        mergeConflictFilterOn = false
                        if (state.viewMode != TranslationViewMode.REVIEW) {
                            component.onAction(TranslateComponent.Action.SaveLastViewMode(
                                TranslationViewMode.REVIEW
                            ))
                        }
                    },
                    onMergeConflictClick = {
                        mergeConflictFilterOn = !mergeConflictFilterOn
                        if (state.viewMode != TranslationViewMode.REVIEW) {
                            component.onAction(TranslateComponent.Action.SaveLastViewMode(
                                TranslationViewMode.REVIEW
                            ))
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
                        TranslateRouter(
                            component = component,
                            scrollCoordinator = scrollCoordinator,
                            onSourceDialogOpen = { showSelectSourceDialog = true },
                            onHasMergeConflicts = { hasMergeConflicts = it },
                            searchRequested = searchRequested,
                            onSearchConsumed = { searchRequested = false },
                            mergeConflictFilterOn = mergeConflictFilterOn,
                            onMergeConflictFilterReset = { mergeConflictFilterOn = false },
                            chunksDoneRequested = chunksDoneRequested,
                            onChunksDoneConsumed = { chunksDoneRequested = false }
                        )
                    }
                }
            }
        }

        // Dialogs
        if (showSelectSourceDialog) {
            SourceSelectionDialog(
                targetTranslation = component.targetTranslation,
                onDismissRequest = { showSelectSourceDialog = false },
                onConfirm = {
                    showSelectSourceDialog = false
                    component.onAction(TranslateComponent.Action.ConfirmSelectedSources(it))
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
                    component.openHome(true)
                },
                onDismiss = { showUpdateSourcesDialog = false }
            )
        }

        if (showExportDialog) {
            ExportDialog(
                targetTranslation = component.targetTranslation,
                openPrint = showPrintDialog,
                onExportToApp = component::exportToApp,
                onLogin = {
                    showExportDialog = false
                    component.openLogin()
                },
                onLogout = {
                    showExportDialog = false
                    component.logout()
                },
                onMergeConflict = {
                    mergeConflictFilterOn = true
                    component.onAction(TranslateComponent.Action.SaveLastViewMode(
                        TranslationViewMode.REVIEW
                    ))
                },
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
