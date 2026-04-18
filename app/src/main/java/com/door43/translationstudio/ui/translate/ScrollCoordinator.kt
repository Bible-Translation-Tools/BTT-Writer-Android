package com.door43.translationstudio.ui.translate

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.door43.translationstudio.core.Chunk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PendingScrollItem(val chapterId: String, val chunkId: String? = null)

@Stable
class ScrollCoordinator(
    val listState: LazyListState
) {
    var lastViewedChunk by mutableStateOf<Chunk?>(null)
    var hasDoneInitialLoad by mutableStateOf(false)
    var pendingScrollChapter by mutableStateOf<PendingScrollItem?>(null)
    var sliderChapterLabel by mutableStateOf<String?>(null)

    var onSliderDrag: ((Float) -> Unit)? = null

    val dominantIndex = derivedStateOf {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@derivedStateOf 0

        val dominantItem = visibleItems.find { it.offset > -(it.size / 2) }
        val rawIndex = dominantItem?.index ?: visibleItems.first().index
        rawIndex
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

    fun onSliderChange(value: Float) {
        onSliderDrag?.invoke(value)
    }
}

@Composable
fun rememberScrollCoordinator(): ScrollCoordinator {
    val listState = rememberLazyListState()
    return remember { ScrollCoordinator(listState) }
}

@Composable
fun rememberScrollBinding(
    coordinator: ScrollCoordinator,
    items: List<TranslateItem>,
    component: TranslateComponent
) {
    val state by component.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val currentItems by rememberUpdatedState(items)

    var savedInitialLoad by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        coordinator.hasDoneInitialLoad = savedInitialLoad
    }

    val lastFocusChapterId = state.lastFocusChapterId
    val lastFocusFrameId = state.lastFocusFrameId
    LaunchedEffect(items, lastFocusChapterId) {
        if (!coordinator.hasDoneInitialLoad && items.isNotEmpty() && lastFocusChapterId != null) {
            var targetIndex = items.indexOfFirst {
                it.chunk.chapterSlug == lastFocusChapterId && it.chunk.chunkSlug == lastFocusFrameId
            }
            if (targetIndex == -1) {
                targetIndex = items.indexOfFirst { it.chunk.chapterSlug == lastFocusChapterId }
            }
            if (targetIndex != -1) {
                coordinator.listState.scrollToItem(targetIndex)
                coordinator.lastViewedChunk = items[targetIndex].chunk
                coordinator.hasDoneInitialLoad = true
                savedInitialLoad = true
            }
        }
    }

    LaunchedEffect(coordinator.pendingScrollChapter) {
        val scrollTarget = coordinator.pendingScrollChapter ?: return@LaunchedEffect

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
            coordinator.lastViewedChunk = resolved[targetIndex].chunk
        }
        coordinator.pendingScrollChapter = null
    }

    LaunchedEffect(items) {
        val chunkToFind = coordinator.lastViewedChunk
        if (coordinator.hasDoneInitialLoad && chunkToFind != null && items.isNotEmpty()
            && coordinator.pendingScrollChapter == null
        ) {
            var newIndex = items.indexOfFirst {
                it.chunk.chapterSlug == chunkToFind.chapterSlug && it.chunk.chunkSlug == chunkToFind.chunkSlug
            }
            if (newIndex == -1) {
                newIndex = items.indexOfFirst { it.chunk.chapterSlug == chunkToFind.chapterSlug }
            }
            if (newIndex != -1) {
                coordinator.listState.scrollToItem(newIndex)
            }
        }
    }

    val dominantIndex by coordinator.dominantIndex
    LaunchedEffect(dominantIndex, items) {
        if (items.isNotEmpty() && coordinator.pendingScrollChapter == null) {
            val safeIndex = dominantIndex.coerceIn(0, maxOf(0, items.size - 1))
            val item = items[safeIndex]
            coordinator.lastViewedChunk = item.chunk
            component.saveLastFocus(item.chunk.chapterSlug, item.chunk.chunkSlug)
        }
    }

    DisposableEffect(Unit) {
        coordinator.onSliderDrag = handler@{ value ->
            val activeItems = currentItems
            if (activeItems.isEmpty()) return@handler

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
        onDispose { coordinator.onSliderDrag = null }
    }
}
