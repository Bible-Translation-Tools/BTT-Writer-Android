package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class GetRepository @Inject constructor(
    private val createRepository: CreateRepository,
    private val searchRepository: SearchGogsRepositories,
    private val profile: Profile
) {
    private val max = 100

    fun execute(
        translation: TargetTranslation,
        progressListener: OnProgressListener? = null
    ): Repository? {
        progressListener?.onProgress(-1, max, "Getting repository")

        if (profile.gogsUser == null) {
            Logger.e(this.javaClass.name, "Gogs user is not set")
            return null
        }

        // Create repository
        // If it exists, will do nothing
        createRepository.execute(translation, progressListener)

        // Search for repository
        // There could be more than one repo, which name can contain requested repo name.
        // For example: en_ulb_mat_txt, custom_en_ulb_mat_text, en_ulb_mat_text_l3, etc.
        // Setting limit to 100 should be enough to cover most of the cases.
        val repositories = searchRepository.execute(
            profile.gogsUser!!.id,
            translation.id,
            100,
            progressListener
        )

        if (repositories.isNotEmpty()) {
            for (repo in repositories) {
                // Filter repos to have exact user and repo name
                if (repo.owner.username == profile.gogsUser!!.username && repo.name == translation.id) {
                    return repo
                }
            }
        }

        return null
    }
}