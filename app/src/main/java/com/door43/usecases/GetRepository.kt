package com.door43.usecases

import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import org.bibletranslationtools.gogsclient.Repository
import org.bibletranslationtools.logger.Logger

class GetRepository(
    private val createRepository: CreateRepository,
    private val searchRepository: SearchGogsRepositories,
    private val profile: Profile
) {
    suspend fun execute(
        translation: TargetTranslation,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Repository? {
        onProgress(-1f, "Getting repository")

        val user = profile.gogsUser ?: run {
            Logger.e(this.javaClass.name, "Gogs user is not set")
            return null
        }

        // Create repository
        // If it exists, will do nothing
        createRepository.execute(translation, onProgress)

        // Search for repository
        // There could be more than one repo, which name can contain requested repo name.
        // For example: en_ulb_mat_txt, custom_en_ulb_mat_text, en_ulb_mat_text_l3, etc.
        // Setting limit to 100 should be enough to cover most of the cases.
        val repositories = searchRepository.execute(
            user.id,
            translation.id,
            100,
            onProgress
        )

        return repositories.find {
            it.owner?.username == user.username && it.name == translation.id

        }
    }
}