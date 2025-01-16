package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class DownloadIndex @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client
) {
    fun download(progressListener: OnProgressListener? = null): Boolean {
        var connection: HttpURLConnection? = null
        val message = context.resources.getString(R.string.downloading_index)

        progressListener?.onProgress(-1, 100, message)

        return try {
            library.tearDown()

            val url = prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_INDEX_SQLITE_URL,
                context.resources.getString(R.string.pref_default_index_sqlite_url)
            )
            val downloadUrl = URL(url)

            connection = downloadUrl.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val fileLength = connection.contentLength

                connection.inputStream.use { input ->
                    directoryProvider.databaseFile.outputStream().use { output ->
                        val data = ByteArray(4096)
                        var total = 0
                        var count: Int
                        while ((input.read(data).also { count = it }) != -1) {
                            total += count
                            if (fileLength > 0) {
                                progressListener?.onProgress(total, fileLength, message)
                            }
                            output.write(data, 0, count)
                        }
                    }
                }
                true
            } else false
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    fun import(index: Uri): Boolean {
        return try {
            library.tearDown()

            context.contentResolver.openInputStream(index)?.use { input ->
                directoryProvider.databaseFile.outputStream().use { output ->
                    val data = ByteArray(4096)
                    var total = 0
                    var count: Int
                    while ((input.read(data).also { count = it }) != -1) {
                        total += count
                        output.write(data, 0, count)
                    }
                }
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }
}