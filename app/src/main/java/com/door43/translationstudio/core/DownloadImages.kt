package com.door43.translationstudio.core

import android.annotation.SuppressLint
import android.util.Log
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.context
import com.door43.translationstudio.R
import com.door43.util.FileUtilities
import com.door43.util.FileUtilities.moveOrCopyQuietly
import com.door43.util.Zip
import org.unfoldingword.tools.http.GetRequest
import org.unfoldingword.tools.http.Request
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject

/**
 * Created by blm on 12/28/16.  Revived from pre-resource container code.
 * This is a temporary solution to downloading images until a resource container solution
 * is ready.
 */
class DownloadImages @Inject constructor(
    private val directoryProvider: IDirectoryProvider
) {
    data class Result(val success: Boolean, val imagesDir: File?)

    /**
     *
     * @param listener
     * @return
     */
    @SuppressLint("DefaultLocale")
    fun download(listener: OnProgressListener? = null): File? {
        // TODO: 1/21/2016 we need to be sure to download images for the correct project.
        // Right now only obs has images
        // eventually the api will be updated so we can easily download the correct images.

        val imagesDir = File(directoryProvider.externalAppDir, "assets/images")
        val fullPath = File(String.format("%s/%s", imagesDir, "images.zip"))

        imagesDir.mkdirs()

        val success = requestToFile(fullPath, listener)
        return if (success) {
            var fileCount = 0
            try {
                val tempDir = File(imagesDir, "temp")
                tempDir.mkdirs()

                val outOf = context()!!.resources.getString(R.string.out_of)
                val unpacking = context()!!.resources.getString(R.string.unpacking)
                listener?.onProgress(0, TOTAL_FILE_COUNT, unpacking)
                Log.i(TAG, "unpacking: ")

                Zip.unzip(fullPath, tempDir)
                FileUtilities.deleteQuietly(fullPath)

                // move files out of dir
                for (dir in tempDir.listFiles()!!) {
                    if (dir.isDirectory) {
                        for (f in dir.listFiles()!!) {
                            moveOrCopyQuietly(f, File(imagesDir, f.name))

                            val message = String.format(
                                "%s: %d %s %d",
                                unpacking,
                                ++fileCount,
                                outOf,
                                TOTAL_FILE_COUNT
                            )
                            listener?.onProgress(fileCount, TOTAL_FILE_COUNT, message)
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

    private fun requestToFile(
        outputFile: File,
        listener: OnProgressListener?
    ): Boolean {
        val url: URL
        try {
            url = URL(IMAGES_URL)
        } catch (e: MalformedURLException) {
            e.printStackTrace()
            return false
        }

        val outOf = context()!!.resources.getString(R.string.out_of)
        val mbDownloaded = context()!!.resources.getString(R.string.mb_downloaded)

        val r = GetRequest(url)
        r.setTimeout(5000)
        r.setProgressListener(object : Request.OnProgressListener {
            @SuppressLint("DefaultLocale")
            override fun onProgress(max: Long, progress: Long) {
                listener?.let {
                    val message = String.format(
                        "%2.2f %s %2.2f %s",
                        progress / (1024f * 1024f),
                        outOf,
                        max / (1024f * 1024f),
                        mbDownloaded
                    )
                    listener.onProgress(progress.toInt(), max.toInt(), message)
                    // Log.i(TAG,  "Download progress - " + progress + "out of " + max);
                }
            }

            override fun onIndeterminate() {
                listener?.onIndeterminate()
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
