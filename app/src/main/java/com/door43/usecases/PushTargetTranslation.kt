package com.door43.usecases

import android.content.Context
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.git.Repo
import com.door43.translationstudio.git.TransportCallback
import org.bibletranslationtools.logger.Logger
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import java.io.IOException

class PushTargetTranslation(
    private val context: Context,
    private val profile: Profile,
    private val getRepository: GetRepository,
    private val transportCallback: TransportCallback
) {
    data class Result(
        val status: Status,
        val message: String?
    )

    suspend fun execute(
        targetTranslation: TargetTranslation,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Result {
        if (profile.gogsUser != null) {
            val repository = getRepository.execute(targetTranslation, onProgress)
            try {
                targetTranslation.commitSync()
                val repo: Repo = targetTranslation.repo
                return push(repo, repository!!.sshUrl, onProgress)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            return Result(Status.AUTH_FAILURE, null)
        }

        return Result(Status.UNKNOWN, null)
    }

    @Throws(JGitInternalException::class)
    private fun push(
        repo: Repo,
        remote: String,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Result {
        onProgress(-1f, "Uploading translation")

        var status = Status.UNKNOWN
        val git: Git
        try {
            repo.deleteRemote("origin")
            repo.setRemote("origin", remote)
            git = repo.git
        } catch (e: IOException) {
            return Result(status, e.message)
        }

        val spec = RefSpec("refs/heads/master")

        val pushCommand = git.push()
            .setTransportConfigCallback(transportCallback)
            .setRemote("origin")
            .setPushTags()
            .setForce(false)
            .setRefSpecs(spec)

        try {
            val pushResults = pushCommand.call()
            val response = StringBuilder()
            status = Status.OK // will be OK if no errors are found
            for (r in pushResults) {
                val updates = r.remoteUpdates
                for (update in updates) {
                    response.append(parseRemoteRefUpdate(update, remote))
                    response.append("\n")

                    val updateStatus = update.status
                    when (updateStatus) {
                        RemoteRefUpdate.Status.OK, RemoteRefUpdate.Status.UP_TO_DATE -> {}
                        RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> {
                            status = Status.REJECTED_NON_FAST_FORWARD
                        }
                        RemoteRefUpdate.Status.REJECTED_NODELETE -> {
                            status = Status.REJECTED_NODELETE
                        }
                        RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED -> {
                            status = Status.REJECTED_REMOTE_CHANGED
                        }
                        RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> {
                            status = Status.REJECTED_OTHER_REASON
                        }
                        else -> status = Status.UNKNOWN
                    }
                }

//                if (status.isRejected) {
//                     pushRejectedResults = r // save rejection data
//                }
            }
            // give back the response message
            return Result(status, response.toString())
        } catch (e: TransportException) {
            Logger.e(this.javaClass.name, e.message ?: "Error", e)
            val cause = e.cause
            if (cause is NoRemoteRepositoryException) {
                status = Status.NO_REMOTE_REPO
            } else if (isAuthFailure(e)) {
                status = Status.AUTH_FAILURE
            }
            return Result(status, null)
        } catch (e: OutOfMemoryError) {
            Logger.e(this.javaClass.name, e.message ?: "Error", e)
            status = Status.OUT_OF_MEMORY
            return Result(status, null)
        } catch (e: java.lang.Exception) {
            Logger.e(this.javaClass.name, e.message ?: "Error", e)
            return Result(status, null)
        } catch (e: Throwable) {
            Logger.e(this.javaClass.name, e.message ?: "Error", e)
            return Result(status, null)
        }
    }

    private fun isAuthFailure(e: Exception): Boolean {
        var t: Throwable? = e
        while (t != null) {
            val msg = t.message ?: ""
            if (msg.contains("Auth fail") ||
                msg.contains("not permitted") ||
                msg.contains("Cannot log in") ||
                msg.contains("No more authentication methods")) {
                return true
            }
            t = t.cause
        }
        return false
    }

    enum class Status {
        OK,
        OUT_OF_MEMORY,
        AUTH_FAILURE,
        NO_REMOTE_REPO,
        REJECTED_NON_FAST_FORWARD,
        REJECTED_NODELETE,
        REJECTED_OTHER_REASON,
        REJECTED_REMOTE_CHANGED,
        UNKNOWN;

        val isRejected: Boolean
            get() = this == REJECTED_NON_FAST_FORWARD ||
                    this == REJECTED_NODELETE ||
                    this == REJECTED_OTHER_REASON ||
                    this == REJECTED_REMOTE_CHANGED
    }

    /**
     * Parses the response from the remote
     * @param update
     * @return
     */
    private fun parseRemoteRefUpdate(update: RemoteRefUpdate, remote: String): String {
        var msg: String?
        when (update.status) {
            RemoteRefUpdate.Status.AWAITING_REPORT -> {
                msg = context.resources.getString(
                    R.string.git_awaiting_report,
                    update.remoteName
                )
            }
            RemoteRefUpdate.Status.NON_EXISTING -> {
                msg = context.resources.getString(
                    R.string.git_non_existing,
                    update.remoteName
                )
            }
            RemoteRefUpdate.Status.NOT_ATTEMPTED -> {
                msg = context.resources.getString(
                    R.string.git_not_attempted,
                    update.remoteName
                )
            }
            RemoteRefUpdate.Status.OK -> {
                msg = context.resources.getString(
                    R.string.git_ok,
                    update.remoteName
                )
            }
            RemoteRefUpdate.Status.REJECTED_NODELETE -> {
                msg = context.resources.getString(
                    R.string.git_rejected_nondelete,
                    update.remoteName
                )
            }
            RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD -> {
                msg = context.resources.getString(
                    R.string.git_rejected_nonfastforward,
                    update.remoteName
                )
            }
            RemoteRefUpdate.Status.REJECTED_OTHER_REASON -> {
                val reason = update.message
                msg = if (reason.isNullOrEmpty()) {
                    context.resources.getString(
                        R.string.git_rejected_other_reason,
                        update.remoteName
                    )
                } else {
                    context.resources.getString(
                        R.string.git_rejected_other_reason_detailed,
                        update.remoteName,
                        reason
                    )
                }
            }
            RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED -> {
                msg = context.resources.getString(
                    R.string.git_rejected_remote_changed,
                    update.remoteName
                )
            }
            RemoteRefUpdate.Status.UP_TO_DATE -> {
                msg = context.resources.getString(
                    R.string.git_uptodate,
                    update.remoteName
                )
            }
            else -> msg = "Unknown status"
        }
        msg += "\n" + context.resources.getString(
            R.string.git_server_details,
            remote
        )
        return msg
    }
}