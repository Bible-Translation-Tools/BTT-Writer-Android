package com.door43.translationstudio.ui.translate

import androidx.compose.ui.text.AnnotatedString
import com.door43.translationstudio.core.ChapterTranslation
import com.door43.translationstudio.core.Chunk
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.ProjectTranslation

data class ChunkMeta(
    val id: String,
    val chunk: Chunk,
    val sourceText: String,
    val targetText: String,
    val renderedSourceText: AnnotatedString,
    val renderedTargetText: AnnotatedString,
    val pt: ProjectTranslation,
    val ct: ChapterTranslation,
    val ft: FrameTranslation
)

sealed class ChunkItem(
    val meta: ChunkMeta
) {
    open val sourceTitle: String
        get() {
            return if (isProjectTitle) {
                ""
            } else if (isChapter) {
                meta.chunk.source.project.name.trim()
            } else {
                // TODO: we should read the title from a cache instead of doing file io again
                var title = meta.chunk.source.readChunk(meta.chunk.chapterSlug, "title").trim()
                if (title.isEmpty()) {
                    title = try {
                        "${meta.chunk.source.project.name.trim()} ${meta.chunk.chapterSlug.toInt()}"
                    } catch (_: Exception) {
                        "${meta.chunk.source.project.name.trim()} ${meta.chunk.chapterSlug}"
                    }
                }
                val verseSpan = Frame.parseVerseTitle(meta.sourceText, meta.chunk.sourceTranslationFormat)
                title += if (verseSpan.isEmpty()) {
                    try {
                        ":${meta.chunk.chunkSlug.toInt()}"
                    } catch (_: Exception) {
                        ":${meta.chunk.chunkSlug}"
                    }
                } else ":$verseSpan"
                title
            }
        }

    open val targetTitle: String
        get() {
            if (isProjectTitle) {
                return removeConflicts(meta.chunk.target.targetLanguage.name)
            } else if (isChapter) {
                val ptTitle = removeConflicts(meta.pt.title).trim()
                return if (ptTitle.isNotEmpty()) {
                    ptTitle + " - " + meta.chunk.target.targetLanguage.name
                } else {
                    removeConflicts(meta.chunk.source.project.name).trim() + " - " + meta.chunk.target.targetLanguage.name
                }
            } else {
                // use project title
                var title = removeConflicts(meta.pt.title).trim()
                if (title.isEmpty()) {
                    title = removeConflicts(meta.chunk.source.project.name).trim()
                }
                title += " " + meta.chunk.chapterSlug.toInt()

                val verseSpan = Frame.parseVerseTitle(meta.sourceText, meta.chunk.sourceTranslationFormat)
                title += if (verseSpan.isEmpty()) {
                    ":" + meta.chunk.chunkSlug.toInt()
                } else {
                    ":$verseSpan"
                }
                return title + " - " + meta.chunk.target.targetLanguage.name
            }
        }

    val isChunk: Boolean
        get() = !isChapter && !isProjectTitle

    val isChapter: Boolean
        get() = isChapterReference || isChapterTitle

    val isProjectTitle: Boolean
        get() = meta.chunk.chapterSlug == "front" && meta.chunk.chunkSlug == "title"

    val isChapterTitle: Boolean
        get() = meta.chunk.chapterSlug != "front" && meta.chunk.chapterSlug != "back" && meta.chunk.chunkSlug == "title"

    val isChapterReference: Boolean
        get() = meta.chunk.chapterSlug != "front" && meta.chunk.chapterSlug != "back" && meta.chunk.chunkSlug == "reference"

    fun saveTranslation(text: String) {
        if (isProjectTitle) {
            meta.chunk.target.applyProjectTitleTranslation(text)
        } else if (isChapterReference) {
            meta.chunk.target.applyChapterReferenceTranslation(meta.ct, text)
        } else if (isChapterTitle) {
            meta.chunk.target.applyChapterTitleTranslation(meta.ct, text)
        } else {
            meta.chunk.target.applyFrameTranslation(meta.ft, text)
        }
    }

    data class ReadMode(
        private val sharedMeta: ChunkMeta
    ) : ChunkItem(sharedMeta) {
        override val sourceTitle: String
            get() {
                var title = meta.chunk.source.readChunk(meta.chunk.chapterSlug, "title")
                    .trim()
                if (title.isEmpty()) {
                    title = meta.chunk.source.readChunk("front", "title")
                        .trim()
                    if (meta.chunk.chapterSlug != "front") {
                        title += " " + meta.chunk.chapterSlug.toInt()
                    }
                }
                return title
            }

        override val targetTitle: String
            get() {
                var title: String
                val chapterTranslation = meta.chunk.target
                    .getChapterTranslation(meta.chunk.chapterSlug)
                title = chapterTranslation.title.trim()

                // if no target chapter title translation, fall back to source chapter title
                if (title.isEmpty() && sourceTitle.trim().isNotEmpty()) {
                    title = sourceTitle.trim()
                }

                // if no chapter titles, fall back to project title, try translated title first
                if (title.isEmpty()) {
                    val projTrans = meta.chunk.target.projectTranslation
                    if (projTrans.title.trim().isNotEmpty()) {
                        title = try {
                            "${projTrans.title.trim()} ${meta.chunk.chapterSlug.toInt()}"
                        } catch (_: Exception) {
                            "${projTrans.title.trim()} ${meta.chunk.chapterSlug}"
                        }
                    }
                }

                // fall back to project source title
                if (title.isEmpty()) {
                    title = meta.chunk.source.readChunk("front", "title")
                        .trim()
                    if (meta.chunk.chapterSlug != "front") {
                        title += try {
                            " ${meta.chunk.chapterSlug.toInt()}"
                        } catch (e: Exception) {
                            " ${meta.chunk.chapterSlug}"
                        }
                    }
                }

                return "$title - ${meta.chunk.target.targetLanguage.name}"
            }
    }

    data class ChunkMode(
        private val sharedMeta: ChunkMeta
    ) : ChunkItem(sharedMeta)

    data class ReviewMode(
        private val sharedMeta: ChunkMeta
    ) : ChunkItem(sharedMeta)
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