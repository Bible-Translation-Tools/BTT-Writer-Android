package com.door43.usecases

import org.bibletranslationtools.gogsclient.Repository

class AdvancedGogsRepoSearch(
    private val searchGogsUsers: SearchGogsUsers,
    private val searchGogsRepositories: SearchGogsRepositories
) {
    suspend fun execute(
        userQuery: String,
        repoQuery: String,
        limit: Int,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): List<Repository> {
        val repositories = arrayListOf<Repository>()

        onProgress(-1f, "Searching for repositories")

        val repoNameQuery = repoQuery.ifEmpty { "_" }

        // user search or user and repo search
        if (userQuery.isNotEmpty()) {
            // start by searching user
            val users = searchGogsUsers.execute(userQuery, limit, onProgress)
            for (user in users) {
                // search by repo
                repositories.addAll(
                    searchGogsRepositories.execute(user.id, repoNameQuery, limit, onProgress)
                )
            }
        } else {
            // just search repos
            repositories.addAll(
                searchGogsRepositories.execute(0, repoNameQuery, limit, onProgress)
            )
        }

        return repositories
    }
}