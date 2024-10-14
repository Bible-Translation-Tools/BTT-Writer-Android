package com.door43.usecases

import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import javax.inject.Inject

class MergeTargetTranslation @Inject constructor(
    private val translator: Translator,
    private val backupRC: BackupRC
) {
    data class Result(
        val success: Boolean,
        val status: Status,
        val destinationTranslation: TargetTranslation,
        val sourceTranslation: TargetTranslation
    )

    fun execute(
        destinationTranslation: TargetTranslation,
        sourceTranslation: TargetTranslation,
        deleteSource: Boolean
    ): Result {
        var success = false
        var status: Status

        try {
            val mergeConflict = !destinationTranslation.merge(sourceTranslation.path) {
                // Try to backup and delete corrupt project
                try {
                    backupRC.backupTargetTranslation(sourceTranslation.path)
                    translator.deleteTargetTranslation(sourceTranslation.path)
                } catch (ex: java.lang.Exception) {
                    ex.printStackTrace()
                }
            }
            success = true
            status = Status.SUCCESS
            if (mergeConflict) {
                status = Status.MERGE_CONFLICTS
            }
        } catch (e: Exception) {
            e.printStackTrace()
            status = Status.MERGE_ERROR
        }

        if (deleteSource && success) {
            // delete original
            translator.deleteTargetTranslation(sourceTranslation.id)
            translator.clearTargetTranslationSettings(sourceTranslation.id)
        }

        return Result(success, status, destinationTranslation, sourceTranslation)
    }

    enum class Status {
        SUCCESS,
        MERGE_CONFLICTS,
        MERGE_ERROR
    }
}