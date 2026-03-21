package com.door43.translationstudio.core

import android.os.Handler
import android.os.Looper
import com.door43.usecases.ParseMergeConflicts
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.TaskManager

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

    /**
     * check the whole project to see if there is actually a chunk conflict
     * @param targetTranslationId
     * @param listener
     */
    fun backgroundTestForConflictedChunks(
        targetTranslationId: String,
        translator: Translator,
        listener: OnMergeConflictListener
    ) {
        val task = object : ManagedTask() {
            override fun start() {
                try {
                    if (interrupted()) return
                    val conflicted = isTranslationMergeConflicted(targetTranslationId, translator)
                    result = conflicted
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        task.addOnFinishedListener { task1 ->
            TaskManager.clearTask(task1)
            val conflicted = (task1.result as? Boolean) ?: false

            if (!task1.isCanceled) {
                val hand = Handler(Looper.getMainLooper())
                hand.post {
                    if (conflicted) {
                        listener.onMergeConflict(targetTranslationId)
                    } else {
                        listener.onNoMergeConflict(targetTranslationId)
                    }
                }
            }
        }
        TaskManager.addTask(task)
    }

    interface OnMergeConflictListener {
        fun onNoMergeConflict(targetTranslationId: String)
        fun onMergeConflict(targetTranslationId: String)
    }
}