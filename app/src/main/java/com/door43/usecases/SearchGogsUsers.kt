package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.User
import javax.inject.Inject

class SearchGogsUsers @Inject constructor() {
    private val max = 100

    fun execute(
        authUser: User,
        userQuery: String,
        limit: Int,
        progressListener: OnProgressListener? = null
    ): List<User> {
        progressListener?.onProgress(-1, max, "Searching for users")

        val api = GogsAPI(
            App.getUserString(
                SettingsActivity.KEY_PREF_GOGS_API,
                R.string.pref_default_gogs_api
            )
        )
        return api.searchUsers(userQuery, limit, authUser)
    }
}