package com.door43.usecases

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.door43.translationstudio.Platform
import com.door43.usecases.CheckForLatestRelease.Release

class DownloadLatestRelease(
    private val context: Context,
    private val platform: Platform
) {
    fun execute(release: Release) {
        if (platform.isStoreVersion) {
            // open play store
            val appPackageName: String = context.packageName
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$appPackageName".toUri()
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=$appPackageName".toUri()
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } else {
            // download from GitHub
            val browserIntent = Intent(Intent.ACTION_VIEW, release.downloadUrl.toUri())
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(browserIntent)
        }
    }
}