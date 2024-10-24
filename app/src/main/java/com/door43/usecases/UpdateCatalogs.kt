package com.door43.usecases

import com.door43.OnProgressListener
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class UpdateCatalogs @Inject constructor(
    private val library: Door43Client
) {
    data class Result(val success: Boolean, val addedCount: Int)

    fun execute(message: String, progressListener: OnProgressListener? = null): Result {
        var addedCount = 0
        var success = false
        var maxProgress = 100

        progressListener?.onProgress(-1, maxProgress, message)

        var targetLanguages = library.index.targetLanguages
        val initialLanguages = HashSet<String>()
        for (l in targetLanguages) {
            initialLanguages.add(l.slug)
        }

        Logger.i(this.javaClass.simpleName, "Initial target languages count: " + targetLanguages.size)
        Logger.i(
            this.javaClass.simpleName,
            "Unigue target languages slug count: " + initialLanguages.size
        )

        try {
            library.updateCatalogs { tag, max, complete ->
                maxProgress = max.toInt()
                val details = "$message $tag"
                progressListener?.onProgress(complete.toInt(), max.toInt(), details)
                true
            }
            success = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (success) {
            targetLanguages = library.index.targetLanguages
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