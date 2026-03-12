package com.door43.translationstudio.ui.translate.read

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.adapter.ComposeTextAdapter
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.ChunkMeta
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.resourcecontainer.ResourceContainer

data class ReadModeModel(
    val items: List<ChunkItem.ReadMode> = emptyList()
)

sealed interface ReadAction : ModeAction {
    data class Init(val chunks: List<Chunk>) : ReadAction
}

class ReadModeViewModel : ModeViewModel<ReadAction>() {

    private val _model = MutableStateFlow(ReadModeModel())
    val model: StateFlow<ReadModeModel> = _model

    override fun onAction(action: ReadAction) {
        when (action) {
            is ReadAction.Init -> initialize(action.chunks)
        }
    }

    private fun initialize(chunks: List<Chunk>) {
        viewModelScope.launch {
            val readItems = withContext(Dispatchers.Default) {
                chunks
                    .distinctBy { it.chapterSlug }
                    .chunked(5)
                    .flatMap { batch ->
                        batch.map { async { prepareItem(it) } }
                    }.awaitAll()
            }
            _model.update { it.copy(items = readItems) }
        }
    }

    private fun prepareItem(chunk: Chunk): ChunkItem.ReadMode {
        val (sourceText, renderedSourceText) = prepareSource(chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        val meta = ChunkMeta(
            id = chunk.chapterSlug,
            chunk = chunk,
            sourceText = sourceText,
            targetText = targetText,
            renderedSourceText = renderedSourceText,
            renderedTargetText = renderedTargetText,
            pt = pt,
            ct = ct,
            ft = ft
        )

        return ChunkItem.ReadMode(meta)
    }

    private fun prepareSource(chunk: Chunk): Pair<String, AnnotatedString> {
        val sourceText = fetchSourceText(chunk.source, chunk.chapterSlug)
        val renderedSourceText = renderSourceText(chunk.sourceTranslationFormat, sourceText)

        return sourceText to renderedSourceText
    }

    private fun prepareTarget(chunk: Chunk): Pair<String, AnnotatedString> {
        val targetText = fetchTargetText(chunk.source, chunk.target, chunk.chapterSlug)
        val renderedTargetText = renderTargetText(chunk.targetTranslationFormat, targetText)

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