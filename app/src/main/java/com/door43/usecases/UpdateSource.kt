package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

class UpdateSource @Inject constructor(
    @ApplicationContext val context: Context,
    private val library: Door43Client,
    private val prefRepository: IPreferenceRepository
) {
    data class Result(
        val success: Boolean,
        val updatedCount: Int,
        val addedCount: Int
    )

    fun execute(message: String, progressListener: OnProgressListener? = null): Result {
        var updatedCount = 0
        var addedCount = 0
        var success = false
        var count = 0
        var maxProgress = 100

        progressListener?.onProgress(-1, maxProgress, message)

        var availableTranslationsAll = library.index.findTranslations(
            null,
            null,
            null,
            "book",
            null,
            App.MIN_CHECKING_LEVEL,
            -1
        )
        val previouslyUpdated = HashMap<String, Int>()
        maxProgress = availableTranslationsAll.size

        for (t in availableTranslationsAll) {
            if (++count % 16 == 0) {
                progressListener?.onProgress(count, maxProgress, message)
            }

            val id = t.resourceContainerSlug
            val lastModifiedOnServer = library.getResourceContainerLastModified(
                t.language.slug,
                t.project.slug,
                t.resource.slug
            )
            previouslyUpdated[id] = lastModifiedOnServer
        }

        progressListener?.onProgress(-1, 100, message)

        try {
            val server = prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_MEDIA_SERVER,
                context.resources.getString(R.string.pref_default_media_server)
            )
            val rootApiUrl = server + prefRepository.getRootCatalogApi()
            library.updateSources(rootApiUrl) { tag, max, complete ->
                maxProgress = max.toInt()
                val details = "$message $tag"
                progressListener?.onProgress(complete.toInt(), max.toInt(), details)
                true
            }
            success = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if(success) { // check for changes
            progressListener?.onProgress(-1, 100, message)

            availableTranslationsAll = library.index.findTranslations(
                null,
                null,
                null,
                "book",
                null,
                App.MIN_CHECKING_LEVEL,
                -1
            )

            maxProgress = availableTranslationsAll.size
            count = 0

            for (t in availableTranslationsAll) {
                if (++count % 16 == 0) {
                    progressListener?.onProgress(count, maxProgress, message)
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