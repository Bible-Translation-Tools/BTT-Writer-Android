package com.door43.usecases

import android.content.Context
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Util
import com.door43.translationstudio.ui.publish.ValidationItem
import com.door43.util.StringUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONException
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class ValidateProject @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: Door43Client,
    private val translator: Translator
) {
    fun execute(targetTranslationId: String, sourceTranslationId: String): List<ValidationItem> {
        val hasWarnings = context.getString(R.string.has_warnings)
        val titleStr = context.getString(R.string.title)
        val referenceStr = context.getString(R.string.reference)

        val validations = arrayListOf<ValidationItem>()

        translator.getTargetTranslation(targetTranslationId)?.let { targetTranslation ->
            val targetLanguage = library.index().getTargetLanguage(
                targetTranslation.targetLanguageId
            )

            val container = try {
                library.open(sourceTranslationId)
            } catch (e: Exception) {
                Logger.e(
                    "ValidationTask",
                    "Failed to load resource container",
                    e
                )
                return listOf()
            }

            val format = try {
                TranslationFormat.parse(container.info.getString("content_mime_type"))
            } catch (e: JSONException) {
                Logger.e(
                    "ValidationTask",
                    "Failed to read the translation format from the container",
                    e
                )
                return listOf()
            }

            val projectTitle = container.readChunk("front", "title")
            val sourceLanguage = library.index().getSourceLanguage(container.language.slug)
            val chapters = container.chapters()

            // validate chapters
            var lastValidChapterIndex = -1
            val chapterValidations = arrayListOf<ValidationItem>()

            val projectTranslation = targetTranslation.projectTranslation

            sortArrayNumerically(chapters)
            for (i in chapters.indices) {
                val chapterSlug = chapters[i]
                val chunks = container.chunks(chapterSlug)
                sortArrayNumerically(chunks)

                // validate frames
                var lastValidFrameIndex = -1
                var chapterIsValid = true
                val frameValidations = arrayListOf<ValidationItem>()

                val chapterTranslation = targetTranslation.getChapterTranslation(chapterSlug)
                if (MergeConflictsHandler.isMergeConflicted(chapterTranslation.title) ||
                    chunks.contains("title") &&
                    !chapterTranslation.isTitleFinished
                ) {
                    chapterIsValid = false
                    frameValidations.add(
                        ValidationItem.generateInvalidFrame(
                            getChunkTitle(
                                container,
                                chapterSlug,
                                "title",
                                titleStr
                            ),
                            sourceLanguage,
                            chapterTranslation.title,
                            targetLanguage,
                            TranslationFormat.DEFAULT,
                            targetTranslationId,
                            chapterSlug,
                            "00"
                        )
                    )
                }

                if (MergeConflictsHandler.isMergeConflicted(chapterTranslation.reference) ||
                    chunks.contains("reference") &&
                    !chapterTranslation.isReferenceFinished
                ) {
                    chapterIsValid = false
                    frameValidations.add(
                        ValidationItem.generateInvalidFrame(
                            getChunkTitle(
                                container,
                                chapterSlug,
                                "reference",
                                referenceStr
                            ),
                            sourceLanguage,
                            chapterTranslation.reference,
                            targetLanguage,
                            TranslationFormat.DEFAULT,
                            targetTranslationId,
                            chapterSlug,
                            "00"
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
                        format
                    )
                    val chunkText = container.readChunk(chapterSlug, chunkSlug)
                    // TODO: also validate the checking questions
                    if (lastValidFrameIndex == -1 &&
                        (frameTranslation.isFinished || chunkText.isEmpty())
                    ) {
                        // start new valid range
                        lastValidFrameIndex = j
                    } else if (MergeConflictsHandler.isMergeConflicted(frameTranslation.body) ||
                        !(frameTranslation.isFinished || chunkText.isEmpty()) ||
                        (frameTranslation.isFinished || chunkText.isEmpty()) &&
                        (j == chunks.size - 1)
                    ) {
                        // close valid range
                        if (lastValidFrameIndex > -1) {
                            var previousFrameIndex = j - 1
                            if (frameTranslation.isFinished || chunkText.isEmpty()) {
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
                                val formattedChapter = StringUtilities.formatNumber(chapterSlug)
                                var frameTitle = "$projectTitle $formattedChapter"
                                val frameStartVerse = Frame.getStartVerse(lastValidText, format)
                                val frameEndVerse = Frame.getEndVerse(previousFrame, format)
                                frameTitle += ":$frameStartVerse-$frameEndVerse"

                                frameValidations.add(
                                    ValidationItem.generateValidFrame(
                                        frameTitle,
                                        sourceLanguage,
                                        true
                                    )
                                )
                            } else {
                                val lastValidFrame = chunks[lastValidFrameIndex]
                                val lastValidText = container.readChunk(
                                    chapterSlug,
                                    chunks[lastValidFrameIndex]
                                )
                                val formattedChapter = StringUtilities.formatNumber(chapterSlug)
                                var frameTitle = "$projectTitle $formattedChapter"
                                val frameStartVerse = Frame.getStartVerse(lastValidText, format)
                                val frameEndVerse = Frame.getEndVerse(lastValidText, format)
                                frameTitle += ":$frameStartVerse"

                                if (frameStartVerse != frameEndVerse) {
                                    frameTitle += "-$frameEndVerse"
                                }
                                frameValidations.add(
                                    ValidationItem.generateValidFrame(
                                        frameTitle,
                                        sourceLanguage,
                                        false
                                    )
                                )
                            }
                            lastValidFrameIndex = -1
                        }

                        // add invalid frame
                        if (!(frameTranslation.isFinished || chunkText.isEmpty())) {
                            chapterIsValid = false
                            val formattedChapter = StringUtilities.formatNumber(chapterSlug)
                            var frameTitle = "$projectTitle $formattedChapter"
                            val frameStartVerse = Frame.getStartVerse(chunkText, format)
                            val frameEndVerse = Frame.getEndVerse(chunkText, format)
                            frameTitle += ":$frameStartVerse"

                            if (frameStartVerse != frameEndVerse) {
                                frameTitle += "-$frameEndVerse"
                            }

                            frameValidations.add(
                                ValidationItem.generateInvalidFrame(
                                    frameTitle,
                                    sourceLanguage,
                                    frameTranslation.body,
                                    targetLanguage,
                                    frameTranslation.format,
                                    targetTranslationId,
                                    chapterSlug,
                                    chunkSlug
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
                            val lastChapter = StringUtilities.formatNumber(lastValidChapterSlug)
                            val prevChapter = StringUtilities.formatNumber(previousChapterSlug)
                            val chapterTitle = "$projectTitle $lastChapter-$prevChapter"

                            chapterValidations.add(
                                ValidationItem.generateValidGroup(
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
                                ValidationItem.generateValidGroup(
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
                        chapterTitle = String.format(hasWarnings, chapterTitle.trim())

                        chapterValidations.add(
                            ValidationItem.generateInvalidGroup(
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
                    ValidationItem.generateValidGroup(projectTitle, sourceLanguage, true)
                )
            }
        }

        return validations
    }

    /**
     * sort the array numerically
     * @param array An array to sort
     */
    private fun sortArrayNumerically(array: Array<String>) {
        // sort frames
        array.sortWith { o1, o2 ->
            // do numeric sort
            val lhInt = getIdOrder(o1)
            val rhInt = getIdOrder(o2)
            lhInt.compareTo(rhInt)
        }
    }

    /**
     *
     * @param id
     * @return
     */
    private fun getIdOrder(id: String): Int {
        // if not numeric, then will move to top of list and leave order unchanged
        return Util.strToInt(id, -1)
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