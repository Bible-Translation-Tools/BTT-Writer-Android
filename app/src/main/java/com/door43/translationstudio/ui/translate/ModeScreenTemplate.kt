package com.door43.translationstudio.ui.translate

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.translate.components.TranslateSkeletonList

@Composable
fun <VM : ModeViewModel<*>,S : ModeState<ITEM>, ITEM> ModeScreenTemplate(
    state: S,
    viewModel: VM,
    listState: LazyListState,
    onInit: () -> Unit,
    itemContent: @Composable (ITEM) -> Unit
) {

    val footnote by viewModel.footnote.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        onInit()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = state.items.isEmpty(),
            animationSpec = tween(durationMillis = 500),
            label = "list_fade"
        ) { isLoading ->
            if (isLoading) {
                TranslateSkeletonList()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items = state.items) { item ->
                        itemContent(item)
                    }
                }
            }
        }
    }

    footnote?.let { note ->
        AlertDialog(
            onDismissRequest = { viewModel.onSharedAction(ModeAction.ClearNotes) },
            title = { Text(stringResource(R.string.title_footnote)) },
            text = { Text(note.text) },
            confirmButton = {
                TextButton(onClick = { viewModel.onSharedAction(ModeAction.ClearNotes) }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }
}