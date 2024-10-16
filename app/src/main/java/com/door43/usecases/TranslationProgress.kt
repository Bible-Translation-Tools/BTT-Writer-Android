package com.door43.usecases

import com.door43.translationstudio.App
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import javax.inject.Inject

class TranslationProgress @Inject constructor(
    private val library: Door43Client,
    private val translator: Translator
) {
    fun execute(targetTranslation: TargetTranslation): Double {
        var progress: Double

        // find matching source
        val sourceTranslation = getSourceTranslation(targetTranslation)
            ?: return 0.0

        // load source
        val container = try {
            library.open(sourceTranslation.resourceContainerSlug)
        } catch (e: Exception) {
            e.printStackTrace()
            return 0.0
        }

        // count chunks
        val numSourceChunks = countChunks(container)
        val numTargetChunks = countChunks(targetTranslation)

        progress = if (numSourceChunks == 0) {
            0.0
        } else {
            numTargetChunks.toDouble() / numSourceChunks.toDouble()
        }

        // correct invalid values
        if (progress > 1) progress = 1.0

        return progress
    }

    /**
     * Counts the number of chunks in a target translation.
     * TODO: once target translations become resource containers we can use the method below instead.
     * @param targetTranslation the target translation to count
     * @return the number of completed chunks in the target translation
     */
    private fun countChunks(targetTranslation: TargetTranslation): Int {
        return targetTranslation.numFinished()
    }

    /**
     * Counts how many chunks are in a resource container
     * @param container the resource container to be counted
     * @return the number of chunks in the resource container
     */
    private fun countChunks(container: ResourceContainer): Int {
        var count = 0
        for (chapterSlug in container.chapters()) {
            count += container.chunks(chapterSlug).size
        }
        return count
    }

    /**
     * Returns a single source translation that corresponds to the target translation
     * @param targetTranslation the target translation to match against
     * @return a matching source translation or null
     */
    private fun getSourceTranslation(
        targetTranslation: TargetTranslation
    ): Translation? {
        val selectedSourceId = translator.getSelectedSourceTranslationId(targetTranslation.id)

        return library.index.findTranslations(
            null,
            targetTranslation.projectId,
            null,
            "book",
            null,
            App.MIN_CHECKING_LEVEL,
            -1
        ).firstOrNull {
            it.resourceContainerSlug == selectedSourceId || it.language.slug == "en"
        }
    }
}