package com.door43.translationstudio.ui.translate

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
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
    val footnote by viewModel.footnote.collectAsStateWithLifecycle()
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

    footnote?.let { note ->
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(ModeAction.ClearNotes)
            },
            title = { Text(stringResource(R.string.title_footnote)) },
            text = { Text(note.text) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAction(ModeAction.ClearNotes)
                }) {
                    Text(stringResource(R.string.dismiss))
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
                    onDismiss = { viewModel.onAction(ChunkAction.ReopenChunkConfirmed(false)) },
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
            if (state.noteHelp != null) {
                AlertDialog(
                    onDismissRequest = {
                        viewModel.onAction(ReviewAction.ClearHelp)
                    },
                    title = { Text(state.noteHelp.title) },
                    text = { Text(state.noteHelp.body) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.onAction(ReviewAction.ClearHelp)
                        }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                )
            }
            if (state.wordHelp != null) {
                val scrollState = rememberScrollState()
                AlertDialog(
                    onDismissRequest = {
                        viewModel.onAction(ReviewAction.ClearHelp)
                    },
                    title = { Text(state.wordHelp.title) },
                    text = {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                        ) {
                            Text(state.wordHelp.body)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.onAction(ReviewAction.ClearHelp)
                        }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                )
            }

            LaunchedEffect(state.url) {
                if (state.url != null) {
                    urlHandler.openUri(state.url)
                    viewModel.onAction(ReviewAction.CleanUrl)
                }
            }
        }
    }
}