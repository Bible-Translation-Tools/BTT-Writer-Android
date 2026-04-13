package com.door43.translationstudio.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.SearchBar
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.InfoDialog
import com.door43.translationstudio.ui.dialogs.OverlayDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.newtranslation.LanguagesList
import com.door43.translationstudio.ui.newtranslation.ProjectList
import org.koin.androidx.compose.koinViewModel
import org.unfoldingword.door43client.models.CategoryEntry
import org.unfoldingword.door43client.models.TargetLanguage

@Composable
fun UsfmImportDialog(
    uri: Uri,
    onProjectsImported: (List<String>) -> Unit,
    onMergeConflict: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: UsfmImportViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val onCloseDialog: () -> Unit = {
        viewModel.onAction(UsfmAction.Cleanup)
        onDismiss()
    }

    LaunchedEffect(uri) {
        viewModel.startImport(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is UsfmEvent.ProjectsImported -> onProjectsImported(event.translationIds)
                is UsfmEvent.ResolveMergeConflict -> onMergeConflict(event.translationId)
            }
        }
    }

    if (!state.started) return

    when (state.step) {
        UsfmStep.LANGUAGE -> {
            UsfmLanguageSelectionDialog(
                languages = state.filteredLanguages,
                onLanguageSelected = {
                    viewModel.onAction(UsfmAction.LanguageSelected(it))
                },
                onSearch = {
                    viewModel.onAction(UsfmAction.Search(it))
                },
                onDismiss = onCloseDialog
            )
        }

        UsfmStep.PROMPT_BOOK_NAME -> {
            UsfmBookNameDialog(
                prompt = state.missingNamePrompt ?: "",
                description = state.currentMissingDescription,
                categories = state.filteredCategories,
                isAtRootCategory = state.categoryStack.size <= 1,
                onProjectSelected = {
                    viewModel.onAction(UsfmAction.BookSelected(it))
                },
                onCategorySelected = {
                    viewModel.onAction(UsfmAction.CategorySelected(it))
                },
                onNavigateBack = {
                    viewModel.onAction(UsfmAction.NavigateBack)
                },
                onSkip = { viewModel.onAction(UsfmAction.SkipBook) }
            )
        }

        UsfmStep.PROCESSED -> {
            if (state.existentTranslations.isNotEmpty()) {
                UsfmMergeConflictDialog(
                    message = state.processedResult,
                    conflictIds = state.existentTranslations.map { it.id },
                    onMerge = {
                        viewModel.onAction(UsfmAction.MergeImport(false))
                    },
                    onOverwrite = {
                        viewModel.onAction(UsfmAction.MergeImport(true))
                    },
                    onCancel = onCloseDialog
                )
            } else {
                ConfirmDialog(
                    title = stringResource(R.string.title_processing_usfm_summary),
                    message = state.processedResult,
                    onConfirm = { viewModel.onAction(UsfmAction.ConfirmImport) },
                    onDismiss = onCloseDialog,
                    confirmText = stringResource(R.string.label_continue),
                    dismissText = stringResource(R.string.menu_cancel)
                )
            }
        }

        UsfmStep.DONE -> {
            InfoDialog(
                title = stringResource(
                    if (state.importSuccess) R.string.title_import_usfm_results
                    else R.string.title_import_usfm_error
                ),
                message = stringResource(
                    if (state.importSuccess) R.string.import_usfm_success
                    else R.string.import_usfm_failed
                ),
                onDismiss = {
                    if (state.importSuccess) {
                        viewModel.onAction(UsfmAction.ProjectsImported(state.importedTranslationIds))
                    } else {
                        onCloseDialog()
                    }
                }
            ) { onInfoDismiss ->
                TextButton(onClick = onInfoDismiss) {
                    Text(stringResource(R.string.label_continue))
                }
            }
        }
    }

    state.infoMessage?.let { (title, message) ->
        InfoDialog(
            title = title,
            message = message,
            onDismiss = onCloseDialog
        ) { onInfoDismiss ->
            TextButton(onClick = onInfoDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
        return
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsfmLanguageSelectionDialog(
    languages: List<TargetLanguage>,
    onLanguageSelected: (TargetLanguage) -> Unit,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    OverlayDialog(
        onDismiss = onDismiss,
        maxWidth = 900.dp,
        maxHeight = 900.dp,
        contentPadding = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.title_activity_import_usfm_language))
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "back"
                        )
                    }
                },
                actions = {
                    SearchBar(
                        query = searchQuery,
                        onQueryChanged = {
                            searchQuery = it
                            onSearch(it)
                        },
                        placeholder = stringResource(R.string.search_for_language)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                LanguagesList(
                    languages = languages,
                    disabledLanguages = emptyList(),
                    onLanguageSelected = onLanguageSelected,
                    modifier = Modifier.width(800.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsfmBookNameDialog(
    prompt: String,
    description: String,
    categories: List<CategoryEntry>,
    isAtRootCategory: Boolean,
    onProjectSelected: (String) -> Unit,
    onCategorySelected: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onSkip: () -> Unit
) {
    var showProjectList by rememberSaveable { mutableStateOf(false) }

    if (!showProjectList) {
        ConfirmDialog(
            title = stringResource(R.string.title_activity_import_usfm_language),
            message = prompt,
            onConfirm = { showProjectList = true },
            onDismiss = onSkip,
            confirmText = stringResource(R.string.label_continue),
            dismissText = stringResource(R.string.menu_cancel)
        )
    } else {
        OverlayDialog(
            onDismiss = {
                if (isAtRootCategory) {
                    showProjectList = false
                } else {
                    onNavigateBack()
                }
            },
            maxWidth = 900.dp,
            maxHeight = 900.dp,
            contentPadding = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                R.string.title_activity_import_usfm_book,
                                description
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isAtRootCategory) {
                                showProjectList = false
                            } else {
                                onNavigateBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProjectList(
                        categories = categories,
                        onProjectSelected = onProjectSelected,
                        onCategorySelected = { onCategorySelected(it) },
                        modifier = Modifier.width(800.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UsfmMergeConflictDialog(
    message: String,
    conflictIds: List<String>,
    onMerge: () -> Unit,
    onOverwrite: () -> Unit,
    onCancel: () -> Unit
) {
    val warning = conflictIds.singleOrNull()?.let { conflictId ->
        stringResource(
            R.string.import_merge_conflict_project_name,
            conflictId
        )
    } ?: stringResource(R.string.import_merge_conflicts)

    val fullMessage = "$message\n$warning"

    InfoDialog(
        title = stringResource(R.string.merge_conflict_title),
        message = fullMessage,
        onDismiss = onCancel
    ) { onInfoDismiss ->
        TextButton(onClick = onMerge) {
            Text(stringResource(R.string.merge_projects_label))
        }
        TextButton(onClick = onOverwrite) {
            Text(stringResource(R.string.overwrite_projects_label))
        }
        TextButton(onClick = {
            onCancel()
            onInfoDismiss()
        }) {
            Text(stringResource(R.string.title_cancel))
        }
    }
}
