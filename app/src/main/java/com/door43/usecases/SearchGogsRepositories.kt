package com.door43.usecases

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import org.bibletranslationtools.gogsclient.GogsAPI
import org.bibletranslationtools.gogsclient.Repository

class SearchGogsRepositories(
    private val context: Context,
    private val prefRepository: IPreferenceRepository
) {
    suspend fun execute(
        uid: Int,
        query: String,
        limit: Int,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): List<Repository> {
        onProgress(-1f, "Searching for repositories")
        val repositories = arrayListOf<Repository>()

        val repoQuery = query.ifEmpty { "_" }

        val api = GogsAPI(
            apiUrl = prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_GOGS_API,
                context.getString(R.string.pref_default_gogs_api)
            ),
            userAgent = context.getString(R.string.gogs_user_agent)
        )
        val repos = api.searchRepos(repoQuery, uid, limit)

        // fetch additional information about the repos (clone urls)
        for (repo in repos) {
            val extraRepo = api.getRepo(repo, null)
            if (extraRepo != null) {
                repositories.add(extraRepo)
            }
        }

        return repositories
    }
}