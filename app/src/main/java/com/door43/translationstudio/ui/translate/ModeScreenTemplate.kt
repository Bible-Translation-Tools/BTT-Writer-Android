package com.door43.translationstudio.ui.translate

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.ConfirmDialog
import com.door43.translationstudio.ui.translate.chunk.ChunkAction
import com.door43.translationstudio.ui.translate.chunk.ChunkState
import com.door43.translationstudio.ui.translate.components.TranslateSkeletonList
import com.door43.translationstudio.ui.translate.dialogs.FootnoteDialog
import com.door43.translationstudio.ui.translate.dialogs.FootnoteDialogType
import com.door43.translationstudio.ui.translate.review.ReviewAction
import com.door43.translationstudio.ui.translate.review.ReviewState

@Composable
fun <S : ModeState, ITEM : TranslateItem> ModeScreenTemplate(
    state: S,
    viewModel: ModeViewModel<ITEM>,
    listState: LazyListState,
    itemContent: @Composable (ITEM) -> Unit
) {
    val modeState by viewModel.modeState.collectAsStateWithLifecycle()
    val stateItems by viewModel.items.collectAsStateWithLifecycle()

    val urlHandler = LocalUriHandler.current

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = stateItems.isEmpty(),
            animationSpec = tween(durationMillis = 500),
            label = "list_fade"
        ) { isLoading ->
            if (isLoading) {
                TranslateSkeletonList()
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items = stateItems, key = { it.id }) { item ->
                        itemContent(item)
                    }
                }
            }
        }
    }

    modeState.footnote?.let { note ->
        FootnoteDialog(
            title = stringResource(R.string.title_footnote),
            text = note.text,
            type = if (note.editable) FootnoteDialogType.ACT else FootnoteDialogType.VIEW,
            onDismissRequest = {
                viewModel.onAction(ModeAction.ClearFootnote)
            },
            onDeleteNote = {
                viewModel.onAction(ModeAction.DeleteNote(note))
            },
            onEditNote = {
                viewModel.onAction(ModeAction.OpenFootnoteEditor(note))
            }
        )
    }

    modeState.footnoteToEdit?.let { note ->
        FootnoteDialog(
            title = stringResource(R.string.title_add_footnote),
            text = note.text,
            type = FootnoteDialogType.EDIT,
            onDismissRequest = {
                viewModel.onAction(ModeAction.ClearFootnoteToEdit)
            },
            onSaveText = { newText ->
                val newNote = note.copy(text = newText)
                viewModel.onAction(ModeAction.SaveFootnote(newNote))
            }
        )
    }

    when (state) {
        is ChunkState -> {
            if (state.chunkToReopen != null) {
                ConfirmDialog(
                    title = stringResource(R.string.chunk_done_title),
                    message = stringResource(R.string.chunk_done_prompt),
                    onDismiss = {
                        viewModel.onAction(ChunkAction.ReopenChunkConfirmed(false))
                    },
                    onConfirm = {
                        viewModel.onAction(
                            ChunkAction.ReopenChunkConfirmed(true)
                        )
                    },
                    confirmText = stringResource(R.string.edit)
                )
            }
        }
        is ReviewState -> {
            LaunchedEffect(state.url) {
                if (state.url != null) {
                    urlHandler.openUri(state.url)
                    viewModel.onAction(ReviewAction.CleanUrl)
                }
            }

            if (state.chunkToDone != null) {
                ConfirmDialog(
                    title = stringResource(R.string.chunk_checklist_title),
                    message = AnnotatedString.fromHtml(
                        stringResource(R.string.chunk_checklist_body)
                    ),
                    onDismiss = {
                        viewModel.onAction(ReviewAction.ToggleDoneConfirmed(false))
                    },
                    onConfirm = {
                        viewModel.onAction(
                            ReviewAction.ToggleDoneConfirmed(true)
                        )
                    }
                )
            }
        }
    }
}