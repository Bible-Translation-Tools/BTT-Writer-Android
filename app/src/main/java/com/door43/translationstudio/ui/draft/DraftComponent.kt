package com.door43.translationstudio.ui.draft

import android.app.Application
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ComponentScope
import com.door43.translationstudio.core.Progress
import com.door43.translationstudio.core.ProgressManager
import com.door43.translationstudio.core.ProgressOwner
import com.door43.translationstudio.core.TaskHandle
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.launchWithProgress
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.RenderNode
import com.door43.usecases.ImportDraft
import com.door43.util.sortNumerically
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer

data class ChapterContent(
    val heading: String,
    val title: String,
    val renderNodes: List<RenderNode> = emptyList()
)

interface DraftComponent {

    val state: StateFlow<State>
    val progress: StateFlow<Progress?>

    fun onNavigateBack()
    fun getResourceContainer(rcSlug: String): ResourceContainer?
    fun getSourceLanguage(draftTranslation: ResourceContainer): SourceLanguage?
    suspend fun parseChapterContent(
        chapterSlug: String,
        container: ResourceContainer,
        renderingProvider: RenderingProvider
    ): ChapterContent
    fun importDraft(sourceContainer: ResourceContainer)

    fun onFinish()

    data class State(
        val draftTranslations: List<Translation> = emptyList(),
        val importResult: ImportDraft.Result? = null,
        val chapterContent: ChapterContent? = null
    )

    sealed interface Result {
        data object NavigateBack : Result
    }
}

class DefaultDraftComponent(
    componentContext: ComponentContext,
    translationId: String,
    private val onResult: (DraftComponent.Result) -> Unit
) : DraftComponent,
    ComponentContext by componentContext,
    KoinComponent, ComponentScope, ProgressOwner {

    private val application: Application by inject()
    private val translator: Translator by inject()
    private val library: Door43Client by inject()
    private val importDraft: ImportDraft by inject()

    override val coroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val progressManager = ProgressManager(coroutineScope)
    override val progress get() = progressManager.progress

    private val _state = MutableStateFlow(DraftComponent.State())
    override val state: StateFlow<DraftComponent.State> = _state.asStateFlow()

    override suspend fun runTask(message: String?, block: suspend (TaskHandle) -> Unit) {
        progressManager.runTask(message, block)
    }

    init {
        loadDraftTranslations(translationId)

        lifecycle.doOnDestroy {
            coroutineScope.cancel()
        }
    }

    override fun onNavigateBack() {
        onResult(DraftComponent.Result.NavigateBack)
    }

    override fun getResourceContainer(rcSlug: String): ResourceContainer? {
        return try {
            library.open(rcSlug)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun getSourceLanguage(draftTranslation: ResourceContainer): SourceLanguage? {
        return try {
            library.index.getSourceLanguage(
                draftTranslation.info.getJSONObject("language").getString("slug")
            )
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun parseChapterContent(
        chapterSlug: String,
        container: ResourceContainer,
        renderingProvider: RenderingProvider
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

        val mimeType = container.info.optString("content_mime_type")
        val bodyFormat = TranslationFormat.parse(mimeType)

        val sourceRendering = RenderingGroup()
        var heading = ""

        if (Clickables.isClickableFormat(bodyFormat)) {
            val renderer = renderingProvider.setupRenderingGroup(
                bodyFormat,
                sourceRendering,
                verseDisplay = VerseDisplay.NUMBER,
                target = true
            )
            renderer.setSuppressLeadingMajorSectionHeadings(true)
            heading = renderer.getLeadingMajorSectionHeading(chapterBody)
        } else {
            sourceRendering.addEngine(renderingProvider.createDefaultRenderer())
        }

        sourceRendering.init(chapterBody)
        val renderNodes = sourceRendering.start()

        ChapterContent(
            heading = heading,
            title = title,
            renderNodes = renderNodes
        )
    }

    override fun importDraft(sourceContainer: ResourceContainer) {
        launchWithProgress(
            application.getString(R.string.please_wait)
        ) {
            val result = withContext(Dispatchers.IO) {
                importDraft.execute(sourceContainer)
            }
            _state.update { it.copy(importResult = result) }
        }
    }

    override fun onFinish() {
        onResult(DraftComponent.Result.NavigateBack)
    }

    private fun loadDraftTranslations(targetTranslationId: String?) {
        launchWithProgress(
            application.getString(R.string.please_wait)
        ) {
            targetTranslationId?.let { id ->
                translator.getTargetTranslation(id)?.let { targetTranslation ->
                    val translations = library.index.findTranslations(
                        targetTranslation.targetLanguage.slug,
                        targetTranslation.projectId,
                        null,
                        "book",
                        null,
                        0,
                        -1
                    ).filter { it.resource.slug != "udb" }

                    _state.update {
                        it.copy(draftTranslations = translations)
                    }
                }
            }
        }
    }
}