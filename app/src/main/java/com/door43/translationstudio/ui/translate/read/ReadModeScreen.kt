package com.door43.translationstudio.ui.translate.read

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
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.translate.components.TranslateSkeletonList
import com.door43.translationstudio.ui.viewmodels.SourceTabItem
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.unfoldingword.resourcecontainer.ResourceContainer

@Composable
fun ReadModeScreen(
    items: List<Chunk>,
    listState: LazyListState,
    sourceTabs: List<SourceTabItem>,
    selectedSource: ResourceContainer?,
    targetTranslation: TargetTranslation,
    onSourceTabClick: (String) -> Unit,
    onAddNewSourceClick: () -> Unit,
    onRemoveSourceClick: (String) -> Unit
) {
    val viewModel: ReadModeViewModel = koinViewModel()

    val model by viewModel.model.collectAsStateWithLifecycle()

    val typography: Typography = koinInject()

    LaunchedEffect(items) {
        viewModel.initialize(items)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Crossfade(
            targetState = model.items.isEmpty(),
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
                    items(items = model.items, key = { it.meta.id }) { chapter ->
                        ReadCard(
                            chapter = chapter,
                            sourceTabs = sourceTabs,
                            typography = typography,
                            selectedSource = selectedSource,
                            targetTranslation = targetTranslation,
                            onSourceTabClick = onSourceTabClick,
                            onAddNewSourceClick = onAddNewSourceClick,
                            onRemoveSourceClick = onRemoveSourceClick
                        )
                    }
                }
            }
        }
    }

    model.notes?.let { notes ->
        AlertDialog(
            onDismissRequest = { viewModel.clearNotes() },
            title = { Text(stringResource(R.string.title_footnote)) },
            text = { Text(notes) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearNotes() }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }
}