package com.door43.usecases

import com.door43.OnProgressListener
import com.door43.translationstudio.App
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

class DownloadResourceContainers @Inject constructor(
    private val library: Door43Client
) {

    data class Result(
        val downloadedContainers: List<ResourceContainer>,
        val downloadedTranslations: List<String>,
        val failedSourceDownloads: List<String>,
        val failedHelpsDownloads: List<String>,
        val failureMessages: Map<String, String>
    )

    data class DownloadResult(
        val success: Boolean,
        val containers: List<ResourceContainer>
    )

    fun download(
        translation: Translation,
        onProgressListener: OnProgressListener? = null
    ): DownloadResult {
        var success = false
        val max = 100
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
                DownloadResourceContainers::class.java.simpleName,
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
                    Logger.w(
                        DownloadResourceContainers::class.java.simpleName,
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
                    Logger.w(
                        DownloadResourceContainers::class.java.simpleName,
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
                    Logger.w(
                        DownloadResourceContainers::class.java.simpleName,
                        "Download translation questions Failed: " + translation.resourceContainerSlug,
                        e
                    )
                }
            }
        }

        return DownloadResult(success, downloadedContainers)
    }

    fun download(
        translationIDs: List<String>,
        progressListener: OnProgressListener? = null
    ): Result {
        val downloadedContainers = arrayListOf<ResourceContainer>()
        val failedSourceDownloads = arrayListOf<String>()
        val failureMessages = hashMapOf<String, String>()
        val failedHelpsDownloads = arrayListOf<String>()
        val downloadedTranslations = arrayListOf<String>()
        val downloadedTwBibleLanguages = HashSet<String>()
        val downloadedTwObsLanguages = HashSet<String>()

        val maxProgress = translationIDs.size

        progressListener?.onProgress(-1, maxProgress, "")

        for (index in 0 until maxProgress) {
            val resourceContainerSlug = translationIDs[index]
            var translation: Translation? = null
            var passSuccess = false

            progressListener?.onProgress(index, maxProgress, resourceContainerSlug)

            Logger.i(
                this.javaClass.simpleName,
                "Loading ID: $resourceContainerSlug"
            )

            try {
                translation = library.index.getTranslation(resourceContainerSlug)
                val rc = library.download(
                    translation.language.slug,
                    translation.project.slug,
                    translation.resource.slug
                )
                downloadedContainers.add(rc)
                Logger.i(
                    this.javaClass.simpleName,
                    "download Success: " + translation.resourceContainerSlug
                )
                passSuccess = true
            } catch (e: Exception) {
                Logger.e(
                    this.javaClass.simpleName,
                    "download source Failed: $resourceContainerSlug", e
                )
                e.printStackTrace()
                failureMessages[resourceContainerSlug] = e.message ?: "Unknown error"
                failedSourceDownloads.add(resourceContainerSlug)
            }

            if (passSuccess) {
                // also download helps
                translation?.let { tr ->
                    val resourceSlug = tr.resource.slug
                    val languageSlug = tr.language.slug
                    val projectSlug = tr.project.slug

                    if (resourceSlug != "tw" && resourceSlug != "tn" && resourceSlug != "tq" && resourceSlug != "udb") {
                        // TODO: 11/2/16 only download these if there is an update
                        try {
                            if (projectSlug == "obs") {
                                passSuccess = downloadTranslationWords(
                                    index,
                                    resourceContainerSlug,
                                    downloadedTwObsLanguages,
                                    languageSlug,
                                    "bible-obs",
                                    "OBS Words",
                                    maxProgress,
                                    downloadedContainers,
                                    failedHelpsDownloads,
                                    failedSourceDownloads,
                                    progressListener
                                )
                            } else {
                                passSuccess = downloadTranslationWords(
                                    index,
                                    resourceContainerSlug,
                                    downloadedTwBibleLanguages,
                                    languageSlug,
                                    "bible",
                                    "Bible Words",
                                    maxProgress,
                                    downloadedContainers,
                                    failedHelpsDownloads,
                                    failedSourceDownloads,
                                    progressListener
                                )
                            }
                        } catch (e: java.lang.Exception) {
                            Logger.w(
                                this.javaClass.simpleName,
                                "download translation words Failed: $resourceContainerSlug", e
                            )
                            e.printStackTrace()
                        }

                        passSuccess = passSuccess and downloadHelps(
                            index,
                            resourceContainerSlug,
                            languageSlug,
                            projectSlug,
                            "tn",
                            "Notes",
                            maxProgress,
                            downloadedContainers,
                            failedHelpsDownloads,
                            failedSourceDownloads,
                            progressListener
                        )

                        passSuccess = passSuccess and downloadHelps(
                            index,
                            resourceContainerSlug,
                            languageSlug,
                            projectSlug,
                            "tq",
                            "Questions",
                            maxProgress,
                            downloadedContainers,
                            failedHelpsDownloads,
                            failedSourceDownloads,
                            progressListener
                        )
                    }
                }
            }

            if (passSuccess) {
                downloadedTranslations.add(resourceContainerSlug)
            }
        }

        progressListener?.onProgress(maxProgress, maxProgress, "")

        return Result(
            downloadedContainers,
            downloadedTranslations,
            failedSourceDownloads,
            failedHelpsDownloads,
            failureMessages
        )
    }

    /**
     * handles specific translation words download for resource, only downloads once for each language
     * @param progress
     * @param resourceContainerSlug
     * @param downloaded
     * @param languageSlug
     * @param projectSlug
     * @param name
     * @return
     */
    private fun downloadTranslationWords(
        progress: Int,
        resourceContainerSlug: String,
        downloaded: MutableSet<String>,
        languageSlug: String,
        projectSlug: String,
        name: String,
        maxProgress: Int,
        downloadedContainers: ArrayList<ResourceContainer>,
        failedHelpsDownloads: ArrayList<String>,
        failedSourceDownloads: ArrayList<String>,
        progressListener: OnProgressListener?
    ): Boolean {
        var success = true
        if (!downloaded.contains(languageSlug)) {
            success = downloadHelps(
                progress,
                resourceContainerSlug,
                languageSlug,
                projectSlug,
                "tw",
                name,
                maxProgress,
                downloadedContainers,
                failedHelpsDownloads,
                failedSourceDownloads,
                progressListener
            )
            if (success) {
                downloaded.add(languageSlug)
            }
        } else {
            Logger.i(
                this.javaClass.simpleName,
                "'$name' already downloaded for: $languageSlug"
            )
        }
        return success
    }

    /**
     * handles specific helps download for resource
     * @param progress
     * @param resourceContainerSlug
     * @param languageSlug
     * @param projectSlug
     * @param resourceSlug
     * @param name
     * @return
     */
    private fun downloadHelps(
        progress: Int,
        resourceContainerSlug: String,
        languageSlug: String,
        projectSlug: String,
        resourceSlug: String,
        name: String,
        maxProgress: Int,
        downloadedContainers: ArrayList<ResourceContainer>,
        failedHelpsDownloads: ArrayList<String>,
        failedSourceDownloads: ArrayList<String>,
        progressListener: OnProgressListener?
    ): Boolean {
        var passSuccess = true
        try {
            // check if helps present before trying to download
            val helps = library.index.findTranslations(
                languageSlug,
                projectSlug,
                resourceSlug,
                null,
                null,
                App.MIN_CHECKING_LEVEL,
                -1
            )
            if (helps.size == 0) {
                Logger.i(
                    this.javaClass.simpleName,
                    "No '$name' for: $resourceContainerSlug"
                )
            }
            for (help in helps) {
                Logger.i(
                    this.javaClass.simpleName,
                    "Loading " + name + " ID: " + help.resourceContainerSlug
                )
                progressListener?.onProgress(progress, maxProgress, help.resourceContainerSlug)
                val rc = library.download(help.language.slug, help.project.slug, help.resource.slug)
                downloadedContainers.add(rc)
                Logger.i(this.javaClass.simpleName, name + " download Success: " + rc.slug)
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            val resource = languageSlug + "_" + projectSlug + "_" + resourceSlug
            Logger.w(
                this.javaClass.simpleName,
                "$name download Helps Failed: $resource", e
            )
            failedHelpsDownloads.add(resource)
            failedSourceDownloads.add(resourceContainerSlug) // if helps download failed, then mark the source as error also
            passSuccess = false
        }
        return passSuccess
    }
}