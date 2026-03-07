package com.door43.translationstudio.core

import org.unfoldingword.resourcecontainer.ResourceContainer

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

    val isProjectTitle: Boolean
        get() = chapterSlug == "front" && chunkSlug == "title"

    val isChapterReference: Boolean
        get() = chapterSlug != "front" && chapterSlug != "back" && chunkSlug == "reference"

    val isChapterTitle: Boolean
        get() = chapterSlug != "front" && chapterSlug != "back" && chunkSlug == "title"

    val isChapter: Boolean
        get() = isChapterReference || isChapterTitle

    val isChunk: Boolean
        get() = !isChapter && !isProjectTitle

    /**
     * Removes merge conflicts in text (uses first option)
     * @param text
     * @return
     */
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
