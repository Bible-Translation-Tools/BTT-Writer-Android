package com.door43.translationstudio.core

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.network.GetRequest
import com.door43.translationstudio.network.HttpRequest
import com.door43.util.FileUtilities
import com.door43.util.FileUtilities.moveOrCopyQuietly
import com.door43.util.Zip
import java.io.File
import java.io.IOException

/**
 * Created by blm on 12/28/16.  Revived from pre-resource container code.
 * This is a temporary solution to downloading images until a resource container solution
 * is ready.
 */
class DownloadImages(
    private val context: Context,
    private val directoryProvider: IDirectoryProvider
) {
    /**
     *
     * @param onProgress
     * @return
     */
    @SuppressLint("DefaultLocale")
    suspend fun download(onProgress: (Float, String?) -> Unit): File? {
        // TODO: 1/21/2016 we need to be sure to download images for the correct project.
        // Right now only obs has images
        // eventually the api will be updated so we can easily download the correct images.

        val imagesDir = File(directoryProvider.externalAppDir, "assets/images")
        val fullPath = File(String.format("%s/%s", imagesDir, "images.zip"))

        imagesDir.mkdirs()

        val success = requestToFile(fullPath, onProgress)
        return if (success) {
            var fileCount = 0
            try {
                val tempDir = File(imagesDir, "temp")
                tempDir.mkdirs()

                val outOf = context.getString(R.string.out_of)
                val unpacking = context.getString(R.string.unpacking)
                onProgress(0f, unpacking)
                Log.i(TAG, "unpacking: ")

                Zip.unzip(fullPath, tempDir)
                FileUtilities.deleteQuietly(fullPath)

                // move files out of dir
                for (dir in tempDir.listFiles()!!) {
                    if (dir.isDirectory) {
                        for (f in dir.listFiles()!!) {
                            moveOrCopyQuietly(f, File(imagesDir, f.name))
                            val progress = fileCount / TOTAL_FILE_COUNT.toFloat()

                            val message = String.format(
                                "%s: %d %s %d",
                                unpacking,
                                ++fileCount,
                                outOf,
                                TOTAL_FILE_COUNT
                            )
                            onProgress(progress, message)
                            // Log.i(TAG,  "Download progress - " + fileCount + " out of " + TOTAL_FILE_COUNT);
                        }
                    }
                }
                FileUtilities.deleteQuietly(tempDir)
                imagesDir
            } catch (e: Exception) {
                null
            }
        } else null
    }

    private suspend fun requestToFile(
        outputFile: File,
        onProgress: (Float, String?) -> Unit
    ): Boolean {
        val outOf = context.getString(R.string.out_of)
        val mbDownloaded = context.getString(R.string.mb_downloaded)

        val r = GetRequest(IMAGES_URL)
        r.setTimeout(5000)
        r.setProgressListener(object : HttpRequest.OnProgressListener {
            @SuppressLint("DefaultLocale")
            override fun onProgress(max: Long, progress: Long) {
                val message = String.format(
                    "%2.2f %s %2.2f %s",
                    progress / (1024f * 1024f),
                    outOf,
                    max / (1024f * 1024f),
                    mbDownloaded
                )
                onProgress(progress / max.toFloat(), message)
                // Log.i(TAG,  "Download progress - " + progress + "out of " + max);
            }
            override fun onIndeterminate() {
            }
        })

        try {
            r.download(outputFile)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    companion object {
        val TAG: String = DownloadImages::class.java.name
        private const val IMAGES_URL = "https://cdn.unfoldingword.org/obs/jpg/obs-images-360px.zip"
        const val TOTAL_FILE_COUNT: Int = 598
    }
}
