package com.door43.translationstudio.core

import com.door43.translationstudio.git.Repo
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.File
import java.io.IOException

/**
 * Represents the commit history of a single file within a git repository
 */
class FileHistory @Throws(IOException::class, GitAPIException::class) constructor(
    repo: Repo,
    relativeFile: File?
) {
    private val file: File?
    private val git: Git = repo.git
    private var history = arrayOf<RevCommit>()
    private var index = 0

    init {
        // sanitize file to be relative to repo
        val localPath = repo.localPath
        this.file = if (relativeFile != null) {
            val filePathString = relativeFile.toString()
            val pos = filePathString.indexOf(localPath)
            if (pos >= 0) {
                File(filePathString.substring(pos + localPath.length + 1))
            } else {
                null
            }
        } else {
            null
        }
    }

    val atHead: Boolean
        get() = index == 0

    /**
     * Reloads the commit history
     */
    @Throws(IOException::class, GitAPIException::class)
    fun loadCommits() {
        val currentFile = file ?: run {
            history = emptyArray()
            return
        }

        // preserve current position if not at HEAD
        var currentCommit: RevCommit? = if (index > 0) current else null

        // load history
        val repository: Repository = git.repository
        val head = repository.resolve("HEAD")

        if (head != null) {
            val log = git.log().add(head).addPath(currentFile.toString())
            val commits = log.call()
            val historyList = mutableListOf<RevCommit>()

            for (commit in commits) {
                historyList.add(commit)

                // restore current position by comparing name (hash)
                currentCommit?.let { current ->
                    if (commit.name == current.name) {
                        index = historyList.size - 1
                        currentCommit = null
                    }
                }
            }
            history = historyList.toTypedArray()
        } else {
            history = emptyArray()
        }
    }

    val hasPrevious: Boolean
        get() = index + 1 < history.size

    /**
     * Returns the previous commit in the file history
     * The position in the history will not be changed if the previous index would be out of bounds
     */
    val previous: RevCommit?
        get() {
            val prevIndex = index + 1
            return getCommit(prevIndex)?.also {
                index = prevIndex
            }
        }

    /**
     * Returns the currently viewed commit of the file history
     */
    val current: RevCommit?
        get() = getCommit(index)

    val hasNext: Boolean
        get() = index - 1 >= 0 && history.isNotEmpty()

    /**
     * Returns the next commit in the file history
     * The position in the history will not be changed if the next index would be out of bounds
     */
    val next: RevCommit?
        get() {
            val nextIndex = index - 1
            return getCommit(nextIndex)?.also {
                index = nextIndex
            }
        }

    /**
     * Returns the HEAD of the commit tree
     */
    val head: RevCommit?
        get() = getCommit(0)

    fun reset() {
        index = 0
    }

    private fun getCommit(pos: Int): RevCommit? = history.getOrNull(pos)

    /**
     * Returns the file contents at the given commit
     */
    @Throws(IOException::class, IllegalStateException::class)
    fun read(commit: RevCommit): String? {
        val targetFile = file ?: return null
        val walk = TreeWalk(git.repository)
        walk.addTree(commit.tree)
        walk.isRecursive = true
        walk.filter = PathFilter.create(targetFile.toString())

        if (!walk.next()) {
            throw IllegalStateException("Did not find expected file '$targetFile'")
        }

        val objectId = walk.getObjectId(0)
        val loader = git.repository.open(objectId)
        return String(loader.bytes, Charsets.UTF_8)
    }
}