package com.door43.translationstudio.ui.translate.chunk

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.ChunkMeta
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeState
import com.door43.translationstudio.ui.translate.ModeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ChunkState(
    val test: String = ""
) : ModeState

sealed interface ChunkAction : ModeAction {
    data class ItemTextChanged(val item: ChunkItem.ChunkMode, val text: String) : ChunkAction
    data class CardsSwiped(val item: ChunkItem.ChunkMode, val sourceOnTop: Boolean) : ChunkAction
}

class ChunkModeViewModel(
    chunks: StateFlow<List<Chunk>>
) : ModeViewModel<ChunkAction, ChunkItem.ChunkMode>(chunks) {

    private val _state = MutableStateFlow(ChunkState())
    val state: StateFlow<ChunkState> = _state

    override fun mapToChildType(chunks: List<Chunk>): List<ChunkItem.ChunkMode> {
        return chunks.chunked(5).flatMap { batch ->
            batch.map { prepareItem(it) }
        }
    }

    override fun onAction(action: ChunkAction) {
        when (action) {
            is ChunkAction.ItemTextChanged -> onItemTextChanged(action.item, action.text)
            is ChunkAction.CardsSwiped -> onCardsSwiped(action.item, action.sourceOnTop)
        }
    }

    private fun onCardsSwiped(item: ChunkItem.ChunkMode, sourceOnTop: Boolean) {
        updateLocalItem(item.copy(sourceOnTop = sourceOnTop))
    }

    private fun prepareItem(chunk: Chunk, sourceOnTop: Boolean = true): ChunkItem.ChunkMode {
        val (sourceText, renderedSourceText) = prepareSource(chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        val id = "${chunk.chapterSlug}-${chunk.chunkSlug}"
        val meta = ChunkMeta(
            chunk = chunk,
            sourceText = sourceText,
            targetText = targetText,
            renderedSourceText = renderedSourceText,
            renderedTargetText = renderedTargetText,
            pt = pt,
            ct = ct,
            ft = ft
        )

        return ChunkItem.ChunkMode(id, sourceOnTop, meta)
    }

    private fun prepareSource(chunk: Chunk): Pair<String, AnnotatedString> {
        val text = chunk.source.readChunk(chunk.chapterSlug, chunk.chunkSlug)
        return text to renderSourceText(chunk.sourceTranslationFormat, text)
    }

    private fun prepareTarget(chunk: Chunk): Pair<String, AnnotatedString> {
        val text = fetchTargetText(chunk.target, chunk.chapterSlug, chunk.chunkSlug)
        return text to renderTargetText(chunk.targetTranslationFormat, text)
    }

    private fun fetchTargetText(target: TargetTranslation, chapterSlug: String, chunkSlug: String): String {
        return when (chapterSlug) {
            "front" -> if (chunkSlug == "title") target.projectTranslation.title else ""
            "back" -> ""
            else -> when (chunkSlug) {
                "title" -> target.getChapterTranslation(chapterSlug).title
                "reference" -> target.getChapterTranslation(chapterSlug).reference
                else -> target.getFrameTranslation(chapterSlug, chunkSlug, target.format).body
            }
        }
    }

    private fun onItemTextChanged(item: ChunkItem.ChunkMode, text: String) {
        viewModelScope.launch {
            item.saveTranslation(text)
            updateLocalItem(
                prepareItem(item.meta.chunk, false)
            )
        }
    }
}