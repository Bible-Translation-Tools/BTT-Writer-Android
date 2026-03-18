package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeState
import com.door43.translationstudio.ui.translate.ModeViewModel
import com.door43.translationstudio.ui.translate.Swipable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChunkState(
    val chunkToReopen: ChunkItem? = null
) : ModeState

sealed interface ChunkAction : ModeAction {
    data class ItemTextChanged(val item: ChunkItem, val text: String) : ChunkAction
    data class ReopenChunkClicked(val item: ChunkItem) : ChunkAction
    data class ReopenChunkConfirmed(val confirm: Boolean) : ChunkAction
}

class ChunkModeViewModel(
    chunks: StateFlow<List<Chunk>>
) : ModeViewModel<ChunkItem>(chunks) {

    private val _state = MutableStateFlow(ChunkState())
    val state: StateFlow<ChunkState> = _state

    override fun mapToChildType(chunks: List<Chunk>, onReady: (List<ChunkItem>) -> Unit) {
        viewModelScope.launch {
            val items = withContext(Dispatchers.Default) {
                chunks
                    .chunked(5)
                    .flatMap { batch ->
                        batch.map { async { prepareItem(it) } }
                    }.awaitAll()
            }
            onReady(items)
        }
    }

    override fun onAction(action: ModeAction) {
        super.onAction(action)
        when (action) {
            is ChunkAction.ItemTextChanged -> onItemTextChanged(action.item, action.text)
            is ChunkAction.ReopenChunkClicked -> onReopenChunkClicked(action.item)
            is ChunkAction.ReopenChunkConfirmed -> onReopenChunkConfirmed(action.confirm)
        }
    }

    override fun onCardsSwiped(item: Swipable, sourceOnTop: Boolean) {
        updateItem(item.selfCopy(sourceOnTop = sourceOnTop) as ChunkItem)
    }

    private fun prepareItem(chunk: Chunk, sourceOnTop: Boolean = true): ChunkItem {
        val (sourceText, renderedSourceText) = prepareSource(chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        return ChunkItem(
            id  = "${chunk.chapterSlug}-${chunk.chunkSlug}",
            chunk = chunk,
            sourceText = sourceText,
            targetText = targetText,
            renderedSourceText = renderedSourceText,
            renderedTargetText = renderedTargetText,
            pt = pt,
            ct = ct,
            ft = ft,
            sourceOnTop = sourceOnTop
        )
    }

    private fun prepareSource(chunk: Chunk): Pair<String, AnnotatedString> {
        val text = chunk.source.readChunk(chunk.chapterSlug, chunk.chunkSlug)
        return text to renderSourceText(chunk.sourceTranslationFormat, text)
    }

    private fun prepareTarget(chunk: Chunk): Pair<String, AnnotatedString> {
        val text = fetchTargetText(chunk.target, chunk.chapterSlug, chunk.chunkSlug)
        return text to renderTargetText(chunk.targetTranslationFormat, text)
    }

    private fun fetchTargetText(
        target: TargetTranslation,
        chapterSlug: String,
        chunkSlug: String
    ): String {
        return when (chapterSlug) {
            "front" -> if (chunkSlug == "title") target.projectTranslation.title else ""
            "back" -> ""
            else -> when (chunkSlug) {
                "title" -> target.getChapterTranslation(chapterSlug).title
                "reference" -> target.getChapterTranslation(chapterSlug).reference
                else -> target.getFrameTranslation(
                    chapterSlug,
                    chunkSlug,
                    target.format
                ).body
            }
        }
    }

    private fun onItemTextChanged(item: ChunkItem, text: String) {
        viewModelScope.launch {
            item.saveTranslation(text)
        }
    }

    private fun onReopenChunkClicked(item: ChunkItem) {
        _state.value = _state.value.copy(
            chunkToReopen = item
        )
    }

    private fun onReopenChunkConfirmed(confirm: Boolean) {
        viewModelScope.launch {
            if (confirm) {
                _state.value.chunkToReopen?.let {
                    withContext(Dispatchers.IO) {
                        it.reopen()
                    }
                    val updated = prepareItem(it.chunk, false)
                    updateItem(updated)
                }
            }
        }
        _state.value = _state.value.copy(
            chunkToReopen = null
        )
    }
}