package com.door43.translationstudio.ui.translate.review

import android.app.Application
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewModelScope
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.rendering.spannables.ArticleLinkSpan
import com.door43.translationstudio.rendering.spannables.PassageLinkSpan
import com.door43.translationstudio.rendering.spannables.TranslationWordLinkSpan
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_ENABLE_TM_LINKS
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_TM_URL
import com.door43.translationstudio.ui.textadapters.ComposeTextAdapter
import com.door43.translationstudio.ui.translate.ModeAction
import com.door43.translationstudio.ui.translate.ModeState
import com.door43.translationstudio.ui.translate.ModeViewModel
import com.door43.translationstudio.ui.translate.ReviewItem
import com.door43.translationstudio.ui.translate.TargetTranslationActivity.Companion.SEARCH_SOURCE
import com.door43.translationstudio.ui.translate.TranslationHelp
import com.door43.usecases.RenderHelps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.util.Locale
import java.util.regex.Pattern

sealed class Help {
    abstract val title: String
    abstract val body: AnnotatedString

    data class Notes(
        override val title: String,
        override val body: AnnotatedString
    ) : Help()

    data class Words(
        override val title: String,
        override val body: AnnotatedString
    ) : Help()

    data class Questions(
        override val title: String,
        override val body: AnnotatedString
    ) : Help()
}

data class ReviewState(
    val resourcesOpen: Boolean = false,
    val help: Help? = null,
    val url: String? = null
) : ModeState

sealed interface ReviewAction : ModeAction {
    data class ItemTextChanged(val item: ReviewItem, val text: String) : ReviewAction
    data class OpenResources(val value: Boolean) : ReviewAction
    data class RenderHelps(val item: ReviewItem) : ReviewAction
    data class OpenHelp(val item: HelpItem) : ReviewAction
    object ClearHelp : ReviewAction
    object CleanUrl : ReviewAction
}

class ReviewModeViewModel(
    chunks: StateFlow<List<Chunk>>,
    private val targetTranslation: TargetTranslation,
    private val prefRepository: IPreferenceRepository,
    private val renderHelps: RenderHelps,
    private val renderingProvider: RenderingProvider,
    private val library: Door43Client,
    private val translator: Translator
) : ModeViewModel<ReviewItem>(chunks), KoinComponent {

    private val application: Application by inject()

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state

    override fun mapToChildType(chunks: List<Chunk>): List<ReviewItem> {
        return chunks.chunked(5).flatMap { batch ->
            batch.map { prepareItem(it) }
        }
    }

    override fun onAction(action: ModeAction) {
        super.onAction(action)
        when (action) {
            is ReviewAction.OpenResources -> openResources(action.value)
            is ReviewAction.RenderHelps -> onRenderHelps(action.item)
            is ReviewAction.OpenHelp -> onOpenHelpItem(action.item)
            ReviewAction.ClearHelp -> _state.update { it.copy(help = null) }
            ReviewAction.CleanUrl -> _state.update { it.copy(url = null) }
        }
    }

    private fun prepareItem(chunk: Chunk): ReviewItem {
        val (sourceText, renderedSourceText) = prepareSource(chunk)
        val (targetText, renderedTargetText) = prepareTarget(chunk)
        val (pt, ct, ft) = prepareTranslations(chunk)

        return ReviewItem(
            id  = "${chunk.chapterSlug}-${chunk.chunkSlug}",
            chunk = chunk,
            sourceText = sourceText,
            targetText = targetText,
            renderedSourceText = renderedSourceText,
            renderedTargetText = renderedTargetText,
            pt = pt,
            ct = ct,
            ft = ft
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
            RenderingProvider().setupRenderingGroup(
                format,
                renderingGroup,
                verseDisplay = VerseDisplay.NUMBER,
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

    private fun openResources(value: Boolean) {
        _state.update { it.copy(resourcesOpen = value) }
    }

    private fun onRenderHelps(item: ReviewItem) {
        if (!item.helps.isEmpty()) return

        viewModelScope.launch {
            val helps = withContext(Dispatchers.Default) {
                renderHelps.execute(item.chunk, item.chunkConfig)
            }
            updateItem(item.copy(helps = helps))
        }
    }

    private fun onOpenHelpItem(item: HelpItem) {
        when (item) {
            is HelpItem.Note -> renderTranslationHelp(item.data)
            is HelpItem.Word -> renderWord(item.rcSlug, item.data.chapter)
            is HelpItem.Question -> renderTranslationHelp(item.data, true)
        }
    }

    private fun renderTranslationHelp(note: TranslationHelp, isTq: Boolean = false) {
        val title = note.title
        val body = renderHelpContents(note.body)
        val help = if (!isTq) {
            Help.Notes(title, body)
        } else Help.Questions(title, body)

        _state.update { it.copy(help = help) }
    }

    private fun renderWord(rcSlug: String, chapterSlug: String) {
        getResourceContainer(rcSlug)?.let { twRc ->
            val word = twRc.readChunk(chapterSlug, "01")
            val pattern = Pattern.compile("#+([^\\n]+)\\n+([\\s\\S]*)")
            val match = pattern.matcher(word)
            var description = ""
            var title = ""

            if (match.find()) {
                title = match.group(1) ?: ""
                description = match.group(2) ?: ""
            }

            val body = renderHelpContents(description, twRc)

            _state.update { it.copy(help = Help.Words(title, body)) }
        }
    }

    private fun renderHelpContents(
        contents: String,
        twRc: ResourceContainer? = null
    ): AnnotatedString {
        val enableTmLinks = prefRepository.getDefaultPref(
            KEY_PREF_ENABLE_TM_LINKS,
            false
        )

        val sourceRC = getSelectedSourceTranslationId()?.let { id ->
            ContainerCache.get(id) ?: ContainerCache.cache(library, id)
        }
        val closestRc = sourceRC?.let { rc ->
            getClosestResourceContainer(
                rc.language.slug,
                "bible",
                "tw"
            )
        }

        val renderer = renderingProvider.createHtmlRenderer { span ->
            var result = false
            when (span) {
                is ArticleLinkSpan -> {
                    val title = application.getString(
                        R.string.tm_title,
                        span.section,
                        span.slug
                    )
                    span.setTitle(title)
                    result = enableTmLinks
                }
                is PassageLinkSpan -> {
                    twRc?.let { rc ->
                        val chunk = rc.readChunk(span.chapterId, span.frameId)
                        val verseTitle = Frame.parseVerseTitle(
                            chunk,
                            TranslationFormat.parse(rc.contentMimeType)
                        )
                        val chapterId = try {
                            span.chapterId.toInt().toString()
                        } catch (_: Exception) {
                            span.chapterId
                        }
                        val title = "${rc.readChunk("front", "title")} $chapterId:$verseTitle"
                        span.setTitle(title)
                        result = chunk.isNotEmpty()
                    }
                }
                is TranslationWordLinkSpan -> {
                    if (sourceRC != null) {
                        val titlePattern = Pattern.compile("#(.*)")
                        closestRc?.let { rc ->
                            val closestWord = rc.readChunk(
                                span.machineReadable,
                                "01"
                            )
                            if (closestWord.isNotEmpty()) {
                                val linkMatch = titlePattern.matcher(closestWord.trim())
                                var title = span.machineReadable
                                if (linkMatch.find()) {
                                    title = linkMatch.group(1) ?: title
                                }
                                span.title = title
                                result = true
                            }
                        }
                    }
                }
            }
            result
        }

        val html = renderer.toAnnotatedHtml(contents)
        val body = ComposeTextAdapter.convertHtml(
            html = html,
            onLinkClick = { link ->
                when (link) {
                    is LinkData.TranslationWord -> {
                        twRc?.let { rc ->
                            _state.update { it.copy(help = null) }
                            renderWord(rc.slug, link.id)
                        }
                    }
                    is LinkData.Article -> {
                        val baseUrl = prefRepository.getDefaultPref(
                            KEY_PREF_TM_URL,
                            application.getString(R.string.pref_default_tm_url),
                            String::class.javaObjectType
                        )
                        val span = link.toSpan()
                        val url = "$baseUrl?section=${span.section}#${span.slug}"
                        _state.update { it.copy(url = url) }
                    }
                    else -> {}
                }
            },
            linkFilter = {
                it is LinkData.TranslationWord || it is LinkData.Article
            }
        )

        return body
    }

    private fun getClosestResourceContainer(
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String
    ): ResourceContainer? {
        return ContainerCache.cacheClosest(library, languageSlug, projectSlug, resourceSlug)
    }

    private fun getSelectedSourceTranslationId(): String? {
        return translator.getSelectedSourceTranslationId(targetTranslation.id)
    }

    private fun getResourceContainer(slug: String): ResourceContainer? {
        return ContainerCache.get(slug)
    }
}