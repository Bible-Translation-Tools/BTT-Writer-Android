package com.door43.usecases

import com.door43.translationstudio.core.Translator
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

/**
 * Updates a target language in the library index and propagates the change
 * to all existing target translations that use this language.
 */
class UpdateTargetLanguage @Inject constructor(
    private val library: Door43Client,
    private val translator: Translator
) {
    fun execute(language: TargetLanguage): Boolean {
        val index = library.index
        val updated = if (index.isTempTargetLanguage(language.slug)) {
            index.addTempTargetLanguage(language)
        } else {
            index.addTargetLanguage(language)
        }
        if (!updated) {
            Logger.w(TAG, "Failed to update target language ${language.slug}")
            return false
        }

        for (translation in translator.targetTranslations) {
            if (translation.targetLanguage?.slug != language.slug) continue
            try {
                translation.changeTargetLanguage(language)
            } catch (e: Exception) {
                // keep going: one broken project must not block the others
                Logger.e(TAG, "Failed to update project ${translation.id}", e)
            }
        }
        return true
    }

    companion object {
        private const val TAG = "UpdateTargetLanguage"
    }
}
