package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.translate.ModeComponent
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.TranslateComponent

@Composable
fun ChunkModeSection(
    component: ChunkModeComponent,
    parentComponent: TranslateComponent,
    typography: Typography,
    listState: LazyListState,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    onConflictClick: (String, String) -> Unit
) {
    val sharedState by parentComponent.sharedState.collectAsStateWithLifecycle()
    val chunkState by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()
    val items by component.items.collectAsStateWithLifecycle()

    val hasConflicts = items.any { it.hasMergeConflict }

    LaunchedEffect(hasConflicts) {
        onHasMergeConflicts(hasConflicts)
    }

    ModeScreenTemplate(
        component = component,
        items = items,
        listState = listState,
        dialogs = {
            if (chunkState.chunkToReopen != null) {
                ConfirmDialog(
                    title = stringResource(R.string.chunk_done_title),
                    message = stringResource(R.string.chunk_done_prompt),
                    onDismiss = {
                        component.onAction(ChunkModeComponent.Action.ReopenChunkConfirmed(false))
                    },
                    onConfirm = {
                        component.onAction(ChunkModeComponent.Action.ReopenChunkConfirmed(true))
                    },
                    confirmText = stringResource(R.string.edit)
                )
            }
        }
    ) { item ->
        ChunkCard(
            item = item,
            sourceTabs = sharedState.sourceTabs,
            typography = typography,
            selectedSource = sharedState.resourceContainer,
            targetTranslation = parentComponent.targetTranslation,
            onSourceTabClick = {
                parentComponent.onAction(TranslateComponent.Action.SelectSource(it))
            },
            onAddNewSourceClick = onSourceDialogOpen,
            onRemoveSourceClick = {
                parentComponent.onAction(TranslateComponent.Action.RemoveSource(it))
            },
            onTextChange = {
                component.onAction(ChunkModeComponent.Action.ItemTextChanged(item, it))
            },
            onCardsSwiped = { sourceOnTop ->
                component.onAction(ModeComponent.Action.CardsSwiped(item, sourceOnTop))
            },
            onOpenChunkClick = {
                component.onAction(ChunkModeComponent.Action.ReopenChunkClicked(item))
            },
            onConflictClick = onConflictClick
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}
