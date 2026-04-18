package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.ScrollCoordinator
import com.door43.translationstudio.ui.translate.TranslateComponent
import com.door43.translationstudio.ui.translate.ScrollBindingEffect

@Composable
fun ChunkModeSection(
    component: ChunkModeComponent,
    parentComponent: TranslateComponent,
    typography: Typography,
    scrollCoordinator: ScrollCoordinator,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    onConflictClick: (String, String) -> Unit
) {
    val sharedState by parentComponent.sharedState.collectAsStateWithLifecycle()
    val chunkState by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()
    val items by component.items.collectAsStateWithLifecycle()

    ScrollBindingEffect(scrollCoordinator, items, parentComponent)

    val hasConflicts = items.any { it.hasMergeConflict }

    LaunchedEffect(hasConflicts) {
        onHasMergeConflicts(hasConflicts)
    }

    ModeScreenTemplate(
        component = component,
        items = items.toList(),
        listState = scrollCoordinator.listState,
        dialogs = {
            if (chunkState.chunkToReopen != null) {
                ConfirmDialog(
                    title = stringResource(R.string.chunk_done_title),
                    message = stringResource(R.string.chunk_done_prompt),
                    onDismiss = {
                        component.onReopenChunkConfirmed(false)
                    },
                    onConfirm = {
                        component.onReopenChunkConfirmed(true)
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
            onSourceTabClick = parentComponent::selectSource,
            onAddNewSourceClick = onSourceDialogOpen,
            onRemoveSourceClick = parentComponent::removeSource,
            onTextChange = {
                component.onItemTextChanged(item, it)
            },
            onCardsSwiped = { sourceOnTop ->
                component.onCardsSwiped(item, sourceOnTop)
            },
            onOpenChunkClick = { component.reopenChunk(item) },
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
