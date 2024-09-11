package com.door43.usecases

import com.door43.OnProgressListener
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class DownloadResourceContainer @Inject constructor(
    private val library: Door43Client
) {
    private val max = 100

    data class DownloadResult(
        val success: Boolean,
        val position: Int,
        val containers: List<ResourceContainer>
    )

    fun execute(translation: Translation, position: Int, onProgressListener: OnProgressListener? = null): DownloadResult {
        var success = false
        val downloadedContainers = arrayListOf<ResourceContainer>()

        onProgressListener?.onProgress(-1, max, "Downloading resource container")

        try {
            val rc = library.download(
                translation.language.slug,
                translation.project.slug,
                translation.resource.slug
            )
            downloadedContainers.add(rc)
            success = true
        } catch (e: Exception) {
            Logger.e(
                DownloadResourceContainer::class.java.simpleName,
                "Download source Failed: " + translation.resourceContainerSlug,
                e
            )
        }

        if (success) {
            // also download helps
            if (translation.resource.slug != "tw" && translation.resource.slug != "tn" && translation.resource.slug != "tq") {
                // TODO: 11/2/16 only download these if there is an update
                try {
                    if (translation.project.slug == "obs") {
                        onProgressListener?.onProgress(-1, max, "Downloading obs translation words")
                        val rc = library.download(translation.language.slug, "bible-obs", "tw")
                        downloadedContainers.add(rc)
                    } else {
                        onProgressListener?.onProgress(-1, max, "Downloading translation words")
                        val rc = library.download(translation.language.slug, "bible", "tw")
                        downloadedContainers.add(rc)
                    }
                } catch (e: java.lang.Exception) {
                    Logger.e(
                        DownloadResourceContainer::class.java.simpleName,
                        "Download translation words Failed: " + translation.resourceContainerSlug,
                        e
                    )
                }
                try {
                    onProgressListener?.onProgress(-1, max, "Downloading translation notes")
                    val rc = library.download(
                        translation.language.slug,
                        translation.project.slug,
                        "tn"
                    )
                    downloadedContainers.add(rc)
                } catch (e: java.lang.Exception) {
                    Logger.e(
                        DownloadResourceContainer::class.java.simpleName,
                        "Download translation notes Failed: " + translation.resourceContainerSlug,
                        e
                    )
                }
                try {
                    onProgressListener?.onProgress(-1, max, "Downloading translation questions")
                    val rc = library.download(
                        translation.language.slug,
                        translation.project.slug,
                        "tq"
                    )
                    downloadedContainers.add(rc)
                } catch (e: java.lang.Exception) {
                    Logger.e(
                        DownloadResourceContainer::class.java.simpleName,
                        "Download translation questions Failed: " + translation.resourceContainerSlug,
                        e
                    )
                }
            }
        }

        return DownloadResult(success, position, downloadedContainers)
    }
}