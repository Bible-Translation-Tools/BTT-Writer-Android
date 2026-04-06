package com.door43.translationstudio.ui.translate.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.dialogs.ConfirmDialog
import com.door43.translationstudio.ui.dialogs.InfoDialog
import com.door43.translationstudio.ui.dialogs.ProgressDialog
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.translate.TargetAction
import com.door43.translationstudio.ui.translate.TargetTranslationViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import org.unfoldingword.resourcecontainer.Language

@Composable
fun ReviewModeSection(
    translationViewModel: TargetTranslationViewModel,
    typography: Typography,
    listState: LazyListState,
    searchRequested: Boolean,
    onSearchConsumed: () -> Unit,
    onSourceDialogOpen: () -> Unit,
    onHasMergeConflicts: (Boolean) -> Unit,
    mergeConflictFilterOn: Boolean = false,
    onMergeConflictFilterReset: () -> Unit,
    chunksDoneRequested: Boolean,
    onChunksDoneConsumed: () -> Unit
) {
    val viewModel: ReviewModeViewModel = koinViewModel {
        parametersOf(translationViewModel.sharedState, translationViewModel.eventSender)
    }

    val sharedState by translationViewModel.sharedState.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val filteredItems by viewModel.filteredItems.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val urlHandler = LocalUriHandler.current

    val hasConflicts = items.any { it.hasMergeConflict }

    // Auto-disable conflict filter when no conflicts remain
    LaunchedEffect(hasConflicts) {
        onHasMergeConflicts(hasConflicts)
        if (items.isNotEmpty() && !hasConflicts && mergeConflictFilterOn) {
            onMergeConflictFilterReset()
            viewModel.onAction(ReviewAction.SetMergeConflictFilterOn(false))
        }
    }

    LaunchedEffect(mergeConflictFilterOn) {
        viewModel.onAction(ReviewAction.SetMergeConflictFilterOn(
            mergeConflictFilterOn
        ))
    }

    // Open search when requested from sidebar
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            viewModel.onAction(ReviewAction.OpenSearch)
            onSearchConsumed()
        }
    }

    // Scroll to matching item when search navigates
    LaunchedEffect(state.search?.currentItemId) {
        val targetId = state.search?.currentItemId ?: return@LaunchedEffect
        val index = filteredItems.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    LaunchedEffect(chunksDoneRequested) {
        if (chunksDoneRequested) {
            viewModel.onAction(ReviewAction.MarkAllDoneClicked)
            onChunksDoneConsumed()
        }
    }

    Box {
        Column {
            state.search?.let { search ->
                SearchBar(
                    searchState = search,
                    onQueryChange = {
                        viewModel.onAction(ReviewAction.UpdateSearchQuery(it))
                    },
                    onSubjectChange = {
                        viewModel.onAction(ReviewAction.SetSearchSubject(it))
                    },
                    onNext = {
                        viewModel.onAction(ReviewAction.NextMatch)
                    },
                    onPrev = {
                        viewModel.onAction(ReviewAction.PrevMatch)
                    },
                    onClose = {
                        viewModel.onAction(ReviewAction.CloseSearch)
                    }
                )
            }
            ModeScreenTemplate(
                viewModel = viewModel,
                items = filteredItems,
                listState = listState,
                dialogs = {
                    // URL handler
                    LaunchedEffect(state.url) {
                        if (state.url != null) {
                            urlHandler.openUri(state.url!!)
                            viewModel.onAction(ReviewAction.CleanUrl)
                        }
                    }

                    // Mark done confirmation
                    if (state.chunkToDone != null) {
                        ConfirmDialog(
                            title = stringResource(R.string.chunk_checklist_title),
                            message = AnnotatedString.fromHtml(
                                stringResource(R.string.chunk_checklist_body)
                            ),
                            onDismiss = {
                                viewModel.onAction(ReviewAction.ToggleDoneConfirmed(false))
                            },
                            onConfirm = {
                                viewModel.onAction(ReviewAction.ToggleDoneConfirmed(true))
                            }
                        )
                    }

                    // Mark all chunks done confirmation
                    when (val dialogState = state.markAllDoneState) {
                        is MarkAllDialogState.Confirm -> {
                            ConfirmDialog(
                                title = stringResource(R.string.project_checklist_title),
                                message = AnnotatedString.fromHtml(
                                    stringResource(R.string.project_checklist_body)
                                ),
                                onDismiss = {
                                    viewModel.onAction(
                                        ReviewAction.MarkAllDoneConfirmed(false)
                                    )
                                },
                                onConfirm = {
                                    viewModel.onAction(
                                        ReviewAction.MarkAllDoneConfirmed(true)
                                    )
                                }
                            )
                        }
                        is MarkAllDialogState.Result -> {
                            InfoDialog(
                                onDismiss = {
                                    viewModel.onAction(
                                        ReviewAction.MarkAllDoneConfirmed(false)
                                    )
                                },
                                title = stringResource(R.string.result),
                                message = AnnotatedString.fromHtml(
                                    stringResource(
                                        id = R.string.mark_chunks_done_result,
                                        dialogState.marked,
                                        dialogState.total
                                    )
                                ).text,
                                buttons = { onDismiss ->
                                    TextButton(onClick = onDismiss) {
                                        Text(stringResource(R.string.dismiss))
                                    }
                                }
                            )
                        }
                        null -> { /* No dialog */ }
                    }
                }
            ) { item ->
                ReviewCard(
                    item = item,
                    sourceTabs = sharedState.sourceTabs,
                    typography = typography,
                    resourcesOpen = state.resourcesOpen,
                    onSourceTabClick = {
                        translationViewModel.onAction(TargetAction.SelectSource(it))
                    },
                    onAddNewSourceClick = onSourceDialogOpen,
                    onRemoveSourceClick = {
                        translationViewModel.onAction(TargetAction.RemoveSource(it))
                    },
                    onTextChange = {
                        viewModel.onAction(ReviewAction.ItemTextChanged(item, it))
                    },
                    onExpandedChange = { expanded ->
                        viewModel.onAction(ReviewAction.OpenResources(expanded))
                        if (!expanded) viewModel.onAction(ReviewAction.ClearHelp)
                    },
                    onRenderHelps = {
                        viewModel.onAction(ReviewAction.RenderHelps(item))
                    },
                    onHelpClick = {
                        viewModel.onAction(ReviewAction.OpenHelp(it))
                    },
                    onEditToggle = {
                        viewModel.onAction(ReviewAction.ToggleEdit(item))
                    },
                    onDoneToggle = {
                        viewModel.onAction(ReviewAction.ToggleDoneClicked(item))
                    },
                    onUndoClick = {
                        viewModel.onAction(ReviewAction.Undo(item))
                    },
                    onRedoClick = {
                        viewModel.onAction(ReviewAction.Redo(item))
                    },
                    onAddNoteClick = { caretPos ->
                        viewModel.onAction(ReviewAction.AddNoteClicked(item, caretPos))
                    },
                    onDragDropVerse = { machineReadable, verseRawStart, verseRawEnd, targetRawPosition ->
                        viewModel.onAction(
                            ReviewAction.DragDropVerse(
                                item, machineReadable, verseRawStart, verseRawEnd, targetRawPosition
                            )
                        )
                    },
                    onConflictSelected = {
                        viewModel.onAction(ReviewAction.SelectConflict(item, it))
                    },
                    searchQuery = state.search?.let { search ->
                        if (search.query.length >= 2 && search.subject == SearchSubject.TARGET) {
                            search.query
                        } else null
                    },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        HelpPanel(
            help = state.help,
            typography = typography,
            sourceLanguage = sharedState.resourceContainer?.language,
            onClearHelp = { viewModel.onAction(ReviewAction.ClearHelp) },
            onOpenIndex = { viewModel.onAction(ReviewAction.OpenIndex(it)) },
            onOpenWord = { rcSlug, slug ->
                viewModel.onAction(ReviewAction.OpenWord(rcSlug, slug))
            },
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f / 3.08f)
                .align(Alignment.CenterEnd)
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}

@Composable
private fun HelpPanel(
    help: Help?,
    typography: Typography,
    sourceLanguage: Language?,
    onClearHelp: () -> Unit,
    onOpenIndex: (rcSlug: String) -> Unit,
    onOpenWord: (rcSlug: String, slug: String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = help != null,
        modifier = modifier,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
        )
    ) {
        help?.let { currentHelp ->
            when (currentHelp) {
                is Help.Notes -> NotesCard(
                    title = currentHelp.title,
                    body = currentHelp.body,
                    typography = typography,
                    sourceLanguage = sourceLanguage,
                    onClose = onClearHelp
                )
                is Help.Words -> WordsCard(
                    title = currentHelp.title,
                    body = currentHelp.body,
                    typography = typography,
                    sourceLanguage = sourceLanguage,
                    onCloseClick = onClearHelp,
                    onIndexClick = {
                        onOpenIndex(currentHelp.rcSlug)
                    }
                )
                is Help.Questions -> QuestionsCard(
                    title = currentHelp.title,
                    body = currentHelp.body,
                    typography = typography,
                    sourceLanguage = sourceLanguage,
                    onClose = onClearHelp
                )
                is Help.Index -> IndexCard(
                    words = currentHelp.words,
                    typography = typography,
                    sourceLanguage = sourceLanguage,
                    onCloseClick = onClearHelp,
                    onItemClick = {
                        onOpenWord(currentHelp.rcSlug, it.slug)
                    }
                )
            }
        }
    }
}
