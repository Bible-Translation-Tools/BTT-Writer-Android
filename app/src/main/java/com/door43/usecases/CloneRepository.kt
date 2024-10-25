package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.git.TransportCallback
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.util.FileUtilities.deleteQuietly
import dagger.hilt.android.qualifiers.ApplicationContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

class CloneRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository,
    private val directoryProvider: IDirectoryProvider
) {
    private val max = 100

    fun execute(
        cloneUrl: String,
        progressListener: OnProgressListener? = null
    ): Result {
        progressListener?.onProgress(-1, max, context.resources.getString(R.string.downloading))

        var tempDir: File? = directoryProvider.createTempDir(System.currentTimeMillis().toString())
        var status = Status.UNKNOWN

        try {
            // prepare destination
            val port = prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_GIT_SERVER_PORT,
                context.resources.getString(R.string.pref_default_git_server_port)
            ).toInt()
            val cloneCommand = Git.cloneRepository()
                .setTransportConfigCallback(TransportCallback(directoryProvider, port))
                .setURI(cloneUrl)
                .setDirectory(tempDir)
            try {
                val cloneResult = cloneCommand.call()
                cloneResult.repository.close()
                status = Status.SUCCESS
            } catch (e: TransportException) {
                Logger.e(this.javaClass.name, e.message, e)
                val cause = e.cause
                if (cause != null) {
                    val subException = cause.cause
                    when {
                        subException != null -> {
                            val detail = subException.message
                            if ("Auth fail" == detail) {
                                status = Status.AUTH_FAILURE
                            }
                        }
                        cause.message!!.contains("not permitted") -> {
                            status = Status.AUTH_FAILURE
                        }
                        cause is NoRemoteRepositoryException -> {
                            status = Status.NO_REMOTE_REPO
                        }
                        else -> status = Status.NO_REMOTE_REPO
                    }
                }
            } catch (e: Exception) {
                Logger.e(this.javaClass.name, e.message, e)
            } catch (e: OutOfMemoryError) {
                Logger.e(this.javaClass.name, e.message, e)
                status = Status.OUT_OF_MEMORY
            } catch (e: Throwable) {
                Logger.e(this.javaClass.name, e.message, e)
            }
        } catch (e: Exception) {
            Logger.e(
                this.javaClass.name,
                "Failed to clone the repository $cloneUrl", e
            )
        } finally {
            if (status != Status.SUCCESS) {
                deleteQuietly(tempDir)
                tempDir = null
            }
        }

        return Result(status, cloneUrl, tempDir)
    }

    data class Result(val status: Status, val cloneUrl: String, val cloneDir: File?)

    enum class Status {
        NO_REMOTE_REPO,
        UNKNOWN,
        AUTH_FAILURE,
        OUT_OF_MEMORY,
        SUCCESS
    }
}