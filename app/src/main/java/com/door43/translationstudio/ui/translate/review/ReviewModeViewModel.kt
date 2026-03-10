package com.door43.translationstudio.ui.translate.review

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.AndroidViewModel
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.adapter.ComposeTextAdapter
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.Companion.SEARCH_SOURCE
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.util.Locale

class ReviewModeViewModel(
    private val application: Application,
    private val prefRepository: IPreferenceRepository
) : AndroidViewModel(application) {

    fun getLastSearchSource(): String {
        val defaultSource = SearchSubject.SOURCE.name.uppercase(
            Locale.getDefault()
        )
        return prefRepository.getDefaultPref(
            SEARCH_SOURCE,
            defaultSource
        )
    }

    fun setLastSearchSource(subject: SearchSubject) {
        prefRepository.setDefaultPref(
            SEARCH_SOURCE,
            subject.name.uppercase(Locale.getDefault())
        )
    }

    private fun fetchSourceText(source: ResourceContainer, chapterSlug: String, chunkSlug: String?): String {
        return if (chunkSlug != null) {
            source.readChunk(chapterSlug, chunkSlug)
        } else {
            var chapterBody = ""
            val sorter = SlugSorter()
            val chunks = sorter.sort(source.chunks(chapterSlug))
            for (chunk in chunks) {
                if(chunk != "title") {
                    chapterBody += source.readChunk(chapterSlug, chunk);
                }
            }
            chapterBody
        }
    }

    private fun renderSourceText(sourceText: String, format: TranslationFormat): AnnotatedString {
        return try {
            val renderingGroup = RenderingGroup()
            renderingGroup.init(sourceText)
            RenderingProvider(application).setupRenderingGroup(
                format,
                renderingGroup,
                pinVerses = false,
                target = false
            )
            val renderNodes = renderingGroup.startNodes()
            val textNodes = RenderNodeConverter.renderNodesToTextNodes(renderNodes)
            ComposeTextAdapter.convert(textNodes/*, onNoteClick = onNoteClick*/)
        } catch (_: Exception) {
            AnnotatedString(sourceText)
        }
    }

    private fun fetchTargetText(
        source: ResourceContainer,
        target: TargetTranslation,
        chapterSlug: String,
        chunkSlug: String?
    ): String {
        return if (chunkSlug != null) {
            when (chapterSlug) {
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
        } else {
            var chapterBody = ""
            val sorter = SlugSorter()
            val chunks = sorter.sort(source.chunks(chapterSlug))
            for (chunk in chunks) {
                val translation = target.getFrameTranslation(chapterSlug, chunk, target.format)
                chapterBody += " " + translation.body
            }
            chapterBody
        }
    }

    private fun renderTargetText(targetText: String): AnnotatedString {
        return AnnotatedString(targetText)
    }
}