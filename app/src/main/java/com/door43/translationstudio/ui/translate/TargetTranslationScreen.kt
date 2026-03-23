package com.door43.translationstudio.ui.translate

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.components.ConfirmDialog
import com.door43.translationstudio.ui.components.ProgressDialog
import com.door43.translationstudio.ui.translate.chunk.ChunkAction
import com.door43.translationstudio.ui.translate.chunk.ChunkCard
import com.door43.translationstudio.ui.translate.chunk.ChunkModeViewModel
import com.door43.translationstudio.ui.translate.components.NoSourceScreen
import com.door43.translationstudio.ui.translate.components.TranslateSideBar
import com.door43.translationstudio.ui.translate.components.TranslateSideBarAction
import com.door43.translationstudio.ui.translate.dialogs.SourceSelectionDialog
import com.door43.translationstudio.ui.translate.read.ReadCard
import com.door43.translationstudio.ui.translate.read.ReadModeViewModel
import com.door43.translationstudio.ui.translate.review.Help
import com.door43.translationstudio.ui.translate.review.IndexCard
import com.door43.translationstudio.ui.translate.review.NotesCard
import com.door43.translationstudio.ui.translate.review.QuestionsCard
import com.door43.translationstudio.ui.translate.review.ReviewAction
import com.door43.translationstudio.ui.translate.review.ReviewCard
import com.door43.translationstudio.ui.translate.review.ReviewModeViewModel
import com.door43.translationstudio.ui.translate.review.SearchBar
import com.door43.translationstudio.ui.translate.review.SearchSubject
import com.door43.translationstudio.ui.translate.review.WordsCard
import com.door43.translationstudio.ui.viewmodels.TargetAction
import com.door43.translationstudio.ui.viewmodels.TargetEvent
import com.door43.translationstudio.ui.viewmodels.TargetTranslationState
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun TargetTranslationScreen(
    viewModel: TargetTranslationViewModel = koinViewModel(),
    onHomeClick: () -> Unit,
    onNavigateToDraft: () -> Unit,
    onProjectPreview: () -> Unit,
    onUploadExport: () -> Unit,
    onPrint: () -> Unit,
    onFeedback: () -> Unit,
    onChunksDone: () -> Unit,
    onSettings: () -> Unit,
    onRestartAutoCommitTimer: () -> Unit,
    onUpdateSources: () -> Unit
) {
    val typography: Typography = koinInject()
    val context = LocalContext.current

    val state: TargetTranslationState by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val menuItems = remember { mutableStateListOf<TranslateSideBarAction>() }

    // Strings
    val draftExistsStr = stringResource(R.string.draft_translation_exists)
    val draftPreview = stringResource(R.string.preview)

    var showUpdateSourcesDialog by rememberSaveable { mutableStateOf(false) }
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var searchRequested by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var lastViewedChunk by remember { mutableStateOf<Chunk?>(null) }
    var hasDoneInitialLoad by rememberSaveable { mutableStateOf(false) }
    var pendingScrollChapter by remember { mutableStateOf<String?>(null) }
    var sliderChapterLabel by remember { mutableStateOf<String?>(null) }

    val dominantIndex by remember(state.items) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || state.items.isEmpty()) return@derivedStateOf 0

            val dominantItem = visibleItems.find { it.offset > -(it.size / 2) }
            val rawIndex = dominantItem?.index ?: visibleItems.first().index

            // Coerce protects us if the list suddenly shrinks before Compose finishes updating
            rawIndex.coerceIn(0, maxOf(0, state.items.size - 1))
        }
    }

    val currentSliderValue by remember(state.items) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            if (visibleItems.isEmpty() || state.items.isEmpty()) return@derivedStateOf 0f

            val firstItem = visibleItems.first()
            val scrolledPixels = -firstItem.offset
            val itemFraction = (scrolledPixels.toFloat() / firstItem.size.toFloat()).coerceIn(0f, 1f)

            val absolutePosition = firstItem.index + itemFraction
            (absolutePosition / state.items.size).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(state.items, state.lastFocusChapterId) {
        if (!hasDoneInitialLoad && state.items.isNotEmpty() && state.lastFocusChapterId != null) {
            // Try to find the exact chunk
            var targetIndex = state.items.indexOfFirst {
                it.chapterSlug == state.lastFocusChapterId && it.chunkSlug == state.lastFocusFrameId
            }
            // Fallback: If in READ mode,
            // the specific chunkId might be filtered out. Find the chapter.
            if (targetIndex == -1) {
                targetIndex = state.items.indexOfFirst { it.chapterSlug == state.lastFocusChapterId }
            }

            if (targetIndex != -1) {
                listState.scrollToItem(targetIndex)
                lastViewedChunk = state.items[targetIndex]
                hasDoneInitialLoad = true
            }
        }
    }

    LaunchedEffect(pendingScrollChapter) {
        val scrollTarget = pendingScrollChapter ?: return@LaunchedEffect

        // Wait for state.items to contain the target chapter
        snapshotFlow { state.items }
            .first { items -> items.any { it.chapterSlug == scrollTarget } }

        val targetIndex = state.items.indexOfFirst { it.chapterSlug == scrollTarget }
        if (targetIndex != -1) {
            // Wait for the LazyList to actually have enough items rendered
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > targetIndex }

            listState.scrollToItem(targetIndex)
            lastViewedChunk = state.items[targetIndex]
        }
        pendingScrollChapter = null
    }

    LaunchedEffect(state.items) {
        val chunkToFind = lastViewedChunk
        if (hasDoneInitialLoad && chunkToFind != null && state.items.isNotEmpty()
            && pendingScrollChapter == null
        ) {
            // Try exact match first
            var newIndex = state.items.indexOfFirst {
                it.chapterSlug == chunkToFind.chapterSlug && it.chunkSlug == chunkToFind.chunkSlug
            }

            // Fallback: If we switched to READ mode,
            // the exact chunk might be gone. Match by chapter.
            if (newIndex == -1) {
                newIndex = state.items.indexOfFirst { it.chapterSlug == chunkToFind.chapterSlug }
            }

            if (newIndex != -1) {
                listState.scrollToItem(newIndex)
            }
        }
    }

    LaunchedEffect(dominantIndex, state.items) {
        if (state.items.isNotEmpty() && pendingScrollChapter == null) {
            val chunk = state.items[dominantIndex]
            lastViewedChunk = chunk // Update our memory for the next mode swap
            viewModel.onAction(
                TargetAction.SaveLastFocus(chunk.chapterSlug, chunk.chunkSlug)
            )
        }
    }

    LaunchedEffect(state.draftAvailable, state.viewMode) {
        menuItems.clear()
        menuItems.addAll(
            buildMenu(
                context = context,
                viewMode = state.viewMode,
                draftAvailable = state.draftAvailable,
                onHomeClick = onHomeClick,
                onNavigateToDraft = onNavigateToDraft,
                onProjectPreview = onProjectPreview,
                onUploadExport = onUploadExport,
                onPrint = onPrint,
                onFeedback = onFeedback,
                onChunksDone = onChunksDone,
                onSettings = onSettings,
                onSearchRequested = { searchRequested = true }
            )
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is TargetEvent.ShowMessage -> {
                    snackBarHostState.showSnackbar(event.message)
                }
                TargetEvent.RestartAutoCommitTimer -> {
                    onRestartAutoCommitTimer()
                }
            }

        }
    }

    LaunchedEffect(state.showDraftAvailable) {
        if (state.showDraftAvailable) {
            scope.launch {
                val result = snackBarHostState.showSnackbar(
                    message = draftExistsStr,
                    actionLabel = draftPreview,
                    duration = SnackbarDuration.Long
                )
                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        onNavigateToDraft()
                    }
                    else -> {}
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackBarHostState) {
                Snackbar(
                    snackbarData = it,
                    actionColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier.padding(paddingValues)
        ) {
            TranslateSideBar(
                currentViewMode = state.viewMode,
                showMergeConflict = false, // TODO model.items.any { it.hasMergeConflicts },
                onReadClick = {
                    if (state.viewMode != TranslationViewMode.READ) {
                        viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.READ))
                    }
                },
                onChunkClick = {
                    if (state.viewMode != TranslationViewMode.CHUNK) {
                        viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.CHUNK))
                    }
                },
                onReviewClick = {
                    // TODO Should reset conflict items filter
                    if (state.viewMode != TranslationViewMode.REVIEW) {
                        viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.REVIEW))
                    }
                },
                onMergeConflictClick = {
                    // TODO Should toggle conflict items filter
                    viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.REVIEW))
                },
                onSliderValueChange = {
                    val exactPosition = it * state.items.size
                    val targetIndex = exactPosition.toInt().coerceIn(0, state.items.size - 1)
                    val fraction = exactPosition - targetIndex

                    if (state.items.isNotEmpty()) {
                        val slug = state.items[targetIndex].chapterSlug
                        sliderChapterLabel = slug.toIntOrNull()?.toString() ?: slug
                    }

                    // THE GUESS: Because the target chapter isn't on screen yet,
                    // we don't know its height.
                    // We have to guess the offset based on the screen height.
                    // Here we assume an average chapter is about 3 screens tall.
                    val screenHeight = listState.layoutInfo.viewportSize.height
                    val estimatedOffsetPixels = (fraction * (screenHeight * 3)).toInt()

                    scope.launch {
                        listState.scrollToItem(targetIndex, estimatedOffsetPixels)
                    }
                },
                sliderValue = currentSliderValue,
                chapterLabel = sliderChapterLabel,
                actions = menuItems
            )

            Box(modifier = Modifier.weight(1f)) {
                if (state.items.isEmpty()) {
                    NoSourceScreen(
                        projectTitle = state.projectTitle ?: "",
                        onAddSourceClick = { showSourceDialog = true }
                    )
                } else {
                    when (state.viewMode) {
                        TranslationViewMode.READ -> {
                            val readVm: ReadModeViewModel = koinViewModel {
                                parametersOf(viewModel.sharedStateFlow, viewModel.eventSender)
                            }
                            val readState by readVm.state.collectAsStateWithLifecycle()

                            ModeScreenTemplate(
                                state = readState,
                                viewModel = readVm,
                                listState = listState
                            ) { item ->
                                ReadCard(
                                    item = item,
                                    sourceTabs = state.sourceTabs,
                                    typography = typography,
                                    selectedSource = state.resourceContainer,
                                    targetTranslation = viewModel.targetTranslation,
                                    onSourceTabClick = {
                                        viewModel.onAction(TargetAction.SelectSource(it))
                                    },
                                    onAddNewSourceClick = { showSourceDialog = true },
                                    onRemoveSourceClick = {
                                        viewModel.onAction(TargetAction.RemoveSource(it))
                                    },
                                    onCardsSwiped = {
                                        readVm.onAction(
                                            ModeAction.CardsSwiped(item, it)
                                        )
                                    },
                                    onBeginTranslation = {
                                        pendingScrollChapter = it
                                        viewModel.onAction(
                                            TargetAction.SaveLastViewMode(TranslationViewMode.CHUNK)
                                        )
                                    }
                                )
                            }
                        }
                        TranslationViewMode.CHUNK -> {
                            val chunkVm: ChunkModeViewModel = koinViewModel {
                                parametersOf(viewModel.sharedStateFlow, viewModel.eventSender)
                            }
                            val chunkState by chunkVm.state.collectAsStateWithLifecycle()

                            ModeScreenTemplate(
                                state = chunkState,
                                viewModel = chunkVm,
                                listState = listState
                            ) { item ->
                                ChunkCard(
                                    item = item,
                                    sourceTabs = state.sourceTabs,
                                    typography = typography,
                                    selectedSource = state.resourceContainer,
                                    targetTranslation = viewModel.targetTranslation,
                                    onSourceTabClick = {
                                        viewModel.onAction(TargetAction.SelectSource(it))
                                    },
                                    onAddNewSourceClick = { showSourceDialog = true },
                                    onRemoveSourceClick = {
                                        viewModel.onAction(TargetAction.RemoveSource(it))
                                    },
                                    onTextChange = {
                                        chunkVm.onAction(
                                            ChunkAction.ItemTextChanged(item, it)
                                        )
                                    },
                                    onCardsSwiped = { sourceOnTop ->
                                        chunkVm.onAction(
                                            ModeAction.CardsSwiped(item, sourceOnTop)
                                        )
                                    },
                                    onOpenChunkClick = {
                                        chunkVm.onAction(ChunkAction.ReopenChunkClicked(item))
                                    }
                                )
                            }
                        }
                        TranslationViewMode.REVIEW -> {
                            val reviewVm: ReviewModeViewModel = koinViewModel {
                                parametersOf(
                                    viewModel.sharedStateFlow,
                                    viewModel.eventSender
                                )
                            }
                            val reviewState by reviewVm.state.collectAsStateWithLifecycle()

                            // Open search when requested from sidebar
                            LaunchedEffect(searchRequested) {
                                if (searchRequested) {
                                    reviewVm.onAction(ReviewAction.OpenSearch)
                                    searchRequested = false
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
                                        state = reviewState,
                                        viewModel = reviewVm,
                                        listState = listState
                                    ) { item ->
                                        ReviewCard(
                                            item = item,
                                            sourceTabs = state.sourceTabs,
                                            typography = typography,
                                            resourcesOpen = reviewState.resourcesOpen,
                                            onSourceTabClick = {
                                                viewModel.onAction(TargetAction.SelectSource(it))
                                            },
                                            onAddNewSourceClick = { showSourceDialog = true },
                                            onRemoveSourceClick = {
                                                viewModel.onAction(TargetAction.RemoveSource(it))
                                            },
                                            onTextChange = {
                                                reviewVm.onAction(
                                                    ReviewAction.ItemTextChanged(item, it)
                                                )
                                            },
                                            onExpandedChange = { expanded ->
                                                reviewVm.onAction(
                                                    ReviewAction.OpenResources(expanded)
                                                )
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
                                                reviewVm.onAction(ReviewAction.DragDropVerse(
                                                    item, machineReadable, verseRawStart, verseRawEnd, targetRawPosition
                                                ))
                                            },
                                            onConflictSelected = {
                                                reviewVm.onAction(
                                                    ReviewAction.SelectConflict(item, it)
                                                )
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

                                val helpVisible = reviewState.help != null

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = helpVisible,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(1f / 3f)
                                        .align(Alignment.CenterEnd),
                                    enter = slideInHorizontally(
                                        initialOffsetX = { it },
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy
                                        )
                                    ),
                                    exit = slideOutHorizontally(
                                        targetOffsetX = { it },
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy
                                        )
                                    )
                                ) {
                                    reviewState.help?.let { help ->
                                        when (help) {
                                            is Help.Notes -> NotesCard(
                                                title = help.title,
                                                body = help.body,
                                                typography = typography,
                                                sourceLanguage = state.resourceContainer?.language,
                                                onClose = {
                                                    reviewVm.onAction(ReviewAction.ClearHelp)
                                                }
                                            )
                                            is Help.Words -> WordsCard(
                                                title = help.title,
                                                body = help.body,
                                                typography = typography,
                                                sourceLanguage = state.resourceContainer?.language,
                                                onCloseClick = {
                                                    reviewVm.onAction(ReviewAction.ClearHelp)
                                                },
                                                onIndexClick = {
                                                    reviewVm.onAction(
                                                        ReviewAction.OpenIndex(help.rcSlug)
                                                    )
                                                }
                                            )
                                            is Help.Questions -> QuestionsCard(
                                                title = help.title,
                                                body = help.body,
                                                typography = typography,
                                                sourceLanguage = state.resourceContainer?.language,
                                                onClose = {
                                                    reviewVm.onAction(ReviewAction.ClearHelp)
                                                }
                                            )
                                            is Help.Index -> IndexCard(
                                                words = help.words,
                                                typography = typography,
                                                sourceLanguage = state.resourceContainer?.language,
                                                onCloseClick = {
                                                    reviewVm.onAction(ReviewAction.ClearHelp)
                                                },
                                                onItemClick = {
                                                    reviewVm.onAction(
                                                        ReviewAction.OpenWord(help.rcSlug, it.slug)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSourceDialog) {
        SourceSelectionDialog(
            targetTranslation = viewModel.targetTranslation,
            onDismissRequest = { showSourceDialog = false },
            onConfirm = {
                showSourceDialog = false
                viewModel.onAction(TargetAction.ConfirmSelectedSources(it))
            },
            onUpdateSources = {
                showUpdateSourcesDialog = true
            }
        )
    }

    if (showUpdateSourcesDialog) {
        ConfirmDialog(
            title = stringResource(R.string.warning_title),
            message = stringResource(R.string.update_warning),
            onConfirm = {
                showUpdateSourcesDialog = false
                onUpdateSources()
            },
            onDismiss = {
                showUpdateSourcesDialog = false
            }
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}

private fun buildMenu(
    context: Context,
    viewMode: TranslationViewMode,
    draftAvailable: Boolean,
    onHomeClick: () -> Unit,
    onNavigateToDraft: () -> Unit,
    onProjectPreview: () -> Unit,
    onUploadExport: () -> Unit,
    onPrint: () -> Unit,
    onFeedback: () -> Unit,
    onChunksDone: () -> Unit,
    onSettings: () -> Unit,
    onSearchRequested: () -> Unit
): List<TranslateSideBarAction> {
    val menuItems = mutableListOf<TranslateSideBarAction>()

    menuItems.add(
        TranslateSideBarAction(
            title = context.getString(R.string.action_translations),
            icon = Icons.AutoMirrored.Filled.LibraryBooks,
            onClick = onHomeClick
        )
    )
    if (draftAvailable) {
        menuItems.add(
            TranslateSideBarAction(
                title = context.getString(R.string.view_available_drafts),
                icon = Icons.Default.Translate,
                onClick = onNavigateToDraft
            )
        )
    }
    menuItems.addAll(
        listOf(
            TranslateSideBarAction(
                title = context.getString(R.string.title_review),
                icon = Icons.Default.DoneAll,
                onClick = onProjectPreview
            ),
            TranslateSideBarAction(
                title = context.getString(R.string.menu_upload_export),
                icon = Icons.Default.Upload,
                onClick = onUploadExport
            ),
            TranslateSideBarAction(
                title = context.getString(R.string.print),
                icon = Icons.Default.Print,
                onClick = onPrint
            ),
            TranslateSideBarAction(
                title = context.getString(R.string.feedback),
                icon = Icons.Default.Feedback,
                onClick = onFeedback
            )
        )
    )
    if (viewMode == TranslationViewMode.REVIEW) {
        menuItems.addAll(
            listOf(
                TranslateSideBarAction(
                    title = context.getString(R.string.action_search),
                    icon = Icons.Default.Search,
                    onClick = onSearchRequested
                ),
                TranslateSideBarAction(
                    title = context.getString(R.string.mark_chunks_done),
                    icon = Icons.Default.Check,
                    onClick = onChunksDone
                )
            )
        )
    }
    menuItems.add(
        TranslateSideBarAction(
            title = context.getString(R.string.action_settings),
            icon = Icons.Default.Settings,
            onClick = onSettings
        )
    )

    return menuItems
}