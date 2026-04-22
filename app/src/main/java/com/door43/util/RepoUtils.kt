package com.door43.util

import com.door43.translationstudio.core.TargetTranslation
import org.bibletranslationtools.logger.Logger
import java.io.File

object RepoUtils {
    /**
     * Attempts to recover from a corrupt git history.
     * @param targetTranslation Target translation to recover
     * @return Result of recovery
     */
    fun recover(targetTranslation: TargetTranslation?): Boolean {
        if (targetTranslation == null) return false
        Logger.w(this::javaClass.name, "Recovering repository for " + targetTranslation.id)
        try {
            val gitDir = File(targetTranslation.path, ".git")
            if (FileUtilities.deleteQuietly(gitDir)) {
                targetTranslation.commitSync(".", false)
                Logger.i(this::javaClass.name, "History repaired for " + targetTranslation.id)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}