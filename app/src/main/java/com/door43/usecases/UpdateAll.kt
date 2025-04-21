package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import javax.inject.Inject

class UpdateAll @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client
) {
    data class Result(val success: Boolean)

    fun execute(updateCatalogs: Boolean, progressListener: OnProgressListener? = null): Result {
        var maxProgress = 100
        var success = false
        var overallSuccess = true

        progressListener?.onProgress(-1, maxProgress, "")

        try {
            val server = prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_MEDIA_SERVER,
                context.resources.getString(R.string.pref_default_media_server)
            )
            val rootApiUrl = server + prefRepository.getRootCatalogApi()
            library.updateSources(
                rootApiUrl
            ) { tag, max, complete ->
                maxProgress = max.toInt()
                progressListener?.onProgress(complete.toInt(), maxProgress, tag)
                true
            }
            success = true
        } catch (e: Exception) {
            e.printStackTrace()
        }

        overallSuccess = overallSuccess and success
        success = false

        progressListener?.onProgress(-1, 100, "")

        try {
            library.updateCatalogs(updateCatalogs) { tag, max, complete ->
                maxProgress = max.toInt()
                progressListener?.onProgress(complete.toInt(), maxProgress, tag)
                true
            }
            success = true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        overallSuccess = overallSuccess and success
        success = false

        progressListener?.onProgress(-1, 100, "")

        try {
            library.updateChunks { tag, max, complete ->
                maxProgress = max.toInt()
                progressListener?.onProgress(complete.toInt(), maxProgress, tag)
                true
            }
            success = true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        overallSuccess = overallSuccess and success

        return Result(overallSuccess)
    }
}