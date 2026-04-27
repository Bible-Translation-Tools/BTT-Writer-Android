package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import org.bibletranslationtools.gogsclient.GogsAPI
import org.bibletranslationtools.gogsclient.Repository
import org.bibletranslationtools.logger.Logger

class CreateRepository(
    private val context: Context,
    private val prefRepo: IPreferenceRepository,
    private val profile: Profile
) {
    suspend fun execute(
        targetTranslation: TargetTranslation,
        onProgress: (Float, String?) -> Unit
    ): Boolean {
        onProgress(-1f, "Preparing location on server")

        val api = GogsAPI(
            apiUrl = prefRepo.getDefaultPref(
                IPreferenceRepository.KEY_PREF_GOGS_API,
                context.resources.getString(R.string.pref_default_gogs_api)
            ),
            userAgent = context.getString(R.string.gogs_user_agent)
        )
        profile.gogsUser?.let { user ->
            val templateRepo = Repository(targetTranslation.id)
            val repo = api.createRepo(templateRepo, user)
            if (repo != null) {
                return true
            } else {
                val response = api.getLastResponse()
                if (response?.code == 409) {
                    // Repository already exists
                    return true
                }
                Logger.w(
                    this.javaClass.name,
                    "Failed to create repository " + targetTranslation.id + ". Gogs responded with " + response?.code + ": " + response?.message
                )
            }
        }

        return false
    }
}