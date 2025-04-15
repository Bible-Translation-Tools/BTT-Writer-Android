package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.User
import javax.inject.Inject

class SearchGogsUsers @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository
) {
    private val max = 100

    fun execute(
        userQuery: String,
        limit: Int,
        progressListener: OnProgressListener? = null
    ): List<User> {
        progressListener?.onProgress(-1, max, "Searching for users")

        val api = GogsAPI(
            prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_GOGS_API,
                context.getString(R.string.pref_default_gogs_api)
            ),
            context.getString(R.string.gogs_user_agent)
        )
        return api.searchUsers(userQuery, limit, null)
    }
}