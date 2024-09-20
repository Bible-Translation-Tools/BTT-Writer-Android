package com.door43.usecases

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.door43.translationstudio.App.Companion.isStoreVersion
import com.door43.usecases.CheckForLatestRelease.Release
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DownloadLatestRelease @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun execute(release: Release) {
        if (isStoreVersion) {
            // open play store
            val appPackageName: String = context.packageName
            try {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$appPackageName")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        } else {
            // download from github
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
            browserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(browserIntent)
        }
    }
}