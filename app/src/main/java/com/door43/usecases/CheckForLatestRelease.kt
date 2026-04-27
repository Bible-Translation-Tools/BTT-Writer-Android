package com.door43.usecases

import android.content.Context
import android.content.pm.PackageManager
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.network.GetRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bibletranslationtools.logger.Logger
import java.io.IOException

class CheckForLatestRelease(
    private val context: Context,
    private val prefRepository: IPreferenceRepository
) {
    data class Result(val release: Release?)

    val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun execute(): Result {


        var release: Release? = null

        val githubApiUrl = prefRepository.getGithubRepoApi()
        val url = "$githubApiUrl/releases/latest"

        val releaseStr = try {
            val request = GetRequest(url)
            request.read()
        } catch (e: IOException) {
            Logger.e(
                TAG,
                "Failed to check for the latest release",
                e
            )
            null
        }

        releaseStr?.let {
            try {
                val releaseInfo: ReleaseInfo = json.decodeFromString(it)
                val tagParts = releaseInfo.tagName.split("\\+".toRegex())

                if (tagParts.size == 2) {
                    val build = tagParts[1].toInt()
                    try {
                        if (build > BuildConfig.VERSION_CODE) {
                            releaseInfo.assets.firstOrNull()?.let { asset ->
                                release = Release(
                                    releaseInfo.name,
                                    asset.browserDownloadUrl,
                                    asset.size,
                                    build
                                )
                            }
                        }
                    } catch (e: PackageManager.NameNotFoundException) {
                        Logger.e(TAG, "Failed to fetch the package info", e)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to parse the latest release", e)
            }
        }

        return Result(release)
    }

    companion object {
        val TAG: String = CheckForLatestRelease::class.java.simpleName
    }

    data class Release(
        val name: String,
        val downloadUrl: String,
        val downloadSize: Int,
        val build: Int
    )
}

@Serializable
private data class ReleaseInfo(
    @SerialName("tag_name")
    val tagName: String,
    val name: String,
    val assets: List<Asset>
)

@Serializable
private data class Asset(
    @SerialName("browser_download_url")
    val browserDownloadUrl: String,
    val size: Int
)