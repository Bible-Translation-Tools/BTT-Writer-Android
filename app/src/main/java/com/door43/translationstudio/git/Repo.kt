package com.door43.translationstudio.git

import com.door43.util.FileUtilities
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.GitCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.errors.LockFailedException
import org.eclipse.jgit.lib.StoredConfig
import java.io.File
import java.io.IOException

class Repo(repositoryPath: String) {

    val id: Int = numRepos++
    val localPath: String

    private var _git: Git? = null
    private var _storedConfig: StoredConfig? = null
    private var remotes: MutableSet<String> = mutableSetOf()

    init {
        val repoPath = File(repositoryPath)
        if (!repoPath.exists()) {
            repoPath.mkdir()
        }

        localPath = repositoryPath

        val gitPath = File("$localPath/.git")
        if (!gitPath.exists()) {
            initRepo()
        }
    }

    private fun initRepo() {
        val init = Git.init()
        val initFile = File(localPath)
        init.setDirectory(initFile)
        try {
            init.call()
        } catch (e: GitAPIException) {
            e.printStackTrace()
        }
    }

    val dir: File
        get() = File(localPath)

    val git: Git
        @Throws(IOException::class)
        get() {
            if (_git == null) {
                _git = Git.open(dir)
            }
            return _git!!
        }

    fun getBranchName(): String {
        return try {
            git.repository.fullBranch
        } catch (e: IOException) {
            ""
        }
    }

    @Throws(IOException::class)
    fun getRemotes(): Set<String> {
        if (remotes.isNotEmpty()) return remotes

        val config = getStoredConfig()
        val remoteNames = config.getSubsections("remote")
        remotes = remoteNames.toMutableSet()
        return remotes
    }

    @Throws(IOException::class)
    fun setRemote(remote: String, url: String) {
        val config = getStoredConfig()
        val remoteNames = config.getSubsections("remote")

        if (remoteNames.contains(remote)) {
            throw IOException(String.format("Remote %s already exists.", remote))
        }

        config.setString("remote", remote, "url", url)
        val fetch = String.format("+refs/heads/*:refs/remotes/%s/*", remote)
        config.setString("remote", remote, "fetch", fetch)
        config.save()
        remotes.add(remote)
    }

    @Throws(IOException::class)
    fun deleteRemote(remote: String) {
        val config = getStoredConfig()
        config.unsetSection("remote", remote)
    }

    @Throws(IOException::class)
    fun getStoredConfig(): StoredConfig {
        if (_storedConfig == null) {
            _storedConfig = git.repository.config
        }
        return _storedConfig!!
    }

    companion object {
        private var numRepos = 0

        /**
         * This will call a git command while attempting to handle lock exceptions.
         * If the repo is locked it will wait and try again several times before removing the lock and
         * calling the command once more. This last call may throw an exception.
         *
         * Use this with caution. You could break things by ignoring the git lock.
         */
        @Deprecated("Use with caution. You could break things by ignoring the git lock.")
        @Throws(GitAPIException::class)
        fun forceCall(command: GitCommand<*>): Any {
            try {
                return command.call()!!
            } catch (e: Exception) {
                if (e is JGitInternalException || e is GitAPIException) {
                    if (!hasCause(e, LockFailedException::class.java)) throw e
                } else {
                    throw e
                }
            }

            // re-try several times
            var attempts = 0
            do {
                attempts++
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                try {
                    return command.call()!!
                } catch (e: Exception) {
                    if (e is JGitInternalException || e is GitAPIException) {
                        if (!hasCause(e, LockFailedException::class.java)) throw e
                    } else {
                        throw e
                    }
                }
            } while (attempts < 30) // try several times up to 15 seconds

            // remove lock and call once more
            val gitDir = command.repository.directory
            val lockFile = File(gitDir, "index.lock")
            if (lockFile.exists()) FileUtilities.deleteQuietly(lockFile)

            return command.call()!!
        }

        /**
         * Checks if the throwable has the given cause
         */
        private fun hasCause(thrown: Throwable, cause: Class<*>): Boolean {
            if (cause.isInstance(thrown)) return true
            var child = thrown.cause

            while (child != null) {
                if (cause.isInstance(child)) return true
                child = child.cause
            }
            return false
        }
    }
}