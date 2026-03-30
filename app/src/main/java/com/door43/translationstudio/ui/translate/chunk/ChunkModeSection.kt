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
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.TargetAction
import com.door43.translationstudio.ui.translate.TargetTranslationViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ChunkModeSection(
    viewModel: TargetTranslationViewModel,
    typography: Typography,
    listState: LazyListState,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    onConflictClick: (String, String) -> Unit
) {
    val chunkVm: ChunkModeViewModel = koinViewModel {
        parametersOf(viewModel.sharedState, viewModel.eventSender)
    }

    val sharedState by viewModel.sharedState.collectAsStateWithLifecycle()
    val chunkState by chunkVm.state.collectAsStateWithLifecycle()
    val items by chunkVm.items.collectAsStateWithLifecycle()

    val hasConflicts = items.any { it.hasMergeConflict }

    LaunchedEffect(hasConflicts) {
        onHasMergeConflicts(hasConflicts)
    }

    ModeScreenTemplate(
        viewModel = chunkVm,
        items = items,
        listState = listState,
        dialogs = {
            if (chunkState.chunkToReopen != null) {
                ConfirmDialog(
                    title = stringResource(R.string.chunk_done_title),
                    message = stringResource(R.string.chunk_done_prompt),
                    onDismiss = {
                        chunkVm.onAction(ChunkAction.ReopenChunkConfirmed(false))
                    },
                    onConfirm = {
                        chunkVm.onAction(ChunkAction.ReopenChunkConfirmed(true))
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
            targetTranslation = viewModel.targetTranslation,
            onSourceTabClick = {
                viewModel.onAction(TargetAction.SelectSource(it))
            },
            onAddNewSourceClick = onSourceDialogOpen,
            onRemoveSourceClick = {
                viewModel.onAction(TargetAction.RemoveSource(it))
            },
            onTextChange = {
                chunkVm.onAction(ChunkAction.ItemTextChanged(item, it))
            },
            onCardsSwiped = { sourceOnTop ->
                chunkVm.onAction(ModeAction.CardsSwiped(item, sourceOnTop))
            },
            onOpenChunkClick = {
                chunkVm.onAction(ChunkAction.ReopenChunkClicked(item))
            },
            onConflictClick = onConflictClick
        )
    }
}
