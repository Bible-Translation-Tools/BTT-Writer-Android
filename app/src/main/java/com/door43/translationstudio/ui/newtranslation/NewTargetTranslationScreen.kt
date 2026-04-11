package com.door43.translationstudio.ui.newtranslation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.SearchBar
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTargetTranslationScreen(
    onNavigateBack: () -> Unit,
    onFinishOk: () -> Unit,
    onCancel: () -> Unit,
    onDuplicate: (String) -> Unit,
    onFinishError: () -> Unit,
    onMergeConflict: (String) -> Unit
) {
    val viewModel: NewTargetTranslationModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val searchPlaceholder = when (state.screenStep) {
        ScreenStep.LANGUAGE -> stringResource(R.string.choose_target_language)
        ScreenStep.PROJECT -> stringResource(R.string.choose_a_project)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NewTranslationEvent.FinishOk,
                NewTranslationEvent.OnMergeSuccess -> onFinishOk()
                is NewTranslationEvent.FinishCanceled -> onCancel()
                is NewTranslationEvent.FinishDuplicate -> {
                    onDuplicate(event.targetTranslationId)
                }
                is NewTranslationEvent.OnMergeError,
                is NewTranslationEvent.FinishError -> onFinishError()
                is NewTranslationEvent.OnMergeConflict -> {
                    onMergeConflict(event.translationId)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.title_activity_new_target_translation))
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (state.screenStep) {
                            ScreenStep.LANGUAGE -> onNavigateBack()
                            ScreenStep.PROJECT -> viewModel.onAction(NewTranslationAction.CategoryBack)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "back",
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                },
                actions = {
                    SearchBar(
                        query = state.searchQuery,
                        onQueryChanged = {
                            viewModel.onAction(NewTranslationAction.OnSearch(it))
                        },
                        placeholder = searchPlaceholder
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
        ) {
            when (state.screenStep) {
                ScreenStep.LANGUAGE -> {
                    LanguagesList(
                        languages = state.filteredLanguages,
                        disabledLanguages = state.disabledLanguages,
                        onLanguageSelected = {
                            viewModel.onAction(NewTranslationAction.LanguageSelected(it))
                        },
                        modifier = Modifier.width(800.dp)
                    )
                }
                ScreenStep.PROJECT -> {
                    ProjectList(
                        categories = state.filteredCategories,
                        onProjectSelected = {
                            viewModel.onAction(NewTranslationAction.ProjectSelected(it))
                        },
                        onCategorySelected = {
                            viewModel.onAction(NewTranslationAction.CategorySelected(it))
                        },
                        modifier = Modifier.width(800.dp)
                    )
                }
            }
        }
    }

    state.mergeConflict?.let { conflict ->
        ConfirmDialog(
            title = stringResource(R.string.warn_existing_target_translation_label),
            message = conflict.message,
            onDismiss = {
                viewModel.onAction(NewTranslationAction.ClearMergeConflict)
            },
            onConfirm = {
                viewModel.onAction(NewTranslationAction.MergeTranslation(conflict))
            },
            confirmText = stringResource(R.string.yes),
            dismissText = stringResource(R.string.no)
        )
    }
}
