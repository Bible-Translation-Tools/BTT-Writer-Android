package com.door43.translationstudio.core

import com.door43.usecases.ParseMergeConflicts

/**
 * Created by blm on 11/22/16.
 */
object MergeConflictsHandler {
    private const val MERGE_CONFLICT_HEAD = "<<<<<<< HEAD.*\\n"
    private val mergeConflictPatternHead: Regex = MERGE_CONFLICT_HEAD.toRegex()

    /**
     * Split the merge conflict into a list of the options
     *
     * @param text
     * @return
     */
    fun getMergeConflictItems(text: String): List<CharSequence> {
        // Assuming ParseMergeConflicts is a Kotlin object or has a static execute method
        return ParseMergeConflicts.execute(text)
    }

    /**
     * Split the merge conflict into a list of the options
     *
     * @param text
     * @return
     */
    fun getMergeConflictItemsHead(text: String): CharSequence? {
        val items = getMergeConflictItems(text)
        return if (items.isEmpty()) null else items[0]
    }

    /**
     * Detects merge conflict tags
     *
     * @param text
     * @return
     */
    fun isMergeConflicted(text: CharSequence): Boolean {
        if (text.isNotEmpty()) {
            return mergeConflictPatternHead.containsMatchIn(text)
        }
        return false
    }

    /**
     * search for first merge conflict - We need this to double-check that there is a conflict in any chunks
     *
     * @param targetTranslationId
     * @return
     */
    fun isTranslationMergeConflicted(
        targetTranslationId: String?,
        translator: Translator
    ): Boolean {
        if (targetTranslationId == null) {
            return false
        }

        val targetTranslation = translator.getTargetTranslation(targetTranslationId) ?: return false

        val pt = targetTranslation.projectTranslation
        if (isMergeConflicted(pt.title)) {
            return true
        }

        val chapters = targetTranslation.chapterTranslations
        for (ct in chapters) {
            if (isMergeConflicted(ct.title)) {
                return true
            }

            if (isMergeConflicted(ct.reference)) {
                return true
            }

            val frames = targetTranslation.getFrameTranslations(
                ct.id,
                TranslationFormat.DEFAULT
            )
            for (frame in frames) {
                if (isMergeConflicted(frame.body)) {
                    return true
                }
            }
        }

        return false
    }
}