package com.door43.usecases

import android.content.Context
import android.net.Uri
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import org.bibletranslationtools.logger.Logger
import org.unfoldingword.door43client.Door43Client
import java.net.HttpURLConnection
import java.net.URL

class DownloadIndex(
    private val context: Context,
    private val directoryProvider: IDirectoryProvider,
    private val prefRepository: IPreferenceRepository,
    private val library: Door43Client
) {
    fun download(onProgress: (Float, String?) -> Unit = {_,_->}): Boolean {
        var connection: HttpURLConnection? = null
        val message = context.resources.getString(R.string.downloading_index)

        onProgress(-1f, message)

        return try {
            library.tearDown()

            val url = prefRepository.getDefaultPref(
                IPreferenceRepository.KEY_PREF_INDEX_SQLITE_URL,
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
                                val progress = total / fileLength.toFloat()
                                onProgress(progress, message)
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
                    true
                }
            } ?: false
        } catch (e: Exception) {
            Logger.e(this::javaClass.name, "Failed to import index", e)
            false
        }
    }
}