package com.door43.translationstudio.core

import org.unfoldingword.resourcecontainer.ResourceContainer

typealias ChunkConfig = Map<String, List<String>>

data class Chunk(
    val chapterSlug: String,
    val chunkSlug: String,
    val source: ResourceContainer,
    val target: TargetTranslation
) {
    val sourceTranslationFormat: TranslationFormat
        get() = TranslationFormat.parse(source.contentMimeType)

    val targetTranslationFormat: TranslationFormat
        get() = target.format

    val isChunk: Boolean
        get() = !isChapter && !isProjectTitle

    val isChapter: Boolean
        get() = isChapterReference || isChapterTitle

    val isProjectTitle: Boolean
        get() = chapterSlug == "front" && chunkSlug == "title"

    val isChapterTitle: Boolean
        get() = chapterSlug != "front" && chapterSlug != "back" && chunkSlug == "title"

    val isChapterReference: Boolean
        get() = chapterSlug != "front" && chapterSlug != "back" && chunkSlug == "reference"

    val config: ChunkConfig
        get() = ((source.config?.get("content") as? Map<*, *>)
            ?.get(chapterSlug) as? Map<*, *>)
            ?.get(chunkSlug) as? ChunkConfig
            ?: emptyMap()

    fun reopen(): Boolean {
        return if (isProjectTitle) {
            target.openProjectTitle()
        } else if (isChapterTitle) {
            target.reopenChapterTitle(chapterSlug)
        } else if (isChapterReference) {
            target.reopenChapterReference(chapterSlug)
        } else {
            target.reopenFrame(chapterSlug, chunkSlug)
        }
    }

    fun close(): Boolean {
        return if (isProjectTitle) {
            target.closeProjectTitle()
        } else if (isChapterTitle) {
            target.finishChapterTitle(chapterSlug)
        } else if (isChapterReference) {
            target.finishChapterReference(chapterSlug)
        } else {
            target.finishFrame(chapterSlug, chunkSlug)
        }
    }
}
