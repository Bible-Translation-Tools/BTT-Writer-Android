package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.Platform
import com.door43.translationstudio.R
import org.unfoldingword.door43client.Door43Client

class UpdateSource(
    private val context: Context,
    private val library: Door43Client,
    private val prefRepository: IPreferenceRepository
) {
    data class Result(
        val success: Boolean,
        val updatedCount: Int,
        val addedCount: Int
    )

    suspend fun execute(onProgress: (Float, String?) -> Unit = {_,_->}): Result {
        var updatedCount = 0
        var addedCount = 0
        var success = false
        var count = 0

        var availableTranslationsAll = library.index.findTranslations(
            null,
            null,
            null,
            "book",
            null,
            Platform.MIN_CHECKING_LEVEL,
            -1
        )
        val previouslyUpdated = HashMap<String, Int>()
        val total = availableTranslationsAll.size

        for (t in availableTranslationsAll) {
            if (++count % 16 == 0) {
                val progress = count / total.toFloat()
                onProgress(progress, null)
            }

            val id = t.resourceContainerSlug
            val lastModifiedOnServer = library.getResourceContainerLastModified(
                t.language.slug,
                t.project.slug,
                t.resource.slug
            )
            previouslyUpdated[id] = lastModifiedOnServer
        }

        onProgress(-1f, null)

        try {
            val server = prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_MEDIA_SERVER,
                context.resources.getString(R.string.pref_default_media_server)
            )
            val rootApiUrl = server + prefRepository.getRootCatalogApi()
            library.updateSources(rootApiUrl) { value, message ->
                onProgress(value, message)
            }
            success = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if(success) { // check for changes
            onProgress(-1f, null)

            availableTranslationsAll = library.index.findTranslations(
                null,
                null,
                null,
                "book",
                null,
                Platform.MIN_CHECKING_LEVEL,
                -1
            )

            val total = availableTranslationsAll.size
            count = 0

            for (t in availableTranslationsAll) {
                if (++count % 16 == 0) {
                    val progress = count / total.toFloat()
                    onProgress(progress, null)
                }

                val id = t.resourceContainerSlug
                if (previouslyUpdated.containsKey(id)) {
                    try {
                        val lastModifiedOnServer = library.getResourceContainerLastModified(
                            t.language.slug,
                            t.project.slug,
                            t.resource.slug
                        )
                        val previousUpdate = previouslyUpdated[id]!!
                        if (lastModifiedOnServer > previousUpdate) {
                            updatedCount++ // update times have changed
                        }
                    } catch (e: java.lang.Exception) {
                        e.printStackTrace()
                    }
                } else {
                    addedCount++ // new entry
                }
            }
        }

        return Result(success, updatedCount, addedCount)
    }
}