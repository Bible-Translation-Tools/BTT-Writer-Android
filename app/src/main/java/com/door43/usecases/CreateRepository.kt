package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class CreateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepo: IPreferenceRepository,
    private val profile: Profile
) {
    private val max = 100

    fun execute(
        targetTranslation: TargetTranslation,
        progressListener: OnProgressListener? = null
    ): Boolean {
        progressListener?.onProgress(-1, max,"Preparing location on server")

        val api = GogsAPI(
            prefRepo.getDefaultPref(
                SettingsActivity.KEY_PREF_GOGS_API,
                context.resources.getString(R.string.pref_default_gogs_api)
            ),
            context.getString(R.string.gogs_user_agent)
        )
        if (profile.gogsUser != null) {
            val templateRepo = Repository(targetTranslation.id, "", false)
            val repo = api.createRepo(templateRepo, profile.gogsUser)
            if (repo != null) {
                return true
            } else {
                val response = api.lastResponse
                if (response.code == 409) {
                    // Repository already exists
                    return true
                }
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