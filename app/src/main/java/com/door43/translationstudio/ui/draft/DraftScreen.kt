package com.door43.translationstudio.ui.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.components.ProgressDialog
import com.door43.translationstudio.ui.viewmodels.ChapterContent
import com.door43.translationstudio.ui.viewmodels.DraftViewModel
import com.door43.util.sortNumerically
import org.koin.androidx.compose.koinViewModel

@Composable
fun DraftScreen(
    viewModel: DraftViewModel = koinViewModel(),
    typography: Typography,
    renderingProvider: RenderingProvider,
    onFinish: () -> Unit
) {
    val model by viewModel.model.collectAsStateWithLifecycle()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf<String?>(null) }

    val draftData = remember(model.draftTranslations) {
        model.draftTranslations.firstOrNull()?.let { draft ->
            val container = viewModel.getResourceContainer(draft.resourceContainerSlug)
            val language = container?.let { viewModel.getSourceLanguage(it) }
            if (container != null && language != null) {
                Pair(container, language)
            } else null
        }
    }

    LaunchedEffect(model.importResult) {
        model.importResult?.let { result ->
            if (result.targetTranslation != null) {
                onFinish()
            } else {
                showErrorDialog = true
            }
        }
    }

    LaunchedEffect(model.draftTranslations) {
        if (model.draftTranslations.isEmpty()) {
            onFinish()
        }
    }

   Scaffold(
        floatingActionButton = {
            if (draftData != null) {
                FloatingActionButton(
                    onClick = { showConfirmDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Import Draft"
                    )
                }
            }
        }
    ) { paddingValues ->
        if (draftData != null) {
            val (container, language) = draftData
            val chapters = remember(container) {
                container.chapters().apply { sortNumerically() }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(chapters) { chapterSlug ->
                    var chapterContent by remember { mutableStateOf<ChapterContent?>(null) }

                    LaunchedEffect(chapterSlug) {
                        chapterContent = viewModel.parseChapterContent(
                            chapterSlug = chapterSlug,
                            container = container,
                            renderingProvider = renderingProvider
                        )
                    }

                    DraftChapterCard(
                        chapterContent = chapterContent,
                        language = language,
                        typography = typography,
                        onNoteClick = { notes -> showNoteDialog = notes }
                    )
                }
            }
        }
    }

    if (showConfirmDialog && draftData != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.import_draft)) },
            text = { Text(stringResource(R.string.import_draft_confirmation)) },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    viewModel.importDraft(draftData.first)
                }) {
                    Text(stringResource(R.string.label_import))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.menu_cancel))
                }
            }
        )
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(stringResource(R.string.translation_import_failed)) },
            confirmButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    showNoteDialog?.let { notes ->
        AlertDialog(
            onDismissRequest = { showNoteDialog = null },
            title = { Text(stringResource(R.string.title_footnote)) },
            text = { Text(notes) },
            confirmButton = {
                TextButton(onClick = { showNoteDialog = null }) {
                    Text(stringResource(R.string.dismiss))
                }
            }
        )
    }

    model.progress?.let { progress ->
        ProgressDialog(
            message = progress.message ?: stringResource(R.string.loading),
            progressValue = progress.progress.toFloat()
        )
    }
}