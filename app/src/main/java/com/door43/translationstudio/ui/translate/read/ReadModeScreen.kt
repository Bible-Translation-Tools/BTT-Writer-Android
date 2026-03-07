package com.door43.translationstudio.ui.translate.read

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.core.Chunk
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReadModeScreen(
    items: List<Chunk>
) {
    val viewModel: ReadModeViewModel = koinViewModel()

    val model by viewModel.model.collectAsStateWithLifecycle()

    LaunchedEffect(items) {
        viewModel.initialize(items)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items = model.items, key = { it.meta.id }) { chapter ->
                ReadCard(chapter)
            }
        }
    }
}