package com.door43.usecases

import com.door43.OnProgressListener
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.gogsclient.User
import javax.inject.Inject

class AdvancedGogsRepoSearch @Inject constructor(
    private val submitNewLanguageRequests: SubmitNewLanguageRequests,
    private val searchGogsUsers: SearchGogsUsers,
    private val searchGogsRepositories: SearchGogsRepositories
) {
    private val max = 100

    fun execute(
        authUser: User,
        userQuery: String,
        repoQuery: String,
        limit: Int,
        progressListener: OnProgressListener? = null
    ): List<Repository> {
        val repositories = arrayListOf<Repository>()

        // submit new language requests
        submitNewLanguageRequests.execute(progressListener)

        progressListener?.onProgress(-1, max, "Searching for repositories")

        val repoNameQuery = repoQuery.ifEmpty { "_" }

        // user search or user and repo search
        if (userQuery.isNotEmpty()) {
            // start by searching user
            val users = searchGogsUsers.execute(authUser, userQuery, limit, progressListener)
            for (user in users) {
                // search by repo
                repositories.addAll(
                    searchGogsRepositories.execute(authUser, user.id, repoNameQuery, limit)
                )
            }
        } else {
            // just search repos
            repositories.addAll(
                searchGogsRepositories.execute(authUser, 0, repoNameQuery, limit, progressListener)
            )
        }

        return repositories
    }
}