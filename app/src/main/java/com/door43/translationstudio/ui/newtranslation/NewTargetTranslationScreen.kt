package com.door43.translationstudio.ui.newtranslation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.components.SearchBar
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTargetTranslationScreen(
    component: NewTranslationComponent
) {
    val state by component.state.collectAsStateWithLifecycle()
    val progress by component.progress.collectAsStateWithLifecycle()

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
                            ScreenStep.LANGUAGE -> component.navigateBack()
                            ScreenStep.PROJECT -> component.onAction(
                                NewTranslationComponent.Action.CategoryBack
                            )
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
                            component.onAction(NewTranslationComponent.Action.OnSearch(it))
                        },
                        placeholder = stringResource(R.string.search_hint)
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
            val animationKey = when (state.screenStep) {
                ScreenStep.LANGUAGE -> 0
                ScreenStep.PROJECT -> state.categoryStack.size
            }
            val forward = state.navigatingForward

            AnimatedContent(
                targetState = animationKey,
                transitionSpec = {
                    if (forward) {
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                    }
                },
                label = "screen_transition"
            ) { _ ->
                when (state.screenStep) {
                    ScreenStep.LANGUAGE -> {
                        LanguagesList(
                            languages = state.filteredLanguages,
                            disabledLanguages = state.disabledLanguages,
                            onLanguageSelected = {
                                component.onAction(
                                    NewTranslationComponent.Action.LanguageSelected(it)
                                )
                            },
                            modifier = Modifier.width(800.dp)
                        )
                    }
                    ScreenStep.PROJECT -> {
                        ProjectList(
                            categories = state.filteredCategories,
                            onProjectSelected = {
                                component.onAction(
                                    NewTranslationComponent.Action.ProjectSelected(it)
                                )
                            },
                            onCategorySelected = {
                                component.onAction(
                                    NewTranslationComponent.Action.CategorySelected(it)
                                )
                            },
                            modifier = Modifier.width(800.dp)
                        )
                    }
                }
            }
        }
    }

    state.mergeConflict?.let { conflict ->
        ConfirmDialog(
            title = stringResource(R.string.warn_existing_target_translation_label),
            message = conflict.message,
            onDismiss = {
                component.onAction(NewTranslationComponent.Action.ClearMergeConflict)
            },
            onConfirm = {
                component.onAction(NewTranslationComponent.Action.MergeTranslation(conflict))
            },
            confirmText = stringResource(R.string.yes),
            dismissText = stringResource(R.string.no)
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}
