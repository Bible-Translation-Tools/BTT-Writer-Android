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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChunkState(
    override val items: List<ChunkItem.ChunkMode> = emptyList(),
) : ModeState<ChunkItem.ChunkMode>

sealed interface ChunkAction : ModeAction {
    data class Init(val chunks: List<Chunk>) : ChunkAction
}

class ChunkModeViewModel : ModeViewModel<ChunkAction>() {

    private val _state = MutableStateFlow(ChunkState())
    val state: StateFlow<ChunkState> = _state

    override fun onAction(action: ChunkAction) {
        when (action) {
            is ChunkAction.Init -> initialize(action.chunks)
        }
    }

    private fun initialize(chunks: List<Chunk>) {
        viewModelScope.launch {
            val chunkItems = withContext(Dispatchers.Default) {
                chunks.chunked(5).flatMap { batch ->
                    batch.map { async { prepareItem(it) } }
                }.awaitAll()
            }
            _state.update { it.copy(items = chunkItems) }
        }
    }

    private fun prepareItem(chunk: Chunk): ChunkItem.ChunkMode {
        val (sourceText, renderedSourceText) = prepareSource(chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        val meta = ChunkMeta(
            id = "${chunk.chapterSlug}-${chunk.chunkSlug}",
            chunk = chunk,
            sourceText = sourceText,
            targetText = targetText,
            renderedSourceText = renderedSourceText,
            renderedTargetText = renderedTargetText,
            pt = pt,
            ct = ct,
            ft = ft
        )

        return ChunkItem.ChunkMode(meta)
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
}