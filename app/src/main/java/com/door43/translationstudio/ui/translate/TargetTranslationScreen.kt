package com.door43.translationstudio.ui.translate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.components.ProgressDialog
import com.door43.translationstudio.ui.translate.chunk.ChunkAction
import com.door43.translationstudio.ui.translate.chunk.ChunkCard
import com.door43.translationstudio.ui.translate.chunk.ChunkModeViewModel
import com.door43.translationstudio.ui.translate.components.NoSourceScreen
import com.door43.translationstudio.ui.translate.components.TranslateSideBar
import com.door43.translationstudio.ui.translate.components.TranslateSideBarAction
import com.door43.translationstudio.ui.translate.dialogs.SourceSelectionDialog
import com.door43.translationstudio.ui.translate.read.ReadAction
import com.door43.translationstudio.ui.translate.read.ReadCard
import com.door43.translationstudio.ui.translate.read.ReadModeViewModel
import com.door43.translationstudio.ui.viewmodels.TargetAction
import com.door43.translationstudio.ui.viewmodels.TargetTranslationState
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun TargetTranslationScreen(
    viewModel: TargetTranslationViewModel = koinViewModel(),
    onHomeClick: () -> Unit,
    onNavigateToDraft: () -> Unit,
    onProjectPreview: () -> Unit,
    onUploadExport: () -> Unit,
    onPrint: () -> Unit,
    onFeedback: () -> Unit,
    onSearch: () -> Unit,
    onChunksDone: () -> Unit,
    onSettings: () -> Unit
) {
    val typography: Typography = koinInject()

    val state: TargetTranslationState by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val menuItems = remember { mutableStateListOf<TranslateSideBarAction>() }

    // Strings

    val draftExistsStr = stringResource(R.string.draft_translation_exists)
    val draftPreview = stringResource(R.string.preview)
    val menuActionHome = stringResource(R.string.action_translations)
    val menuActionDrafts = stringResource(R.string.view_available_drafts)
    val menuActionPreview = stringResource(R.string.title_review)
    val menuActionUpload = stringResource(R.string.menu_upload_export)
    val menuActionPrint = stringResource(R.string.print)
    val menuActionFeedback = stringResource(R.string.feedback)
    val menuActionSearch = stringResource(R.string.action_search)
    val menuActionChunksDone = stringResource(R.string.mark_chunks_done)
    val menuActionSettings = stringResource(R.string.action_settings)

    var showSourceDialog by rememberSaveable { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var lastViewedChunk by remember { mutableStateOf<Chunk?>(null) }
    var hasDoneInitialLoad by rememberSaveable { mutableStateOf(false) }

    val activeList = remember(state.items, state.viewMode) {
        if (state.viewMode == TranslationViewMode.READ) {
            state.items.distinctBy { it.chapterSlug }
        } else {
            state.items
        }
    }

    val dominantIndex by remember(activeList) {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty() || activeList.isEmpty()) return@derivedStateOf 0

            val dominantItem = visibleItems.find { it.offset > -(it.size / 2) }
            val rawIndex = dominantItem?.index ?: visibleItems.first().index

            // Coerce protects us if the list suddenly shrinks before Compose finishes updating
            rawIndex.coerceIn(0, maxOf(0, activeList.size - 1))
        }
    }

    val currentSliderValue by remember(activeList) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo

            if (visibleItems.isEmpty() || activeList.isEmpty()) return@derivedStateOf 0f

            val firstItem = visibleItems.first()
            val scrolledPixels = -firstItem.offset
            val itemFraction = (scrolledPixels.toFloat() / firstItem.size.toFloat()).coerceIn(0f, 1f)

            val absolutePosition = firstItem.index + itemFraction
            (absolutePosition / activeList.size).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(Unit) {
        ContainerCache.empty()
        viewModel.onAction(TargetAction.RefreshSelectedSource)
        viewModel.onAction(TargetAction.InitLastFocus)
    }

    LaunchedEffect(activeList, state.lastFocusChapterId) {
        if (!hasDoneInitialLoad && activeList.isNotEmpty() && state.lastFocusChapterId != null) {
            // Try to find the exact chunk
            var targetIndex = activeList.indexOfFirst {
                it.chapterSlug == state.lastFocusChapterId && it.chunkSlug == state.lastFocusFrameId
            }
            // Fallback: If in READ mode,
            // the specific chunkId might be filtered out. Find the chapter.
            if (targetIndex == -1) {
                targetIndex = activeList.indexOfFirst { it.chapterSlug == state.lastFocusChapterId }
            }

            if (targetIndex != -1) {
                listState.scrollToItem(targetIndex)
                lastViewedChunk = activeList[targetIndex]
                hasDoneInitialLoad = true
            }
        }
    }

    LaunchedEffect(activeList) {
        val chunkToFind = lastViewedChunk
        if (hasDoneInitialLoad && chunkToFind != null && activeList.isNotEmpty()) {

            // Try exact match first
            var newIndex = activeList.indexOfFirst {
                it.chapterSlug == chunkToFind.chapterSlug && it.chunkSlug == chunkToFind.chunkSlug
            }

            // Fallback: If we switched to READ mode,
            // the exact chunk might be gone. Match by chapter.
            if (newIndex == -1) {
                newIndex = activeList.indexOfFirst { it.chapterSlug == chunkToFind.chapterSlug }
            }

            if (newIndex != -1) {
                listState.scrollToItem(newIndex)
            }
        }
    }

    LaunchedEffect(dominantIndex, activeList) {
        if (activeList.isNotEmpty()) {
            val chunk = activeList[dominantIndex]
            lastViewedChunk = chunk // Update our memory for the next mode swap
            viewModel.onAction(
                TargetAction.SaveLastFocus(chunk.chapterSlug, chunk.chunkSlug)
            )
        }
    }

    LaunchedEffect(state.draftAvailable, state.viewMode) {
        menuItems.clear()
        menuItems.add(
            TranslateSideBarAction(
                title = menuActionHome,
                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                onClick = onHomeClick
            )
        )
        if (state.draftAvailable) {
            menuItems.add(
                TranslateSideBarAction(
                    title = menuActionDrafts,
                    icon = Icons.Default.Translate,
                    onClick = onNavigateToDraft
                )
            )
        }
        menuItems.addAll(
            listOf(
                TranslateSideBarAction(
                    title = menuActionPreview,
                    icon = Icons.Default.DoneAll,
                    onClick = onProjectPreview
                ),
                TranslateSideBarAction(
                    title = menuActionUpload,
                    icon = Icons.Default.Upload,
                    onClick = onUploadExport
                ),
                TranslateSideBarAction(
                    title = menuActionPrint,
                    icon = Icons.Default.Print,
                    onClick = onPrint
                ),
                TranslateSideBarAction(
                    title = menuActionFeedback,
                    icon = Icons.Default.Feedback,
                    onClick = onFeedback
                )
            )
        )
        if (state.viewMode == TranslationViewMode.REVIEW) {
            menuItems.addAll(
                listOf(
                    TranslateSideBarAction(
                        title = menuActionSearch,
                        icon = Icons.Default.Search,
                        onClick = onSearch
                    ),
                    TranslateSideBarAction(
                        title = menuActionChunksDone,
                        icon = Icons.Default.Check,
                        onClick = onChunksDone
                    )
                )
            )
        }
        menuItems.add(
            TranslateSideBarAction(
                title = menuActionSettings,
                icon = Icons.Default.Settings,
                onClick = onSettings
            )
        )
    }

    LaunchedEffect(state.snackBarMessage) {
        state.snackBarMessage?.let { message ->
            scope.launch {
                snackBarHostState.showSnackbar(message)
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
                    viewModel.onAction(TargetAction.LastViewMode(TranslationViewMode.READ))
                },
                onChunkClick = {
                    viewModel.onAction(TargetAction.LastViewMode(TranslationViewMode.CHUNK))
                },
                onReviewClick = {
                    // TODO Should reset conflict items filter
                    viewModel.onAction(TargetAction.LastViewMode(TranslationViewMode.REVIEW))
                },
                onMergeConflictClick = {
                    // TODO Should toggle conflict items filter
                    viewModel.onAction(TargetAction.LastViewMode(TranslationViewMode.REVIEW))
                },
                onSliderValueChange = {
                    val exactPosition = it * activeList.size
                    val targetIndex = exactPosition.toInt().coerceIn(0, activeList.size - 1)
                    val fraction = exactPosition - targetIndex

                    // THE GUESS: Because the target chapter isn't on screen yet, we don't know its height.
                    // We have to guess the offset based on the screen height.
                    // Here we assume an average chapter is about 3 screens tall.
                    val screenHeight = listState.layoutInfo.viewportSize.height
                    val estimatedOffsetPixels = (fraction * (screenHeight * 3)).toInt()

                    scope.launch {
                        listState.scrollToItem(targetIndex, estimatedOffsetPixels)
                    }
                },
                sliderValue = currentSliderValue,
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
                            val readVm: ReadModeViewModel = koinViewModel()
                            val readState by readVm.state.collectAsStateWithLifecycle()

                            ModeScreenTemplate(
                                state = readState,
                                viewModel = readVm,
                                listState = listState,
                                onInit = {
                                    readVm.onAction(ReadAction.Init(activeList))
                                }
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
                                    }
                                )
                            }
                        }
                        TranslationViewMode.CHUNK -> {
                            val chunkVm: ChunkModeViewModel = koinViewModel()
                            val chunkState by chunkVm.state.collectAsStateWithLifecycle()

                            ModeScreenTemplate(
                                state = chunkState,
                                viewModel = chunkVm,
                                listState = listState,
                                onInit = {
                                    chunkVm.onAction(ChunkAction.Init(activeList))
                                }
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
                                    }
                                )
                            }
                        }
                        TranslationViewMode.REVIEW -> {
                            Text(
                                text = "Review screen in development...",
                                modifier = Modifier.padding(32.dp)
                            )
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
            onUpdateSources = {}
        )
    }

    progress?.let {
        ProgressDialog(
            message = it.message,
            progress = it.value
        )
    }
}