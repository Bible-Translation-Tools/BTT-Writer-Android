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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.door43.translationstudio.core.Chunk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PendingScrollItem(val chapterId: String, val chunkId: String? = null)

@Stable
class ScrollCoordinator(
    val listState: LazyListState,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    var lastViewedChunk by mutableStateOf<Chunk?>(null)
        internal set
    var hasDoneInitialLoad by mutableStateOf(false)
        internal set
    var pendingScrollChapter by mutableStateOf<PendingScrollItem?>(null)
    var sliderChapterLabel by mutableStateOf<String?>(null)

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

    fun onSliderChange(value: Float, items: List<Chunk>) {
        if (items.isEmpty()) return
        val exactPosition = value * items.size
        val targetIndex = exactPosition.toInt().coerceIn(0, items.size - 1)
        val fraction = exactPosition - targetIndex

        val slug = items[targetIndex].chapterSlug
        sliderChapterLabel = slug.toIntOrNull()?.toString() ?: slug

        val screenHeight = listState.layoutInfo.viewportSize.height
        val estimatedOffsetPixels = (fraction * (screenHeight * 3)).toInt()

        scope.launch {
            listState.scrollToItem(targetIndex, estimatedOffsetPixels)
        }
    }
}

@Composable
fun rememberScrollCoordinator(
    chunks: List<Chunk>,
    lastFocusChapterId: String?,
    lastFocusFrameId: String?,
    viewModel: TargetTranslationViewModel
): ScrollCoordinator {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val coordinator = remember { ScrollCoordinator(listState, scope) }

    // Restore hasDoneInitialLoad across config changes
    var savedInitialLoad by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        coordinator.hasDoneInitialLoad = savedInitialLoad
    }

    // Initial scroll to last focus position
    LaunchedEffect(chunks, lastFocusChapterId) {
        if (!coordinator.hasDoneInitialLoad && chunks.isNotEmpty() && lastFocusChapterId != null) {
            var targetIndex = chunks.indexOfFirst {
                it.chapterSlug == lastFocusChapterId && it.chunkSlug == lastFocusFrameId
            }
            if (targetIndex == -1) {
                targetIndex = chunks.indexOfFirst { it.chapterSlug == lastFocusChapterId }
            }
            if (targetIndex != -1) {
                listState.scrollToItem(targetIndex)
                coordinator.lastViewedChunk = chunks[targetIndex]
                coordinator.hasDoneInitialLoad = true
                savedInitialLoad = true
            }
        }
    }

    // Scroll to pending chapter
    LaunchedEffect(coordinator.pendingScrollChapter) {
        val scrollTarget = coordinator.pendingScrollChapter ?: return@LaunchedEffect

        snapshotFlow { chunks }
            .first { list ->
                list.any {
                    it.chapterSlug == scrollTarget.chapterId
                            && scrollTarget.chunkId?.let { c -> c == it.chunkSlug } ?: true
                }
            }

        val targetIndex = chunks.indexOfFirst {
            it.chapterSlug == scrollTarget.chapterId
                    && scrollTarget.chunkId?.let { c -> c == it.chunkSlug } ?: true
        }
        if (targetIndex != -1) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .first { it > targetIndex }

            listState.scrollToItem(targetIndex)
            coordinator.lastViewedChunk = chunks[targetIndex]
        }
        coordinator.pendingScrollChapter = null
    }

    // Restore position after mode switch (items change)
    LaunchedEffect(chunks) {
        val chunkToFind = coordinator.lastViewedChunk
        if (coordinator.hasDoneInitialLoad && chunkToFind != null && chunks.isNotEmpty()
            && coordinator.pendingScrollChapter == null
        ) {
            var newIndex = chunks.indexOfFirst {
                it.chapterSlug == chunkToFind.chapterSlug && it.chunkSlug == chunkToFind.chunkSlug
            }
            if (newIndex == -1) {
                newIndex = chunks.indexOfFirst { it.chapterSlug == chunkToFind.chapterSlug }
            }
            if (newIndex != -1) {
                listState.scrollToItem(newIndex)
            }
        }
    }

    // Track dominant item and save focus
    val dominantIndex by coordinator.dominantIndex
    LaunchedEffect(dominantIndex, chunks) {
        if (chunks.isNotEmpty() && coordinator.pendingScrollChapter == null) {
            val safeIndex = dominantIndex.coerceIn(0, maxOf(0, chunks.size - 1))
            val item = chunks[safeIndex]
            coordinator.lastViewedChunk = item
            viewModel.onAction(
                TargetAction.SaveLastFocus(item.chapterSlug, item.chunkSlug)
            )
        }
    }

    return coordinator
}
