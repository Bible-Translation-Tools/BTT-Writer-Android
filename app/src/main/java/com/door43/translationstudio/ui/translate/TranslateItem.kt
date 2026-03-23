package com.door43.translationstudio.ui.translate

import androidx.compose.ui.text.AnnotatedString
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.FileHistory
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.ProjectTranslation
import com.door43.translationstudio.ui.translate.review.TargetMode
import com.door43.usecases.ParseMergeConflicts

interface Swipable {
    val sourceOnTop: Boolean
    fun selfCopy(sourceOnTop: Boolean = this.sourceOnTop): Swipable
}

abstract class TranslateItem {
    abstract val id: String
    abstract val chunk: Chunk
    abstract val sourceText: String
    abstract val targetText: String
    abstract val renderedSourceText: AnnotatedString
    abstract val renderedTargetText: AnnotatedString
    abstract val pt: ProjectTranslation
    abstract val ct: ChapterTranslation
    abstract val ft: FrameTranslation

    open val sourceTitle: String
        get() {
            return if (chunk.isProjectTitle) {
                ""
            } else if (chunk.isChapter) {
                chunk.source.project.name.trim()
            } else {
                // TODO: we should read the title from a cache instead of doing file io again
                var title = chunk.source.readChunk(chunk.chapterSlug, "title").trim()
                if (title.isEmpty()) {
                    title = try {
                        "${chunk.source.project.name.trim()} ${chunk.chapterSlug.toInt()}"
                    } catch (_: Exception) {
                        "${chunk.source.project.name.trim()} ${chunk.chapterSlug}"
                    }
                }
                val verseSpan = Frame.parseVerseTitle(sourceText, chunk.sourceTranslationFormat)
                title += if (verseSpan.isEmpty()) {
                    try {
                        ":${chunk.chunkSlug.toInt()}"
                    } catch (_: Exception) {
                        ":${chunk.chunkSlug}"
                    }
                } else ":$verseSpan"
                title
            }
        }

    open val targetTitle: String
        get() {
            if (chunk.isProjectTitle) {
                return removeConflicts(chunk.target.targetLanguage.name)
            } else if (chunk.isChapter) {
                val ptTitle = removeConflicts(pt.title).trim()
                return if (ptTitle.isNotEmpty()) {
                    ptTitle + " - " + chunk.target.targetLanguage.name
                } else {
                    removeConflicts(chunk.source.project.name).trim() + " - " + chunk.target.targetLanguage.name
                }
            } else {
                // use project title
                var title = removeConflicts(pt.title).trim()
                if (title.isEmpty()) {
                    title = removeConflicts(chunk.source.project.name).trim()
                }
                title += " " + chunk.chapterSlug.toInt()

                val verseSpan = Frame.parseVerseTitle(sourceText, chunk.sourceTranslationFormat)
                title += if (verseSpan.isEmpty()) {
                    ":" + chunk.chunkSlug.toInt()
                } else {
                    ":$verseSpan"
                }
                return title + " - " + chunk.target.targetLanguage.name
            }
        }

    val isComplete: Boolean
        get() = when (chunk.chapterSlug) {
            "front" -> {
                // project stuff
                if (chunk.chunkSlug == "title") {
                    pt.isTitleFinished
                } else false
            }
            "back" -> false
            else -> {
                // chapter stuff
                when (chunk.chunkSlug) {
                    "title" -> ct.titleFinished
                    "reference" -> ct.referenceFinished
                    else -> ft.finished
                }
            }
        }

    val hasMergeConflicts: Boolean
        get() = MergeConflictsHandler.isMergeConflicted(targetText)

    val mergeItems: List<CharSequence>
        get() = if (hasMergeConflicts) {
            ParseMergeConflicts.execute(targetText)
        } else emptyList()

    fun saveTranslation(text: String) {
        if (chunk.isProjectTitle) {
            chunk.target.applyProjectTitleTranslation(text)
        } else if (chunk.isChapterReference) {
            chunk.target.applyChapterReferenceTranslation(ct, text)
        } else if (chunk.isChapterTitle) {
            chunk.target.applyChapterTitleTranslation(ct, text)
        } else {
            chunk.target.applyFrameTranslation(ft, text)
        }
    }

    private fun removeConflicts(text: String): String {
        if (MergeConflictsHandler.isMergeConflicted(text)) {
            var unConflictedText = MergeConflictsHandler.getMergeConflictItemsHead(text)
            if (unConflictedText == null) {
                unConflictedText = ""
            }
            return unConflictedText.toString()
        }
        return text
    }
}

data class ReadItem(
    override val id: String,
    override val chunk: Chunk,
    override val sourceText: String,
    override val targetText: String,
    override val renderedSourceText: AnnotatedString,
    override val renderedTargetText: AnnotatedString,
    override val pt: ProjectTranslation,
    override val ct: ChapterTranslation,
    override val ft: FrameTranslation,
    override val sourceOnTop: Boolean
) : TranslateItem(), Swipable {

    override fun selfCopy(sourceOnTop: Boolean): Swipable {
        return copy(sourceOnTop = sourceOnTop)
    }

    override val sourceTitle: String
        get() {
            var title = chunk.source.readChunk(chunk.chapterSlug, "title")
                .trim()
            if (title.isEmpty()) {
                title = chunk.source.readChunk("front", "title")
                    .trim()
                if (chunk.chapterSlug != "front") {
                    title += " " + chunk.chapterSlug.toInt()
                }
            }
            return title
        }

    override val targetTitle: String
        get() {
            var title: String
            val chapterTranslation = chunk.target
                .getChapterTranslation(chunk.chapterSlug)
            title = chapterTranslation.title.trim()

            // if no target chapter title translation, fall back to source chapter title
            if (title.isEmpty() && sourceTitle.trim().isNotEmpty()) {
                title = sourceTitle.trim()
            }

            // if no chapter titles, fall back to project title, try translated title first
            if (title.isEmpty()) {
                val projTrans = chunk.target.projectTranslation
                if (projTrans.title.trim().isNotEmpty()) {
                    title = try {
                        "${projTrans.title.trim()} ${chunk.chapterSlug.toInt()}"
                    } catch (_: Exception) {
                        "${projTrans.title.trim()} ${chunk.chapterSlug}"
                    }
                }
            }

            // fall back to project source title
            if (title.isEmpty()) {
                title = chunk.source.readChunk("front", "title")
                    .trim()
                if (chunk.chapterSlug != "front") {
                    title += try {
                        " ${chunk.chapterSlug.toInt()}"
                    } catch (_: Exception) {
                        " ${chunk.chapterSlug}"
                    }
                }
            }

            return "$title - ${chunk.target.targetLanguage.name}"
        }
}

data class ChunkItem(
    override val id: String,
    override val chunk: Chunk,
    override val sourceText: String,
    override val targetText: String,
    override val renderedSourceText: AnnotatedString,
    override val renderedTargetText: AnnotatedString,
    override val pt: ProjectTranslation,
    override val ct: ChapterTranslation,
    override val ft: FrameTranslation,
    override val sourceOnTop: Boolean
) : TranslateItem(), Swipable {

    override fun selfCopy(sourceOnTop: Boolean): Swipable {
        return copy(sourceOnTop = sourceOnTop)
    }
}

data class ReviewItem(
    override val id: String,
    override val chunk: Chunk,
    override val sourceText: String,
    override val targetText: String,
    override val renderedSourceText: AnnotatedString,
    override val renderedTargetText: AnnotatedString,
    override val pt: ProjectTranslation,
    override val ct: ChapterTranslation,
    override val ft: FrameTranslation,
    val helps: Map<String, Any> = emptyMap(),
    val targetMode: TargetMode,
    val fileHistory: FileHistory? = null
) : TranslateItem()