package com.door43.usecases

import com.door43.OnProgressListener
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger

class UpdateCatalogs(
    private val library: Door43Client
) {
    data class Result(val success: Boolean, val addedCount: Int)

    suspend fun execute(
        updateCatalogs: Boolean,
        progressListener: OnProgressListener? = null
    ): Result {
        var addedCount = 0
        var success = false

        var targetLanguages = library.index.getTargetLanguages()
        val initialLanguages = HashSet<String>()
        for (l in targetLanguages) {
            initialLanguages.add(l.slug)
        }

        Logger.i(this.javaClass.simpleName, "Initial target languages count: " + targetLanguages.size)
        Logger.i(
            this.javaClass.simpleName,
            "Unique target languages slug count: " + initialLanguages.size
        )

        try {
            library.updateCatalogs(updateCatalogs) { tag, max, complete ->
                val progress = complete / max.toFloat()
                progressListener?.onProgress(progress, tag)
                true
            }
            success = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (success) {
            targetLanguages = library.index.getTargetLanguages()
            Logger.i(
                this.javaClass.simpleName,
                "Final target languages count: " + targetLanguages.size
            )
            for (l in targetLanguages) {
                if (!initialLanguages.contains(l.slug)) {
                    addedCount++
                    Logger.i(
                        this.javaClass.simpleName,
                        "New target languages " + addedCount + ": " + l.slug
                    )
                }
            }
        }

        return Result(success, addedCount)
    }
}