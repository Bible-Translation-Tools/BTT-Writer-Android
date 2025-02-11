package com.door43.usecases

import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Util
import com.door43.translationstudio.ui.translate.ListItem
import com.door43.translationstudio.ui.translate.TranslationHelp
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.Link
import org.unfoldingword.tools.logger.Logger
import java.util.regex.Pattern
import javax.inject.Inject

class RenderHelps @Inject constructor(
    private val library: Door43Client
) {
    data class RenderHelpsResult(val item: ListItem, val helps: Map<String, Any>)

    fun execute(item: ListItem): RenderHelpsResult {

        // init default values
        val result: MutableMap<String, Any> = HashMap()
        result["words"] = ArrayList<Any>()
        result["questions"] = ArrayList<Any>()
        result["notes"] = ArrayList<Any>()

        val config = item.chunkConfig

        if (config != null && config.containsKey("words")) {
            val links = getWordsLinks(config["words"]!!, item)
            if (links.isNotEmpty()) {
                result["words"] = links
            }
        }
        val translationQuestions = getTranslationQuestions(item)
        if (translationQuestions.isNotEmpty()) {
            result["questions"] = translationQuestions
        }
        val translationNotes = getTranslationNotes(item)
        if (translationNotes.isNotEmpty()) {
            result["notes"] = translationNotes
        }

        return RenderHelpsResult(item, result)
    }

    private fun getWordsLinks(config: List<String>, item: ListItem): List<Link> {
        val links = ContainerCache.cacheFromLinks(
            library,
            config,
            item.source.language
        )
        val titlePattern = Pattern.compile("#(.*)")
        for (link in links) {
            try {
                val rc = ContainerCache.cacheClosest(
                    library,
                    item.source.language.slug,
                    link.project,
                    link.resource
                )
                if (rc != null) {
                    // TODO: 10/12/16 the words need to have their title placed into
                    //  a "title" file instead of being inline in the chunk
                    val word = rc.readChunk(link.chapter, "01")
                    val match = titlePattern.matcher(word.trim())
                    if (match.find()) {
                        link.title = match.group(1)
                    }
                } else {
                    Logger.w(
                        RenderHelps::class.java.simpleName,
                        "could not find resource container for words " + link.language + "-" + link.project + "-" + link.resource
                    )
                }
            } catch (e: Exception) {
                Logger.e(RenderHelps::class.java.simpleName, e.message, e)
            }
        }
        return links
    }

    private fun getTranslationQuestions(item: ListItem): List<TranslationHelp> {
        val translationQuestions = arrayListOf<TranslationHelp>()
        val questionTranslations = library.index.findTranslations(
            item.source.language.slug,
            item.source.project.slug,
            "tq",
            "help",
            null,
            0,
            -1
        )
        if (questionTranslations.isNotEmpty()) {
            try {
                val rc = ContainerCache.cache(
                    library,
                    questionTranslations[0].resourceContainerSlug
                )
                if(rc != null) {
                    // TRICKY: questions are id'd by verse not chunk
                    val verses = rc.chunks(item.chapterSlug)
                    var rawQuestions = ""
                    // TODO: 2/21/17 this is very inefficient.
                    //  We should only have to map chunk id's once, not for every chunk.
                    for (verse in verses) {
                        val chunk = Util.mapVerseToChunk(
                            item.source,
                            item.chapterSlug,
                            verse
                        )
                        if (verse == chunk) {
                            rawQuestions += "\n\n${rc.readChunk(item.chapterSlug, verse)}"
                        }
                    }
                    val helps: List<TranslationHelp> = parseHelps(rawQuestions.trim())
                    translationQuestions.addAll(helps)
                } else {
                    Logger.w(
                        RenderHelps::class.java.simpleName,
                        "could not find resource container for questions " + questionTranslations[0].resourceContainerSlug
                    )
                }
            } catch (e: Exception) {
                Logger.e(RenderHelps::class.java.simpleName, e.message, e);
            }
        }

        return translationQuestions
    }

    private fun getTranslationNotes(item: ListItem): List<TranslationHelp> {
        val translationNotes = arrayListOf<TranslationHelp>()
        val noteTranslations = library.index.findTranslations(
            item.source.language.slug,
            item.source.project.slug,
            "tn",
            "help",
            null,
            0,
            -1
        )
        if (noteTranslations.isNotEmpty()) {
            try {
                val rc = ContainerCache.cache(
                    library,
                    noteTranslations[0].resourceContainerSlug
                )
                if (rc != null) {
                    val rawNotes = rc.readChunk(item.chapterSlug, item.chunkSlug)
                    if (rawNotes.isNotEmpty()) {
                        val helps: List<TranslationHelp> = parseHelps(rawNotes)
                        translationNotes.addAll(helps)
                    }
                } else {
                    Logger.w(
                        RenderHelps::class.java.simpleName,
                        "could not find resource container for notes " + noteTranslations[0].resourceContainerSlug
                    )
                }
            } catch (e: java.lang.Exception) {
                Logger.e(RenderHelps::class.java.simpleName, e.message, e)
            }
        }
        return translationNotes
    }

    /**
     * Splits some raw help text into translation helps
     * @param rawText the help text
     * @return
     */
    private fun parseHelps(rawText: String): List<TranslationHelp> {
        val helps = arrayListOf<TranslationHelp>()
        val foundTitles = arrayListOf<String>()

        // split up multiple helps
        val helpTextArray = rawText.split("#".toRegex())
        for (helpText in helpTextArray) {
            if (helpText.trim().isEmpty()) continue

            // split help title and body
            val parts = helpText.trim().split("\n".toRegex(), limit = 2)
            var title = parts[0].trim()
            var body = if (parts.size > 1) parts[1].trim() else null

            // prepare snippets (has no title)
            val maxSnippetLength = 50
            if (body == null) {
                body = title
                if (title.length > maxSnippetLength) {
                    title = title.substring(0, maxSnippetLength) + "..."
                }
            }
            // TRICKY: avoid duplicates. e.g. if a question appears in verses 1 and 2
            // while the chunk spans both verses.
            if (!foundTitles.contains(title)) {
                foundTitles.add(title)
                helps.add(TranslationHelp(title, body))
            }
        }
        return helps
    }
}