package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.SettingsActivity
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class CreateRepository @Inject constructor(
    private val prefRepo: IPreferenceRepository,
    private val profile: Profile
) {
    fun execute(
        targetTranslation: TargetTranslation,
        progressListener: OnProgressListener? = null
    ): Boolean {
        progressListener?.onProgress(-1F, "Preparing location on server")

        val api = GogsAPI(
            prefRepo.getDefaultPref(
                SettingsActivity.KEY_PREF_GOGS_API,
                R.string.pref_default_gogs_api
            )
        )
        if (profile.gogsUser != null) {
            val templateRepo = Repository(targetTranslation.id, "", false)
            val repo = api.createRepo(templateRepo, profile.gogsUser)
            if (repo != null) {
                return true
            } else {
                val response = api.lastResponse
                Logger.w(
                    this.javaClass.name,
                    "Failed to create repository " + targetTranslation.id + ". Gogs responded with " + response.code + ": " + response.data,
                    response.exception
                )
            }
        }

        return false
    }
}