package com.door43.usecases

import android.content.Context
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.git.Repo
import com.door43.translationstudio.git.TransportCallback
import org.bibletranslationtools.logger.Logger
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.merge.MergeStrategy
import java.io.IOException

class PullTargetTranslation(
    private val context: Context,
    private val getRepository: GetRepository,
    private val profile: Profile,
    private val transportCallback: TransportCallback
) {
    data class Result(
        val status: Status,
        val message: String?
    )

    suspend fun execute(
        targetTranslation: TargetTranslation,
        mergeStrategy: MergeStrategy,
        sourceURL: String? = null,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Result {
        if (profile.gogsUser != null) {
            try {
                targetTranslation.commitSync()
            } catch (e: java.lang.Exception) {
                Logger.w(
                    this.javaClass.name,
                    "Failed to commit the target translation " + targetTranslation.id,
                    e
                )
            }
            val repo = targetTranslation.repo
            createBackupBranch(repo)

            sourceURL ?: run {
                getRepository.execute(
                    targetTranslation,
                    onProgress
                )?.sshUrl
            }?.let { remoteUrl ->
                return pull(repo, remoteUrl, targetTranslation, mergeStrategy, onProgress)
            }
        } else {
            return Result(Status.AUTH_FAILURE, context.getString(R.string.auth_failure_retry))
        }

        return Result(Status.UNKNOWN, null)
    }

    private fun createBackupBranch(repo: Repo) {
        try {
            val git = repo.git
            val deleteBranchCommand = git.branchDelete()
            deleteBranchCommand.setBranchNames("backup-master")
                .setForce(true)
                .call()
            val createBranchCommand = git.branchCreate()
            createBranchCommand.setName("backup-master")
                .setForce(true)
                .call()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private fun pull(
        repo: Repo,
        remote: String,
        targetTranslation: TargetTranslation,
        mergeStrategy: MergeStrategy,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Result {
        onProgress(-1f, "Downloading updates")

        var status = Status.UNKNOWN
        val git: Git
        try {
            repo.deleteRemote("origin")
            repo.setRemote("origin", remote)
            git = repo.git
        } catch (e: IOException) {
            return Result(status, e.message)
        }

        val conflicts: Map<String, Array<IntArray>>
        var manifest = targetTranslation.manifest

        val pullCommand = git.pull()
            .setTransportConfigCallback(transportCallback)
            .setRemote("origin")
            .setStrategy(mergeStrategy)
            .setRemoteBranchName("master")
        try {
            val result = pullCommand.call()
            val mergeResult = result.mergeResult
            if (!mergeResult?.conflicts.isNullOrEmpty()) {
                status = Status.MERGE_CONFLICTS
                conflicts = mergeResult.conflicts

                // revert manifest merge conflict to avoid corruption
                if (conflicts.containsKey("manifest.json")) {
                    Logger.i("PullTargetTranslationTask", "Reverting to server manifest")
                    try {
                        git.checkout()
                            .setStage(CheckoutCommand.Stage.THEIRS)
                            .addPath("manifest.json")
                            .call()

                        targetTranslation.manifestAccessor.reload()
                        manifest = targetTranslation.mergeManifests(manifest)
                    } catch (e: CheckoutConflictException) {
                        // failed to reset manifest.json
                        Logger.e(this.javaClass.name, "Failed to reset manifest: " + e.message, e)
                    } finally {
                        targetTranslation.manifestAccessor.save(manifest)
                    }
                }

                // keep our license
                if (conflicts.containsKey("LICENSE.md")) {
                    Logger.i("PullTargetTranslationTask", "Reverting to local license")
                    try {
                        git.checkout()
                            .setStage(CheckoutCommand.Stage.OURS)
                            .addPath("LICENSE.md")
                            .call()
                    } catch (e: CheckoutConflictException) {
                        Logger.e(this.javaClass.name, "Failed to reset license: " + e.message, e)
                    }
                }
            } else {
                status = Status.UP_TO_DATE
            }
            return Result(status, "Pulled Successfully!")
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
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is NoRemoteRepositoryException) {
                status = Status.NO_REMOTE_REPO
            }
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
        UP_TO_DATE,
        MERGE_CONFLICTS,
        OUT_OF_MEMORY,
        AUTH_FAILURE,
        NO_REMOTE_REPO,
        UNKNOWN
    }
}