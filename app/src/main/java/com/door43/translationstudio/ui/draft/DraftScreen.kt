package com.door43.translationstudio.ui.draft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.dialogs.BaseDialog
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.util.sortNumerically
import org.koin.compose.koinInject

@Composable
fun DraftScreen(
    component: DraftComponent
) {
    val typography: Typography = koinInject()
    val renderingProvider: RenderingProvider = koinInject()

    val state by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()

    var showConfirmDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf<String?>(null) }

    val draftData = remember(state.draftTranslations) {
        state.draftTranslations.firstOrNull()?.let { draft ->
            val container = component.getResourceContainer(draft.resourceContainerSlug)
            val language = container?.let { component.getSourceLanguage(it) }
            if (container != null && language != null) {
                Pair(container, language)
            } else null
        }
    }

    LaunchedEffect(state.importResult) {
        state.importResult?.let { result ->
            if (result.targetTranslation != null) {
                component.onFinish()
            } else {
                showErrorDialog = true
            }
        }
    }

    LaunchedEffect(state.draftTranslations) {
        if (state.draftTranslations.isEmpty()) {
            component.onFinish()
        }
    }

   Scaffold(
        floatingActionButton = {
            if (draftData != null) {
                FloatingActionButton(
                    onClick = { showConfirmDialog = true }
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
                        chapterContent = component.parseChapterContent(
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
        ConfirmDialog(
            title = stringResource(R.string.import_draft),
            message = stringResource(R.string.import_draft_confirmation),
            onConfirm = {
                showConfirmDialog = false
                component.importDraft(draftData.first)
            },
            onDismiss = { showConfirmDialog = false },
            confirmText = stringResource(R.string.label_import)
        )
    }

    if (showErrorDialog) {
        BaseDialog(
            onDismiss = { showErrorDialog = false },
            title = stringResource(R.string.error),
            message = stringResource(R.string.translation_import_failed)
        ) {
            TextButton(onClick = { showErrorDialog = false }) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }

    showNoteDialog?.let { notes ->
        BaseDialog(
            onDismiss = { showNoteDialog = null },
            title = stringResource(R.string.title_footnote),
            message = notes
        ) {
            TextButton(onClick = { showNoteDialog = null }) {
                Text(stringResource(R.string.dismiss))
            }
        }
    }

    progress?.let { progress ->
        ProgressDialog(
            message = progress.message,
            progress = progress.value
        )
    }
}