package com.door43.translationstudio.ui.translate

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.adapter.ComposeTextAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface ModeAction {
    object ClearNotes : ModeAction
}

data class Footnote(
    val text: String,
    val start: Int,
    val end: Int
)

abstract class ModeViewModel<T> : ViewModel(), KoinComponent {
    protected val application: Application by inject()

    private val _footnote = MutableStateFlow<Footnote?>(null)
    val footnote: StateFlow<Footnote?> = _footnote.asStateFlow()

    abstract fun onAction(action: T)

    fun onSharedAction(action: ModeAction) {
        when(action) {
            ModeAction.ClearNotes -> { _footnote.value = null }
        }
    }

    protected fun prepareTranslations(
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

    protected fun renderSourceText(
        translationFormat: TranslationFormat,
        sourceText: String
    ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(sourceText)
            RenderingProvider(application).setupRenderingGroup(
                translationFormat, renderingGroup, pinVerses = false, target = false
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(
                textNodes,
                onNoteClick = { notes, start, end ->
                    _footnote.value = Footnote(notes.notes, start, end)
                }
            )
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    protected fun renderTargetText(
        translationFormat: TranslationFormat,
        targetText: String
    ): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(targetText)
            RenderingProvider(application).setupRenderingGroup(
                translationFormat, renderingGroup, pinVerses = false, target = true
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(
                textNodes,
                onNoteClick = { notes, start, end ->
                    _footnote.value = Footnote(notes.notes, start, end)
                }
            )
        } catch (_: Exception) {
            AnnotatedString(targetText)
        }
    }
}