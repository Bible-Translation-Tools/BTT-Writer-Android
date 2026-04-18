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
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.LocalSnackbarHostState
import com.door43.translationstudio.ui.components.rememberTranslateMenuItems
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.dialogs.export.ExportDialog
import com.door43.translationstudio.ui.dialogs.feedback.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.source.SourceSelectionDialog
import com.door43.translationstudio.ui.translate.components.NoSourceScreen
import com.door43.translationstudio.ui.translate.components.TranslateSidebar
import kotlinx.coroutines.launch

@Composable
fun TargetTranslationScreen(
    component: TranslateComponent
) {
    val state by component.state.collectAsStateWithLifecycle()
    val sharedState by component.sharedState.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()
    val currentViewMode by component.currentViewMode.subscribeAsState()

    val dialogSlot by component.dialogSlot.subscribeAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var searchRequested by remember { mutableStateOf(false) }
    var chunksDoneRequested by rememberSaveable { mutableStateOf(false) }

    var hasMergeConflicts by remember { mutableStateOf(false) }

    val scrollCoordinator = rememberScrollCoordinator(
        items = emptyList(),
        lastFocusChapterId = state.lastFocusChapterId,
        lastFocusFrameId = state.lastFocusFrameId,
        component = component
    )

    val menuItems = rememberTranslateMenuItems(
        viewMode = currentViewMode,
        draftAvailable = state.draftAvailable,
        onHomeClick = { component.openHome() },
        onNavigateToDraft = {
            component.openDraft(component.targetTranslation.id)
        },
        onProjectPreview = {
            component.openPublishProject(component.targetTranslation.id)
        },
        onUploadExport = component::showExportDialog,
        onPrint = {
            component.showExportDialog(true)
        },
        onFeedback = component::showFeedbackDialog,
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
                    currentViewMode = currentViewMode,
                    showMergeConflict = hasMergeConflicts,
                    conflictFilterOn = state.conflictFilterOn,
                    onReadClick = component::openReadMode,
                    onChunkClick = component::openChunkMode,
                    onReviewClick = {
                        component.openReviewMode(false)
                    },
                    onConflictClick = {
                        component.openReviewMode(!state.conflictFilterOn)
                    },
                    onSliderValueChange = {
                        scrollCoordinator.onSliderChange(it, emptyList())
                    },
                    sliderValue = scrollCoordinator.sliderValue.value,
                    chapterLabel = scrollCoordinator.sliderChapterLabel,
                    actions = menuItems
                )

                Box(modifier = Modifier.weight(1f)) {
                    if (sharedState.resourceContainer == null) {
                        NoSourceScreen(
                            projectTitle = state.projectTitle ?: "",
                            onAddSourceClick = component::showSelectSourcesDialog
                        )
                    } else {
                        TranslateRouter(
                            component = component,
                            scrollCoordinator = scrollCoordinator,
                            onSourceDialogOpen = component::showSelectSourcesDialog,
                            onHasMergeConflicts = { hasMergeConflicts = it },
                            searchRequested = searchRequested,
                            onSearchConsumed = { searchRequested = false },
                            conflictFilterOn = state.conflictFilterOn,
                            onConflictFilterReset = { component.updateMergeFilter(false) },
                            chunksDoneRequested = chunksDoneRequested,
                            onChunksDoneConsumed = { chunksDoneRequested = false }
                        )
                    }
                }
            }
        }

        dialogSlot.child?.instance?.let { child ->
            when (child) {
                is TranslateComponent.DialogChild.Feedback -> FeedbackDialog(
                    component = child.component,
                    onDismiss = component::dismissDialog
                )
                is TranslateComponent.DialogChild.SelectSources -> SourceSelectionDialog(
                    component = child.component,
                    onDismiss = component::dismissDialog
                )
                is TranslateComponent.DialogChild.Export -> ExportDialog(
                    component = child.component,
                    onDismiss = component::dismissDialog
                )
            }
        }

        progress?.let {
            ProgressDialog(
                message = it.message,
                progress = it.value
            )
        }
    }
}
