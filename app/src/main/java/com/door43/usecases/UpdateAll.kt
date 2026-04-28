package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import org.unfoldingword.door43client.Door43Client

class UpdateAll(
    private val context: Context,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client
) {
    data class Result(val success: Boolean)

    suspend fun execute(
        updateCatalogs: Boolean,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Result {
        var success = false
        var overallSuccess = true

        onProgress(-1f, "")

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

        overallSuccess = overallSuccess and success
        success = false

        onProgress(-1f, "")

        try {
            library.updateCatalogs(updateCatalogs) { value, message ->
                onProgress(value, message)
            }
            success = true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        overallSuccess = overallSuccess and success
        success = false

        onProgress(-1f, "")

        try {
            library.updateChunks { value, message ->
                onProgress(value, message)
            }
            success = true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }

        overallSuccess = overallSuccess and success

        return Result(overallSuccess)
    }
}