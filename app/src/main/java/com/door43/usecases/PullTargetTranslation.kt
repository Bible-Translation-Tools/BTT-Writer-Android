package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.git.Repo
import com.door43.translationstudio.git.TransportCallback
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.util.Manifest
import dagger.hilt.android.qualifiers.ApplicationContext
import org.eclipse.jgit.api.CheckoutCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.errors.NoRemoteRepositoryException
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import javax.inject.Inject

class PullTargetTranslation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val submitNewLanguageRequests: SubmitNewLanguageRequests,
    private val getRepository: GetRepository,
    private val profile: Profile,
    private val prefRepository: IPreferenceRepository,
    private val directoryProvider: IDirectoryProvider
) {
    data class Result(
        val status: Status,
        val message: String?
    )

    private val max = 100

    fun execute(
        targetTranslation: TargetTranslation,
        mergeStrategy: MergeStrategy,
        sourceURL: String? = null,
        progressListener: OnProgressListener? = null
    ): Result {
        submitNewLanguageRequests.execute(progressListener)

        if (profile.gogsUser != null) {
            progressListener?.onProgress(-1, max,"Downloading updates")

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
                    progressListener
                )?.sshUrl
            }?.let { remoteUrl ->
                return pull(repo, remoteUrl, targetTranslation, mergeStrategy)
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
        mergeStrategy: MergeStrategy
    ): Result {
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
        var localManifest = Manifest.generate(targetTranslation.path)

        // TODO: we might want to get some progress feedback for the user
        val port = prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_GIT_SERVER_PORT,
            context.resources.getString(R.string.pref_default_git_server_port)
        ).toInt()
        val pullCommand = git.pull()
            .setTransportConfigCallback(TransportCallback(directoryProvider, port))
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
                        val remoteManifest = Manifest.generate(targetTranslation.path)
                        localManifest =
                            TargetTranslation.mergeManifests(localManifest, remoteManifest)
                    } catch (e: CheckoutConflictException) {
                        // failed to reset manifest.json
                        Logger.e(this.javaClass.name, "Failed to reset manifest: " + e.message, e)
                    } finally {
                        localManifest.save()
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
            Logger.e(this.javaClass.name, e.message, e)
            val cause = e.cause
            if (cause != null) {
                val subException = cause.cause
                if (subException != null) {
                    val detail = subException.message
                    if ("Auth fail" == detail) {
                        status = Status.AUTH_FAILURE // we do special handling for auth failure
                    }
                } else if (cause is NoRemoteRepositoryException) {
                    status = Status.NO_REMOTE_REPO
                }
            }
            return Result(status, null)
        } catch (e: OutOfMemoryError) {
            Logger.e(this.javaClass.name, e.message, e)
            status = Status.OUT_OF_MEMORY
            return Result(status, null)
        } catch (e: Exception) {
            val cause = e.cause
            if (cause is NoRemoteRepositoryException) {
                status = Status.NO_REMOTE_REPO
            }
            Logger.e(this.javaClass.name, e.message, e)
            return Result(status, null)
        } catch (e: Throwable) {
            Logger.e(this.javaClass.name, e.message, e)
            return Result(status, null)
        }
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