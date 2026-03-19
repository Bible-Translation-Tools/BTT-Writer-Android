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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(ModeAction.ClearFootnote)
            },
            title = { Text(stringResource(R.string.title_footnote)) },
            text = { Text(note.text) },
            confirmButton = {
                if (note.editable) {
                    TextButton(onClick = {
                        viewModel.onAction(ModeAction.DeleteNote(note))
                    }) {
                        Text(stringResource(R.string.label_delete))
                    }
                    TextButton(onClick = {
                        viewModel.onAction(ModeAction.OpenFootnoteEditor(note))
                    }) {
                        Text(stringResource(R.string.edit))
                    }
                }
                TextButton(onClick = {
                    viewModel.onAction(ModeAction.ClearFootnote)
                }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    modeState.footnoteToEdit?.let { note ->
        val textFieldState = remember { TextFieldState(note.text) }

        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(ModeAction.ClearFootnoteToEdit)
            },
            title = { Text(stringResource(R.string.add_footnote)) },
            text = {
                TextField(
                    state = textFieldState
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAction(ModeAction.ClearFootnoteToEdit)
                }) {
                    Text(stringResource(R.string.title_cancel))
                }
                TextButton(onClick = {
                    val newNote = note.copy(text = textFieldState.text.toString())
                    viewModel.onAction(ModeAction.SaveFootnote(newNote))
                }) {
                    Text(stringResource(R.string.label_ok))
                }
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