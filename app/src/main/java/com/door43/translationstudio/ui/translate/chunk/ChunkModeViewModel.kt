package com.door43.translationstudio.ui.translate.chunk

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.adapter.ComposeTextAdapter
import com.door43.translationstudio.ui.translate.ChunkItem
import com.door43.translationstudio.ui.translate.ChunkMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.resourcecontainer.ResourceContainer

data class ChunkModeModel(
    val items: List<ChunkItem.ChunkMode> = emptyList(),
    val notes: String? = null
)

class ChunkModeViewModel(
    private val application: Application
) : AndroidViewModel(application) {

    private val _model = MutableStateFlow(ChunkModeModel())
    val model: StateFlow<ChunkModeModel> = _model

    fun initialize(chunks: List<Chunk>) {
        viewModelScope.launch {
            val chunkItems = withContext(Dispatchers.Default) {
                chunks
                    .chunked(5)
                    .flatMap { batch ->
                        batch.map { async { prepareItem(it) } }
                    }.awaitAll()
            }
            _model.update { it.copy(items = chunkItems) }
        }
    }

    fun clearNotes() {
        _model.update { it.copy(notes = null) }
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
        val sourceText = fetchSourceText(
            chunk.source,
            chunk.chapterSlug,
            chunk.chunkSlug
        )
        val renderedSourceText = renderSourceText(chunk.sourceTranslationFormat, sourceText)
        return sourceText to renderedSourceText
    }

    private fun prepareTarget(chunk: Chunk): Pair<String, AnnotatedString> {
        val targetText = fetchTargetText(chunk.target, chunk.chapterSlug, chunk.chunkSlug)
        val renderedTargetText = renderTargetText(chunk.targetTranslationFormat, targetText)

        return targetText to renderedTargetText
    }

    private fun prepareTranslations(
        chunk: Chunk,
    ): Triple<ProjectTranslation, ChapterTranslation, FrameTranslation> {
        val pt = chunk.target.projectTranslation
        val ct = chunk.target.getChapterTranslation(chunk.chapterSlug)
        val ft = chunk.target.getFrameTranslation(
            chunk.chapterSlug,
            chunk.chunkSlug,
            chunk.targetTranslationFormat
        )

        return Triple(pt, ct, ft)
    }

    private fun fetchSourceText(
        source: ResourceContainer,
        chapterSlug: String,
        chunkSlug: String
    ) = source.readChunk(chapterSlug, chunkSlug)

    private fun renderSourceText(
        translationFormat: TranslationFormat,
        sourceText: String
    ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(sourceText)
            RenderingProvider(application).setupRenderingGroup(
                translationFormat,
                renderingGroup,
                pinVerses = false,
                target = false
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(
                textNodes,
                onNoteClick = {
                    _model.update { state -> state.copy(notes = it.notes) }
                }
            )
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    private fun fetchTargetText(
        target: TargetTranslation,
        chapterSlug: String,
        chunkSlug: String
    ): String {
        return when (chapterSlug) {
            "front" -> {
                // project stuff
                if (chunkSlug == "title") {
                    target.projectTranslation.title
                } else ""
            }
            "back" -> ""
            else -> {
                // chapter stuff
                when (chunkSlug) {
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
    }

    private fun renderTargetText(
        translationFormat: TranslationFormat,
        targetText: String
    ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(targetText)
            RenderingProvider(application).setupRenderingGroup(
                translationFormat,
                renderingGroup,
                pinVerses = false,
                target = true
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(
                textNodes,
                onNoteClick = {
                    _model.update { state -> state.copy(notes = it.notes) }
                }
            )
        } catch (_: Exception) {
            AnnotatedString(targetText)
        }
    }
}