package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import org.bibletranslationtools.gogsclient.GogsAPI
import org.bibletranslationtools.gogsclient.User

class SearchGogsUsers(
    private val context: Context,
    private val prefRepository: IPreferenceRepository
) {
    suspend fun execute(
        userQuery: String,
        limit: Int,
        onProgress: (Float, String?) -> Unit
    ): List<User> {
        onProgress(-1f, "Searching for users")

        val api = GogsAPI(
            apiUrl = prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_GOGS_API,
                context.getString(R.string.pref_default_gogs_api)
            ),
            userAgent = context.getString(R.string.gogs_user_agent)
        )
        return api.searchUsers(userQuery, limit, null)
    }
}