package com.door43.translationstudio.ui.translate.read

import androidx.compose.ui.text.AnnotatedString
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.ChunkMeta
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeState
import com.door43.translationstudio.ui.translate.ModeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.unfoldingword.resourcecontainer.ResourceContainer

data class ReadState(
    val test: String  = ""
) : ModeState

sealed interface ReadAction : ModeAction {
    data class CardsSwiped(val item: ChunkItem.ReadMode, val sourceOnTop: Boolean) : ReadAction
}

class ReadModeViewModel(
    chunks: StateFlow<List<Chunk>>
) : ModeViewModel<ReadAction, ChunkItem.ReadMode>(chunks) {

    private val _state = MutableStateFlow(ReadState())
    val state: StateFlow<ReadState> = _state

    override fun mapToChildType(chunks: List<Chunk>): List<ChunkItem.ReadMode> {
        return chunks
            .distinctBy { it.chapterSlug }
            .chunked(5)
            .flatMap { batch ->
                batch.map { prepareItem(it) }
            }
    }

    override fun onAction(action: ReadAction) {
        when (action) {
            is ReadAction.CardsSwiped -> onCardsSwiped(action.item, action.sourceOnTop)
        }
    }

    private fun onCardsSwiped(item: ChunkItem.ReadMode, sourceOnTop: Boolean) {
        updateLocalItem(item.copy(sourceOnTop = sourceOnTop))
    }

    private fun prepareItem(chunk: Chunk, sourceOnTop: Boolean = true): ChunkItem.ReadMode {
        val (sourceText, renderedSourceText) = prepareSource(chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        val id = chunk.chapterSlug
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

        return ChunkItem.ReadMode(id, sourceOnTop, meta)
    }

    private fun prepareSource(chunk: Chunk): Pair<String, AnnotatedString> {
        val sourceText = fetchSourceText(chunk.source, chunk.chapterSlug)
        val renderedSourceText = renderSourceText(chunk.sourceTranslationFormat, sourceText)

        return sourceText to renderedSourceText
    }

    private fun prepareTarget(chunk: Chunk): Pair<String, AnnotatedString> {
        val targetText = fetchTargetText(chunk.source, chunk.target, chunk.chapterSlug)
        val renderedTargetText = renderTargetText(
            chunk.targetTranslationFormat,
            targetText,
            VerseDisplay.NUMBER
        )

        return targetText to renderedTargetText
    }

    private fun fetchSourceText(source: ResourceContainer, chapterSlug: String): String {
        var chapterBody = ""
        val sorter = SlugSorter()
        val chunks = sorter.sort(source.chunks(chapterSlug))
        for (chunk in chunks) {
            if (chunk != "title") {
                chapterBody += source.readChunk(chapterSlug, chunk)
            }
        }
        return chapterBody
    }

    private fun fetchTargetText(
        source: ResourceContainer,
        target: TargetTranslation,
        chapterSlug: String
    ): String {
        var chapterBody = ""
        val sorter = SlugSorter()
        val chunks = sorter.sort(source.chunks(chapterSlug))
        for (chunk in chunks) {
            val translation = target.getFrameTranslation(chapterSlug, chunk, target.format)
            chapterBody += " " + translation.body
        }
        return chapterBody
    }
}