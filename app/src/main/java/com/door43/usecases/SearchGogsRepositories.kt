package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.gogsclient.GogsAPI
import org.unfoldingword.gogsclient.Repository
import javax.inject.Inject

class SearchGogsRepositories @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository
) {
    private val max = 100

    fun execute(
        uid: Int,
        query: String,
        limit: Int,
        progressListener: OnProgressListener? = null
    ): List<Repository> {
        progressListener?.onProgress(-1, max, "Searching for repositories")
        val repositories = arrayListOf<Repository>()

        val api = GogsAPI(
            prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_GOGS_API,
                context.getString(R.string.pref_default_gogs_api)
            )
        )
        val repos = api.searchRepos(query, uid, limit)

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