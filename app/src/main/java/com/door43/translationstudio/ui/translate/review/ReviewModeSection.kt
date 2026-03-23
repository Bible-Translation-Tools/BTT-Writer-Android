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
import com.door43.translationstudio.ui.components.ConfirmDialog
import com.door43.translationstudio.ui.translate.ModeScreenTemplate
import com.door43.translationstudio.ui.viewmodels.TargetAction
import com.door43.translationstudio.ui.viewmodels.TargetTranslationState
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ReviewModeSection(
    viewModel: TargetTranslationViewModel,
    state: TargetTranslationState,
    typography: Typography,
    listState: LazyListState,
    searchRequested: Boolean,
    onSearchConsumed: () -> Unit,
    onSourceDialogOpen: () -> Unit
) {
    val reviewVm: ReviewModeViewModel = koinViewModel {
        parametersOf(viewModel.sharedStateFlow, viewModel.eventSender)
    }
    val reviewState by reviewVm.state.collectAsStateWithLifecycle()
    val urlHandler = LocalUriHandler.current

    // Open search when requested from sidebar
    LaunchedEffect(searchRequested) {
        if (searchRequested) {
            reviewVm.onAction(ReviewAction.OpenSearch)
            onSearchConsumed()
        }
    }

    // Scroll to matching item when search navigates
    LaunchedEffect(reviewState.search.currentItemId) {
        val targetId = reviewState.search.currentItemId ?: return@LaunchedEffect
        val items = reviewVm.items.value
        val index = items.indexOfFirst { it.id == targetId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    Box {
        Column {
            if (reviewState.search.active) {
                SearchBar(
                    searchState = reviewState.search,
                    onQueryChange = {
                        reviewVm.onAction(ReviewAction.UpdateSearchQuery(it))
                    },
                    onSubjectChange = {
                        reviewVm.onAction(ReviewAction.SetSearchSubject(it))
                    },
                    onNext = {
                        reviewVm.onAction(ReviewAction.NextMatch)
                    },
                    onPrev = {
                        reviewVm.onAction(ReviewAction.PrevMatch)
                    },
                    onClose = {
                        reviewVm.onAction(ReviewAction.CloseSearch)
                    }
                )
            }
            ModeScreenTemplate(
                viewModel = reviewVm,
                listState = listState,
                dialogs = {
                    // URL handler
                    LaunchedEffect(reviewState.url) {
                        if (reviewState.url != null) {
                            urlHandler.openUri(reviewState.url!!)
                            reviewVm.onAction(ReviewAction.CleanUrl)
                        }
                    }

                    // Mark done confirmation
                    if (reviewState.chunkToDone != null) {
                        ConfirmDialog(
                            title = stringResource(R.string.chunk_checklist_title),
                            message = AnnotatedString.fromHtml(
                                stringResource(R.string.chunk_checklist_body)
                            ),
                            onDismiss = {
                                reviewVm.onAction(ReviewAction.ToggleDoneConfirmed(false))
                            },
                            onConfirm = {
                                reviewVm.onAction(ReviewAction.ToggleDoneConfirmed(true))
                            }
                        )
                    }
                }
            ) { item ->
                ReviewCard(
                    item = item,
                    sourceTabs = state.sourceTabs,
                    typography = typography,
                    resourcesOpen = reviewState.resourcesOpen,
                    onSourceTabClick = {
                        viewModel.onAction(TargetAction.SelectSource(it))
                    },
                    onAddNewSourceClick = onSourceDialogOpen,
                    onRemoveSourceClick = {
                        viewModel.onAction(TargetAction.RemoveSource(it))
                    },
                    onTextChange = {
                        reviewVm.onAction(ReviewAction.ItemTextChanged(item, it))
                    },
                    onExpandedChange = { expanded ->
                        reviewVm.onAction(ReviewAction.OpenResources(expanded))
                        if (!expanded) reviewVm.onAction(ReviewAction.ClearHelp)
                    },
                    onRenderHelps = {
                        reviewVm.onAction(ReviewAction.RenderHelps(item))
                    },
                    onHelpClick = {
                        reviewVm.onAction(ReviewAction.OpenHelp(it))
                    },
                    onEditToggle = {
                        reviewVm.onAction(ReviewAction.ToggleEdit(item))
                    },
                    onDoneToggle = {
                        reviewVm.onAction(ReviewAction.ToggleDoneClicked(item))
                    },
                    onUndoClick = {
                        reviewVm.onAction(ReviewAction.Undo(item))
                    },
                    onRedoClick = {
                        reviewVm.onAction(ReviewAction.Redo(item))
                    },
                    onAddNoteClick = { caretPos ->
                        reviewVm.onAction(ReviewAction.AddNoteClicked(item, caretPos))
                    },
                    onDragDropVerse = { machineReadable, verseRawStart, verseRawEnd, targetRawPosition ->
                        reviewVm.onAction(
                            ReviewAction.DragDropVerse(
                                item, machineReadable, verseRawStart, verseRawEnd, targetRawPosition
                            )
                        )
                    },
                    onConflictSelected = {
                        reviewVm.onAction(ReviewAction.SelectConflict(item, it))
                    },
                    searchQuery = reviewState.search.let { search ->
                        if (search.active && search.query.length >= 2
                            && search.subject == SearchSubject.TARGET
                        ) search.query else null
                    },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }

        // Help panel
        HelpPanel(
            help = reviewState.help,
            typography = typography,
            sourceLanguage = state.resourceContainer?.language,
            onClearHelp = { reviewVm.onAction(ReviewAction.ClearHelp) },
            onOpenIndex = { reviewVm.onAction(ReviewAction.OpenIndex(it)) },
            onOpenWord = { rcSlug, slug -> reviewVm.onAction(ReviewAction.OpenWord(rcSlug, slug)) },
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(1f / 3f)
                .align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun HelpPanel(
    help: Help?,
    typography: Typography,
    sourceLanguage: org.unfoldingword.resourcecontainer.Language?,
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
                    onIndexClick = { onOpenIndex(currentHelp.rcSlug) }
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
                    onItemClick = { onOpenWord(currentHelp.rcSlug, it.slug) }
                )
            }
        }
    }
}
