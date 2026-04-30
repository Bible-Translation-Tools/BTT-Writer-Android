package com.door43.usecases

import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Util
import com.door43.translationstudio.ui.translate.TranslationHelp
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecontainer.Link
import java.util.regex.Pattern

class RenderHelps(
    private val catalogClient: ResourceCatalogClient
) {
    fun execute(chunk: Chunk): Map<String, Any> {

        // init default values
        val result: MutableMap<String, Any> = HashMap()
        result["words"] = ArrayList<Any>()
        result["questions"] = ArrayList<Any>()
        result["notes"] = ArrayList<Any>()

        if (chunk.config.containsKey("words")) {
            val links = getWordsLinks(chunk.config["words"]!!, chunk)
            if (links.isNotEmpty()) {
                result["words"] = links
            }
        }
        val translationQuestions = getTranslationQuestions(chunk)
        if (translationQuestions.isNotEmpty()) {
            result["questions"] = translationQuestions
        }
        val translationNotes = getTranslationNotes(chunk)
        if (translationNotes.isNotEmpty()) {
            result["notes"] = translationNotes
        }

        return result
    }

    private fun getWordsLinks(config: List<String>, chunk: Chunk): List<Link> {
        val links = ContainerCache.cacheFromLinks(
            catalogClient,
            config,
            chunk.source.language
        )
        val titlePattern = Pattern.compile("#(.*)")
        return links.mapNotNull { link ->
            try {
                val rc = link.project?.let { project ->
                    link.resource?.let { resource ->
                        ContainerCache.cacheClosest(
                            catalogClient,
                            chunk.source.language.slug,
                            project,
                            resource
                        )
                    }
                }
                if (rc != null) {
                    // TODO: 10/12/16 the words need to have their title placed into
                    //  a "title" file instead of being inline in the chunk
                    link.chapter?.let { chapter ->
                        val word = rc.readChunk(chapter, "01")
                        val match = titlePattern.matcher(word.trim())
                        if (match.find()) {
                            link.copy(title = match.group(1))
                        } else null
                    }
                } else {
                    Logger.w(
                        RenderHelps::class.java.simpleName,
                        "could not find resource container for words " + link.language + "-" + link.project + "-" + link.resource
                    )
                    null
                }
            } catch (e: Exception) {
                Logger.e(RenderHelps::class.java.simpleName, e.message ?: "error", e)
                null
            }
        }
    }

    private fun getTranslationQuestions(chunk: Chunk): List<TranslationHelp> {
        val translationQuestions = arrayListOf<TranslationHelp>()
        val questionTranslations = catalogClient.library.findTranslations(
            chunk.source.language.slug,
            chunk.source.project.slug,
            "tq",
            "help",
            null,
            0,
            -1
        )
        if (questionTranslations.isNotEmpty()) {
            try {
                val rc = ContainerCache.cache(
                    catalogClient,
                    questionTranslations[0].resourceContainerSlug
                )
                if(rc != null) {
                    // TRICKY: questions are id'd by verse not chunk
                    val verses = rc.chunks(chunk.chapterSlug)
                    var rawQuestions = ""
                    // TODO: 2/21/17 this is very inefficient.
                    //  We should only have to map chunk id's once, not for every chunk.
                    for (verse in verses) {
                        val vChunk = Util.mapVerseToChunk(
                            chunk.source,
                            chunk.chapterSlug,
                            verse
                        )
                        if (verse == vChunk) {
                            rawQuestions += "\n\n${rc.readChunk(chunk.chapterSlug, verse)}"
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
                Logger.e(RenderHelps::class.java.simpleName, e.message ?: "Error", e);
            }
        }

        return translationQuestions
    }

    private fun getTranslationNotes(chunk: Chunk): List<TranslationHelp> {
        val translationNotes = arrayListOf<TranslationHelp>()
        val noteTranslations = catalogClient.library.findTranslations(
            chunk.source.language.slug,
            chunk.source.project.slug,
            "tn",
            "help",
            null,
            0,
            -1
        )
        if (noteTranslations.isNotEmpty()) {
            try {
                val rc = ContainerCache.cache(
                    catalogClient,
                    noteTranslations[0].resourceContainerSlug
                )
                if (rc != null) {
                    val rawNotes = rc.readChunk(chunk.chapterSlug, chunk.chunkSlug)
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
                Logger.e(RenderHelps::class.java.simpleName, e.message ?: "Error", e)
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
        val helpTextArray = rawText.split("#")
        for (helpText in helpTextArray) {
            if (helpText.trim().isEmpty()) continue

            // split help title and body
            val parts = helpText.trim().split("\n", limit = 2)
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