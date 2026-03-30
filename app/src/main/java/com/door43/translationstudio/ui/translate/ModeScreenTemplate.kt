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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.CardsSkeletonList
import com.door43.translationstudio.ui.translate.dialogs.FootnoteDialog
import com.door43.translationstudio.ui.translate.dialogs.FootnoteDialogType

@Composable
fun <ITEM : TranslateItem> ModeScreenTemplate(
    viewModel: ModeViewModel<ITEM>,
    items: List<ITEM>,
    listState: LazyListState,
    dialogs: @Composable () -> Unit = {},
    itemContent: @Composable (ITEM) -> Unit
) {
    val modeState by viewModel.modeState.collectAsStateWithLifecycle()

    var settingsVersion by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        settingsVersion++
        onPauseOrDispose {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = items.isEmpty(),
            animationSpec = tween(durationMillis = 500),
            label = "list_fade"
        ) { isLoading ->
            if (isLoading) {
                CardsSkeletonList()
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items = items, key = { it.id }) { item ->
                        key(settingsVersion) {
                            itemContent(item)
                        }
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

    dialogs()
}
