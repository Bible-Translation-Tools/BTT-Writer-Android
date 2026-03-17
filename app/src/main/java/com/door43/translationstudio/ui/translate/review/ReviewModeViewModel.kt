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
import com.door43.translationstudio.core.SlugSorter
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.rendering.RenderNodeConverter
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.model.LinkData
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_ENABLE_TM_LINKS
import com.door43.translationstudio.ui.spannables.ArticleLinkSpan
import com.door43.translationstudio.ui.spannables.PassageLinkSpan
import com.door43.translationstudio.ui.spannables.ShortReferenceSpan
import com.door43.translationstudio.ui.spannables.TranslationWordLinkSpan
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

data class NoteHelp(
    val title: String,
    val body: AnnotatedString
)

data class WordHelp(
    val title: String,
    val body: AnnotatedString
)

data class ReviewState(
    val resourcesOpen: Boolean = false,
    val noteHelp: NoteHelp? = null,
    val wordHelp: WordHelp? = null
) : ModeState

sealed interface ReviewAction : ModeAction {
    data class ItemTextChanged(val item: ReviewItem, val text: String) : ReviewAction
    data class OpenResources(val value: Boolean) : ReviewAction
    data class RenderHelps(val item: ReviewItem) : ReviewAction
    data class OpenHelp(val item: HelpItem) : ReviewAction
    object ClearHelp : ReviewAction
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
            ReviewAction.ClearHelp -> clearHelp()
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
            is HelpItem.Note -> renderNote(item.data)
            is HelpItem.Word -> renderWord(item.rcSlug, item.data.chapter)
            is HelpItem.Question -> renderQuestion(item.data)
        }
    }

    private fun renderNote(note: TranslationHelp) {
        val enableTmLinks = prefRepository.getDefaultPref(
            KEY_PREF_ENABLE_TM_LINKS,
            false
        )

        val renderer = RenderingProvider().createHtmlRenderer { span ->
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
                    // Not implemented in original Java code
                }

                is TranslationWordLinkSpan -> {
                    val currentRC = getSelectedSourceTranslationId()?.let { id ->
                        ContainerCache.get(id) ?: ContainerCache.cache(library, id)
                    }
                    if (currentRC != null) {
                        val titlePattern = Pattern.compile("#(.*)")
                        val rc = getClosestResourceContainer(currentRC.language.slug, "bible", "tw")
                        if (rc != null) {
                            val word = rc.readChunk(span.machineReadable, "01")
                            if (word.isNotEmpty()) {
                                val linkMatch = titlePattern.matcher(word.trim())
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

                is ShortReferenceSpan -> {
                    // Not implemented fully in original Java code
                }
            }
            result
        }
        val html = renderer.toAnnotatedHtml(note.body)
        val title = note.title
        val body = ComposeTextAdapter.convertHtml(
            html = html,
            onLinkClick = { println(it) }
        )

        _state.update {
            it.copy(noteHelp = NoteHelp(title, body))
        }
    }

    private fun renderWord(rcSlug: String, chapterSlug: String) {
        getResourceContainer(rcSlug)?.let { rc ->
            val enableTmLinks = prefRepository.getDefaultPref(
                KEY_PREF_ENABLE_TM_LINKS,
                false
            )
            val currentRC = getSelectedSourceTranslationId()?.let { id ->
                ContainerCache.get(id) ?: ContainerCache.cache(library, id)
            }

            val word = rc.readChunk(chapterSlug, "01")
            val pattern = Pattern.compile("#+([^\\n]+)\\n+([\\s\\S]*)")
            val match = pattern.matcher(word)
            var description = ""
            var title = ""

            if (match.find()) {
                title = match.group(1) ?: ""
                description = match.group(2) ?: ""
            }

            val renderer = RenderingProvider().createHtmlRenderer { span ->
//                var result = false
//                when (span) {
//                    is ArticleLinkSpan -> {
//                        val title = application.getString(R.string.tm_title, span.section, span.slug)
//                        span.setTitle(title)
//                        result = enableTmLinks
//                    }
//                    is PassageLinkSpan -> {
//                        val chunk = rc.readChunk(span.chapterId, span.frameId)
//                        val verseTitle = Frame.parseVerseTitle(
//                            chunk,
//                            TranslationFormat.parse(rc.contentMimeType)
//                        )
//                        val chapterId = try {
//                            span.chapterId.toInt().toString()
//                        } catch (_: Exception) {
//                            span.chapterId
//                        }
//                        val title = "${rc.readChunk("front", "title")} $chapterId:$verseTitle"
//                        span.setTitle(title)
//                        result = chunk.isNotEmpty()
//                    }
//                    is TranslationWordLinkSpan -> {
//                        val currentRC = getSelectedSourceTranslationId()?.let { id ->
//                            ContainerCache.get(id) ?: ContainerCache.cache(library, id)
//                        }
//                        if (currentRC != null) {
//                            val titlePattern = Pattern.compile("#(.*)")
//                            val closestRc = getClosestResourceContainer(currentRC.language.slug, "bible", "tw")
//
//                            if (closestRc != null) {
//                                val closestWord = closestRc.readChunk(span.machineReadable, "01")
//                                if (closestWord.isNotEmpty()) {
//                                    val linkMatch = titlePattern.matcher(closestWord.trim())
//                                    var title = span.machineReadable
//                                    if (linkMatch.find()) {
//                                        title = linkMatch.group(1) ?: title
//                                    }
//                                    span.title = title
//                                    result = true
//                                }
//                            }
//                        }
//                    }
//                }
//                result
                true
            }

            val html = renderer.toAnnotatedHtml(description)
            val body = ComposeTextAdapter.convertHtml(
                html = html,
                onLinkClick = { link ->
                    when (link) {
                        is LinkData.Markdown -> {
                            currentRC?.let { rc ->
                                val closestRc = getClosestResourceContainer(rc.language.slug, "bible", "tw")
                                if (closestRc != null) {
                                    _state.update { it.copy(wordHelp = null) }
                                    val word = link.address.substringAfterLast('/')
                                        .substringBeforeLast('.')
                                    renderWord(rcSlug, word)
                                }
                            }
                        }
                        is LinkData.Article -> {

                        }
                        else -> {}
                    }
                    println(link)
                }
            )

            _state.update {
                it.copy(wordHelp = WordHelp(title, body))
            }
        }
    }

    private fun renderQuestion(question: TranslationHelp) {
        renderNote(question)
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

    private fun clearHelp() {
        _state.update { it.copy(noteHelp = null, wordHelp = null) }
    }
}