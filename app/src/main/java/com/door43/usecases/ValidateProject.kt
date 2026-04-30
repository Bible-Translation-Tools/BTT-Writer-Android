package com.door43.usecases

import android.content.Context
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Validation
import com.door43.util.StringUtilities
import com.door43.util.sortNumerically
import org.bibletranslationtools.logger.Logger
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.bibletranslationtools.resourcecontainer.ResourceContainer

class ValidateProject(
    private val context: Context,
    private val catalogClient: ResourceCatalogClient,
    private val translator: Translator
) {
    fun execute(targetTranslationId: String, sourceTranslationId: String): List<Validation> {
        val validations = arrayListOf<Validation>()

        translator.getTargetTranslation(targetTranslationId)?.let { targetTranslation ->
            val targetLanguage = catalogClient.library.getTargetLanguage(
                targetTranslation.targetLanguageId
            ) ?: return validations

            val container = try {
                catalogClient.openResourceContainer(sourceTranslationId)
            } catch (e: Exception) {
                Logger.e(
                    "ValidationTask",
                    "Failed to load resource container",
                    e
                )
                return listOf()
            }

            val sourceFormat = try {
                TranslationFormat.parse(container.info.contentMimeType)
            } catch (e: Exception) {
                Logger.e(
                    "ValidationTask",
                    "Failed to read the translation format from the container",
                    e
                )
                return listOf()
            }

            val projectTitle = container.readChunk("front", "title")
            val sourceLanguage = catalogClient.library.getSourceLanguage(
                container.language.slug
            ) ?: return validations
            val chapters = container.chapters()

            // validate chapters
            var lastValidChapterIndex = -1
            val chapterValidations = arrayListOf<Validation>()

            chapters.sortNumerically()
            for (i in chapters.indices) {
                val chapterSlug = chapters[i]
                val chunks = container.chunks(chapterSlug)
                chunks.sortNumerically()

                // validate frames
                var lastValidFrameIndex = -1
                var chapterIsValid = true
                val frameValidations = arrayListOf<Validation>()

                val chapterTranslation = targetTranslation.getChapterTranslation(chapterSlug)
                if (MergeConflictsHandler.isMergeConflicted(chapterTranslation.title) ||
                    chunks.contains("title") &&
                    !chapterTranslation.titleFinished
                ) {
                    chapterIsValid = false
                    frameValidations.add(
                        Validation.InvalidFrame(
                            title = getChunkTitle(
                                container,
                                chapterSlug,
                                "title",
                                context.getString(R.string.title)
                            ),
                            titleLanguage = sourceLanguage,
                            body = chapterTranslation.title,
                            bodyLanguage = targetLanguage,
                            bodyFormat = TranslationFormat.DEFAULT,
                            targetTranslationId = targetTranslationId,
                            chapterId = chapterSlug,
                            frameId = "00"
                        )
                    )
                }

                if (MergeConflictsHandler.isMergeConflicted(chapterTranslation.reference) ||
                    chunks.contains("reference") &&
                    !chapterTranslation.referenceFinished
                ) {
                    chapterIsValid = false
                    frameValidations.add(
                        Validation.InvalidFrame(
                            title = getChunkTitle(
                                container,
                                chapterSlug,
                                "reference",
                                context.getString(R.string.reference)
                            ),
                            titleLanguage = sourceLanguage,
                            body = chapterTranslation.reference,
                            bodyLanguage = targetLanguage,
                            bodyFormat = TranslationFormat.DEFAULT,
                            targetTranslationId = targetTranslationId,
                            chapterId = chapterSlug,
                            frameId = "00"
                        )
                    )
                }

                for (j in chunks.indices) {
                    val chunkSlug = chunks[j]
                    // if chunk types we have already handled, then skip
                    if (chunkSlug == "title" || chunkSlug == "reference") {
                        continue
                    }

                    val frameTranslation = targetTranslation.getFrameTranslation(
                        chapterSlug,
                        chunkSlug,
                        TranslationFormat.DEFAULT
                    )
                    val chunkText = container.readChunk(chapterSlug, chunkSlug)
                    // TODO: also validate the checking questions
                    val finishedOrEmpty = frameTranslation.finished || chunkText.isEmpty()
                    val mergeConflicted = MergeConflictsHandler.isMergeConflicted(
                        frameTranslation.body
                    )
                    val isLastChunk = j == chunks.size - 1

                    if (lastValidFrameIndex == -1 && finishedOrEmpty) {
                        // start new valid range
                        lastValidFrameIndex = j
                    } else if (mergeConflicted || !finishedOrEmpty || isLastChunk) {
                        // close valid range
                        if (lastValidFrameIndex > -1) {
                            var previousFrameIndex = j - 1
                            if (finishedOrEmpty) {
                                previousFrameIndex = j
                            }
                            if (lastValidFrameIndex < previousFrameIndex) {
                                // range
                                val previousFrame = container.readChunk(
                                    chapterSlug,
                                    chunks[previousFrameIndex]
                                )
                                val lastValidText = container.readChunk(
                                    chapterSlug,
                                    chunks[lastValidFrameIndex]
                                )
                                val formattedChapter = StringUtilities.formatNumber(
                                    chapterSlug
                                )
                                var frameTitle = "$projectTitle $formattedChapter"
                                val frameStartVerse = Frame.getStartVerse(
                                    lastValidText,
                                    sourceFormat
                                )
                                val frameEndVerse = Frame.getEndVerse(
                                    previousFrame,
                                    sourceFormat
                                )
                                frameTitle += ":$frameStartVerse-$frameEndVerse"

                                frameValidations.add(
                                    Validation.ValidFrame(
                                        frameTitle,
                                        sourceLanguage,
                                        true
                                    )
                                )
                            } else {
                                val lastValidText = container.readChunk(
                                    chapterSlug,
                                    chunks[lastValidFrameIndex]
                                )
                                val formattedChapter = StringUtilities.formatNumber(
                                    chapterSlug
                                )
                                var frameTitle = "$projectTitle $formattedChapter"
                                val frameStartVerse = Frame.getStartVerse(
                                    lastValidText,
                                    sourceFormat
                                )
                                val frameEndVerse = Frame.getEndVerse(
                                    lastValidText,
                                    sourceFormat
                                )
                                frameTitle += ":$frameStartVerse"

                                if (frameStartVerse != frameEndVerse) {
                                    frameTitle += "-$frameEndVerse"
                                }
                                frameValidations.add(
                                    Validation.ValidFrame(
                                        frameTitle,
                                        sourceLanguage,
                                        false
                                    )
                                )
                            }
                            lastValidFrameIndex = -1
                        }

                        // add invalid frame
                        if (!finishedOrEmpty) {
                            chapterIsValid = false
                            val formattedChapter = StringUtilities.formatNumber(chapterSlug)
                            var frameTitle = "$projectTitle $formattedChapter"
                            val frameStartVerse = Frame.getStartVerse(
                                chunkText,
                                sourceFormat
                            )
                            val frameEndVerse = Frame.getEndVerse(
                                chunkText,
                                sourceFormat
                            )
                            frameTitle += ":$frameStartVerse"

                            if (frameStartVerse != frameEndVerse) {
                                frameTitle += "-$frameEndVerse"
                            }

                            frameValidations.add(
                                Validation.InvalidFrame(
                                    title = frameTitle,
                                    titleLanguage = sourceLanguage,
                                    body = frameTranslation.body,
                                    bodyLanguage = targetLanguage,
                                    bodyFormat = frameTranslation.format,
                                    targetTranslationId = targetTranslationId,
                                    chapterId = chapterSlug,
                                    frameId = chunkSlug
                                )
                            )
                        }
                    }
                }
                if (lastValidChapterIndex == -1 && chapterIsValid) {
                    // start new valid range
                    lastValidChapterIndex = i
                } else if (!chapterIsValid || i == chapters.size - 1) {
                    // close valid range
                    if (lastValidChapterIndex > -1) {
                        var previousChapterIndex = i - 1
                        if (chapterIsValid) {
                            previousChapterIndex = i
                        }
                        if (lastValidChapterIndex < previousChapterIndex) {
                            // range
                            val previousChapterSlug = chapters[previousChapterIndex]
                            val lastValidChapterSlug = chapters[lastValidChapterIndex]
                            val lastChapter = StringUtilities.formatNumber(
                                lastValidChapterSlug
                            )
                            val prevChapter = StringUtilities.formatNumber(
                                previousChapterSlug
                            )
                            val chapterTitle = "$projectTitle $lastChapter-$prevChapter"

                            chapterValidations.add(
                                Validation.ValidFrame(
                                    chapterTitle,
                                    sourceLanguage,
                                    true
                                )
                            )
                        } else {
                            val lastValidChapter = chapters[lastValidChapterIndex]
                            val lastChapter = StringUtilities.formatNumber(lastValidChapter)
                            val chapterTitle = "$projectTitle $lastChapter"

                            chapterValidations.add(
                                Validation.ValidGroup(
                                    chapterTitle,
                                    sourceLanguage,
                                    false
                                )
                            )
                        }
                        lastValidChapterIndex = -1
                    }

                    // add invalid chapter
                    if (!chapterIsValid) {
                        var chapterTitle: String
                        chapterTitle = container.readChunk(chapterSlug, "title")
                        if (chapterTitle.isEmpty()) {
                            val formattedChapter = StringUtilities.formatNumber(chapterSlug)
                            chapterTitle = "$projectTitle $formattedChapter"
                        }
                        chapterTitle = context.getString(
                            R.string.has_warnings,
                            chapterTitle.trim()
                        )

                        chapterValidations.add(
                            Validation.InvalidGroup(
                                chapterTitle,
                                sourceLanguage
                            )
                        )

                        // add frame validations
                        chapterValidations.addAll(frameValidations)
                    }
                }
            }

            // close validations
            if (chapterValidations.size > 1) {
                validations.addAll(chapterValidations)
            } else {
                validations.add(
                    Validation.ValidGroup(
                        title = projectTitle,
                        titleLanguage = sourceLanguage,
                        isRange = true
                    )
                )
            }
        }

        return validations
    }

    /**
     * get the text from the source for title and add the chunk type as a tip
     * @param container
     * @param chapterSlug
     * @param chunkSlug
     * @return
     */
    private fun getChunkTitle(
        container: ResourceContainer,
        chapterSlug: String,
        chunkSlug: String,
        type: String
    ): String {
        val title = container.readChunk(chapterSlug, chunkSlug)
        return title.trim() + " - " + type
    }
}