package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.core.RenderingProvider
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.spannables.Span
import com.door43.usecases.ImportDraft
import com.door43.util.sortNumerically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer

data class ChapterContent(
    val heading: String,
    val title: String,
    val bodyText: CharSequence?
)

data class DraftModel(
    val draftTranslations: List<Translation> = emptyList(),
    val importResult: ImportDraft.Result? = null,
    val progress: ProgressHelper.Progress? = null,
    val chapterContent: ChapterContent? = null
)

class DraftViewModel (
    private val application: Application,
    private val translator: Translator,
    private val library: Door43Client,
    private val importDraft: ImportDraft
) : AndroidViewModel(application) {

    private val _model = MutableStateFlow(DraftModel())
    val model: StateFlow<DraftModel> = _model.asStateFlow()

    fun loadDraftTranslations(targetTranslationId: String?) {
        viewModelScope.launch {
            translator.getTargetTranslation(targetTranslationId)?.let { targetTranslation ->
                val translations = library.index.findTranslations(
                    targetTranslation.targetLanguage.slug,
                    targetTranslation.projectId,
                    null,
                    "book",
                    null,
                    0,
                    -1
                ).filter { it.resource.slug != "udb" }

                _model.update {
                    it.copy(draftTranslations = translations)
                }
            }
        }
    }

    fun importDraft(sourceContainer: ResourceContainer) {
        viewModelScope.launch {
            _model.update {
                it.copy(progress = ProgressHelper.Progress(
                application.getString(R.string.please_wait)
                ))
            }
            val result = withContext(Dispatchers.IO) {
                importDraft.execute(sourceContainer)
            }
            _model.update {
                it.copy(importResult = result, progress = null)
            }
        }
    }

    fun getResourceContainer(rcSlug: String): ResourceContainer? {
        return try {
            library.open(rcSlug)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getSourceLanguage(draftTranslation: ResourceContainer): SourceLanguage? {
        return try {
            library.index.getSourceLanguage(
                draftTranslation.info.getJSONObject("language").getString("slug")
            )
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    suspend fun parseChapterContent(
        chapterSlug: String,
        container: ResourceContainer,
        renderingProvider: RenderingProvider,
        clickInterceptor: Span.OnClickListener
    ): ChapterContent = withContext(Dispatchers.IO) {

        var tempTitle = container.readChunk(chapterSlug, "title")
        if (tempTitle == null) {
            tempTitle = container.readChunk("front", "title") + " " + chapterSlug.toInt()
        }
        val title = tempTitle

        var chapterBody = ""
        val chunks = container.chunks(chapterSlug).apply { sortNumerically() }
        for (chunk in chunks) {
            chapterBody += container.readChunk(chapterSlug, chunk)
        }

        val mimeType = container.info?.optString("content_mime_type")
        val bodyFormat = try {
            TranslationFormat.parse(mimeType)
        } catch (e: Exception) { null }

        val sourceRendering = RenderingGroup()
        var heading = ""

        if (Clickables.isClickableFormat(bodyFormat)) {
            val renderer = renderingProvider.setupRenderingGroup(
                bodyFormat,
                sourceRendering,
                null,
                clickInterceptor,
                true
            )
            renderer.setSuppressLeadingMajorSectionHeadings(true)
            heading = renderer.getLeadingMajorSectionHeading(chapterBody).toString()
        } else {
            sourceRendering.addEngine(renderingProvider.createDefaultRenderer())
        }

        sourceRendering.init(chapterBody)
        val bodyText = sourceRendering.start()

        ChapterContent(
            heading = heading,
            title = title,
            bodyText = bodyText
        )
    }
}