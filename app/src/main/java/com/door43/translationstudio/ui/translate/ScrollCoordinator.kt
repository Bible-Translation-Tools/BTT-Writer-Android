package com.door43.translationstudio.ui.translate

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PendingScrollItem(val chapterId: String, val chunkId: String? = null)

@Stable
class ScrollCoordinator(
    val listState: LazyListState
) {
    var sliderChapterLabel by mutableStateOf<String?>(null)

    private val _pendingScroll = MutableStateFlow<PendingScrollItem?>(null)
    val pendingScroll: StateFlow<PendingScrollItem?> = _pendingScroll.asStateFlow()

    private val _sliderDrags = MutableSharedFlow<Float>(extraBufferCapacity = 16)
    val sliderDrags: SharedFlow<Float> = _sliderDrags.asSharedFlow()

    val dominantIndex = derivedStateOf {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@derivedStateOf 0
        val dominantItem = visibleItems.find { it.offset > -(it.size / 2) }
        dominantItem?.index ?: visibleItems.first().index
    }

    val sliderValue = derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@derivedStateOf 0f

        val firstItem = visibleItems.first()
        val scrolledPixels = -firstItem.offset
        val itemFraction = (scrolledPixels.toFloat() / firstItem.size.toFloat()).coerceIn(0f, 1f)
        val absolutePosition = firstItem.index + itemFraction
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems == 0) 0f
        else (absolutePosition / totalItems).coerceIn(0f, 1f)
    }

    fun requestScroll(chapterId: String, chunkId: String? = null) {
        _pendingScroll.value = PendingScrollItem(chapterId, chunkId)
    }

    fun consumePending() {
        _pendingScroll.value = null
    }

    fun onSliderChange(value: Float) {
        _sliderDrags.tryEmit(value)
    }
}

@Composable
fun rememberScrollCoordinator(): ScrollCoordinator {
    val listState = rememberLazyListState()
    return remember { ScrollCoordinator(listState) }
}

@Composable
fun ScrollBindingEffect(
    coordinator: ScrollCoordinator,
    items: List<TranslateItem>,
    component: TranslateComponent
) {
    val state by component.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)

    var hasDoneInitialLoad by rememberSaveable { mutableStateOf(false) }
    var lastViewedChapter by remember { mutableStateOf<String?>(null) }
    var lastViewedFrame by remember { mutableStateOf<String?>(null) }

    val lastFocusChapterId = state.lastFocusChapterId
    val lastFocusFrameId = state.lastFocusFrameId

    val pendingScroll by coordinator.pendingScroll.collectAsStateWithLifecycle()
    val dominantIndex by coordinator.dominantIndex

    LaunchedEffect(items, lastFocusChapterId) {
        if (!hasDoneInitialLoad && items.isNotEmpty() && lastFocusChapterId != null) {
            var targetIndex = items.indexOfFirst {
                it.chunk.chapterSlug == lastFocusChapterId && it.chunk.chunkSlug == lastFocusFrameId
            }
            if (targetIndex == -1) {
                targetIndex = items.indexOfFirst { it.chunk.chapterSlug == lastFocusChapterId }
            }
            if (targetIndex != -1) {
                coordinator.listState.scrollToItem(targetIndex)
                val chunk = items[targetIndex].chunk
                lastViewedChapter = chunk.chapterSlug
                lastViewedFrame = chunk.chunkSlug
                hasDoneInitialLoad = true
            }
        }
    }

    LaunchedEffect(pendingScroll) {
        val scrollTarget = pendingScroll ?: return@LaunchedEffect

        snapshotFlow { currentItems }
            .first { list ->
                list.any {
                    it.chunk.chapterSlug == scrollTarget.chapterId
                            && scrollTarget.chunkId?.let { c -> c == it.chunk.chunkSlug } ?: true
                }
            }

        val resolved = currentItems
        val targetIndex = resolved.indexOfFirst {
            it.chunk.chapterSlug == scrollTarget.chapterId
                    && scrollTarget.chunkId?.let { c -> c == it.chunk.chunkSlug } ?: true
        }
        if (targetIndex != -1) {
            snapshotFlow { coordinator.listState.layoutInfo.totalItemsCount }
                .first { it > targetIndex }

            coordinator.listState.scrollToItem(targetIndex)
            val chunk = resolved[targetIndex].chunk
            lastViewedChapter = chunk.chapterSlug
            lastViewedFrame = chunk.chunkSlug
        }
        coordinator.consumePending()
    }

    LaunchedEffect(items) {
        val chapterToFind = lastViewedChapter
        val frameToFind = lastViewedFrame
        if (hasDoneInitialLoad && chapterToFind != null && items.isNotEmpty()
            && coordinator.pendingScroll.value == null
        ) {
            var newIndex = items.indexOfFirst {
                it.chunk.chapterSlug == chapterToFind && it.chunk.chunkSlug == frameToFind
            }
            if (newIndex == -1) {
                newIndex = items.indexOfFirst { it.chunk.chapterSlug == chapterToFind }
            }
            if (newIndex != -1) {
                coordinator.listState.scrollToItem(newIndex)
            }
        }
    }

    LaunchedEffect(dominantIndex, items) {
        if (items.isNotEmpty() && coordinator.pendingScroll.value == null) {
            val safeIndex = dominantIndex.coerceIn(0, maxOf(0, items.size - 1))
            val item = items[safeIndex]
            lastViewedChapter = item.chunk.chapterSlug
            lastViewedFrame = item.chunk.chunkSlug
            component.saveLastFocus(item.chunk.chapterSlug, item.chunk.chunkSlug)
        }
    }

    LaunchedEffect(coordinator) {
        coordinator.sliderDrags.collect { value ->
            val activeItems = currentItems
            if (activeItems.isEmpty()) return@collect

            val exactPosition = value * activeItems.size
            val targetIndex = exactPosition.toInt().coerceIn(0, activeItems.size - 1)
            val fraction = exactPosition - targetIndex

            val slug = activeItems[targetIndex].chunk.chapterSlug
            coordinator.sliderChapterLabel = slug.toIntOrNull()?.toString() ?: slug

            val screenHeight = coordinator.listState.layoutInfo.viewportSize.height
            val estimatedOffsetPixels = (fraction * (screenHeight * 3)).toInt()

            scope.launch {
                coordinator.listState.scrollToItem(targetIndex, estimatedOffsetPixels)
            }
        }
    }
}
