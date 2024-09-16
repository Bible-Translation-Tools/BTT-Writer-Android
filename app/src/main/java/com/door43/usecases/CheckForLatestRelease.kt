package com.door43.usecases

import android.content.Context
import android.content.pm.PackageManager
import com.door43.translationstudio.R
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.tools.http.GetRequest
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import java.io.Serializable
import java.net.URL
import javax.inject.Inject

class CheckForLatestRelease @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun execute(): Release? {
        var latestRelease: Release? = null

        val githubApiUrl = context.resources.getString(R.string.github_repo_api)
        val url = "$githubApiUrl/releases/latest"
        var latestReleaseStr: String?
        try {
            val request = GetRequest(URL(url))
            latestReleaseStr = request.read()
        } catch (e: IOException) {
            Logger.e(
                TAG,
                "Failed to check for the latest release",
                e
            )
            latestReleaseStr = null
        }
        if (latestReleaseStr != null) {
            try {
                val latestReleaseJson = JSONObject(latestReleaseStr)
                if (latestReleaseJson.has("tag_name")) {
                    val tag = latestReleaseJson.getString("tag_name")
                    val tagParts = tag.split("\\+".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    if (tagParts.size == 2) {
                        val build = tagParts[1].toInt()
                        try {
                            val pInfo = context.packageManager.getPackageInfo(
                                context.packageName, 0
                            )
                            if (build > pInfo.versionCode) {
                                var downloadUrl: String? = null
                                var downloadSize = 0
                                if (latestReleaseJson.has("assets")) {
                                    val assetsJson = latestReleaseJson.getJSONArray("assets")
                                    val assetJson = assetsJson.getJSONObject(0)
                                    if (assetJson.has("browser_download_url")) {
                                        downloadUrl = assetJson.getString("browser_download_url")
                                    }
                                    if (assetJson.has("size")) {
                                        downloadSize = assetJson.getInt("size")
                                    }
                                }
                                if (downloadUrl != null) {
                                    latestRelease = Release(
                                        latestReleaseJson.getString("name"),
                                        downloadUrl,
                                        downloadSize,
                                        build
                                    )
                                }
                            }
                        } catch (e: PackageManager.NameNotFoundException) {
                            Logger.e(TAG, "Failed to fetch the package info", e)
                        }
                    }
                }
            } catch (e: JSONException) {
                Logger.e(TAG, "Failed to parse the latest release", e)
            }
        }

        return latestRelease
    }

    companion object {
        val TAG: String = CheckForLatestRelease::class.java.simpleName
    }

    class Release(
        val name: String,
        val downloadUrl: String,
        val downloadSize: Int,
        val build: Int
    ) : Serializable {
        companion object {
            private const val SERIAL_VERSION_UID: Long = 1000000
        }
    }
}