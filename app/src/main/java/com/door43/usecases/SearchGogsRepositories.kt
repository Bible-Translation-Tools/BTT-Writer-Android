package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.gogsclient.User
import javax.inject.Inject

class SearchGogsRepositories @Inject constructor() {
    private val max = 100

    fun execute(
        authUser: User,
        uid: Int,
        query: String,
        limit: Int,
        progressListener: OnProgressListener? = null
    ): List<Repository> {
        progressListener?.onProgress(-1, max, "Searching for repositories")
        val repositories = arrayListOf<Repository>()

        val api = GogsAPI(
            App.getUserString(
                SettingsActivity.KEY_PREF_GOGS_API,
                R.string.pref_default_gogs_api
            )
        )
        val repos = api.searchRepos(query, uid, limit)

        // fetch additional information about the repos (clone urls)
        for (repo in repos) {
            val extraRepo = api.getRepo(repo, authUser)
            if (extraRepo != null) {
                repositories.add(extraRepo)
            }
        }

        return repositories
    }
}