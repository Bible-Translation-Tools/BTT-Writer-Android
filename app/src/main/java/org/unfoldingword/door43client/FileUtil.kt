package org.unfoldingword.door43client

import java.io.Closeable
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel

/**
 * Created by joel on 9/1/16.
 * Converted to Kotlin object.
 */
internal object FileUtil {

    /**
     * Converts an input stream into a string
     */
    @Throws(IOException::class)
    fun readStreamToString(input: InputStream): String {
        return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * Returns the contents of a file as a string
     */
    @Throws(IOException::class)
    fun readFileToString(file: File): String {
        return file.readText(Charsets.UTF_8)
    }

    /**
     * Writes a string to a file
     */
    @Throws(IOException::class)
    fun writeStringToFile(file: File, contents: String) {
        file.writeText(contents, Charsets.UTF_8)
    }

    @Throws(IOException::class)
    fun copyInputStreamToFile(source: InputStream, destination: File) {
        source.use { input ->
            openOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    @Throws(IOException::class)
    fun openOutputStream(file: File, append: Boolean = false): FileOutputStream {
        if (file.exists()) {
            if (file.isDirectory) {
                throw IOException("File '$file' exists but is a directory")
            }
            if (!file.canWrite()) {
                throw IOException("File '$file' cannot be written to")
            }
        } else {
            val parent = file.parentFile
            if (parent != null && !parent.mkdirs() && !parent.isDirectory) {
                throw IOException("Directory '$parent' could not be created")
            }
        }
        return FileOutputStream(file, append)
    }

    @Throws(IOException::class)
    fun copy(input: InputStream, output: OutputStream): Int {
        val count = input.copyTo(output)
        return if (count > Int.MAX_VALUE) -1 else count.toInt()
    }

    /**
     * Returns the extension of the file.
     * If no delimiter is found or there is no extension the result is an empty string
     */
    fun getExtension(path: String): String {
        val index = path.lastIndexOf(".")
        if (index == -1 || index == path.length - 1) {
            return ""
        }
        return path.substring(index + 1)
    }

    /**
     * Recursively deletes a directory or just deletes the file
     */
    fun deleteQuietly(fileOrDirectory: File?): Boolean {
        if (fileOrDirectory == null) return true

        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                if (!deleteQuietly(child)) {
                    return false
                }
            }
        }
        return try {
            if (fileOrDirectory.exists()) fileOrDirectory.delete() else true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Attempts to move a file or directory. If moving fails it will try to copy instead.
     */
    fun moveOrCopyQuietly(sourceFile: File, destFile: File): Boolean {
        if (sourceFile.exists()) {
            // first try to move
            if (!sourceFile.renameTo(destFile)) {
                // try to copy
                try {
                    if (sourceFile.isDirectory) {
                        copyDirectory(sourceFile, destFile, null)
                    } else {
                        copyFile(sourceFile, destFile)
                    }
                    return true
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                return true
            }
        }
        return false
    }

    /**
     * Deletes a file/directory by first moving it to a temporary location then deleting it.
     * This avoids an issue with FAT32 on some devices where you cannot create a file
     * with the same name right after deleting it
     */
    fun safeDelete(file: File?) {
        if (file != null && file.exists()) {
            val temp = File(file.parentFile, "${System.currentTimeMillis()}.trash")
            file.renameTo(temp)
            if (file.isDirectory) {
                moveOrCopyQuietly(file, File(temp, file.name))
            } else {
                moveOrCopyQuietly(file, temp)
            }
            deleteQuietly(file) // just in case the move failed
            deleteQuietly(temp)
        }
    }

    @Throws(IOException::class)
    fun copyDirectory(srcDir: File, destDir: File, filter: FileFilter?) {
        require(srcDir.exists()) { "Source '$srcDir' does not exist" }
        require(srcDir.isDirectory) { "Source '$srcDir' exists but is not a directory" }
        require(srcDir.canonicalPath != destDir.canonicalPath) { "Source '$srcDir' and destination '$destDir' are the same" }

        var exclusionList: MutableList<String>? = null
        if (destDir.canonicalPath.startsWith(srcDir.canonicalPath)) {
            val srcFiles = filter?.let { srcDir.listFiles(it) } ?: srcDir.listFiles()
            if (!srcFiles.isNullOrEmpty()) {
                exclusionList = ArrayList(srcFiles.size)
                for (srcFile in srcFiles) {
                    val copiedFile = File(destDir, srcFile.name)
                    exclusionList.add(copiedFile.canonicalPath)
                }
            }
        }
        doCopyDirectory(srcDir, destDir, filter, exclusionList)
    }

    @Throws(IOException::class)
    private fun doCopyDirectory(srcDir: File, destDir: File, filter: FileFilter?, exclusionList: List<String>?) {
        val srcFiles = filter?.let { srcDir.listFiles(it) } ?: srcDir.listFiles()
        ?: throw IOException("Failed to list contents of $srcDir")

        if (destDir.exists()) {
            if (!destDir.isDirectory) {
                throw IOException("Destination '$destDir' exists but is not a directory")
            }
        } else if (!destDir.mkdirs() && !destDir.isDirectory) {
            throw IOException("Destination '$destDir' directory cannot be created")
        }

        if (!destDir.canWrite()) {
            throw IOException("Destination '$destDir' cannot be written to")
        } else {
            for (srcFile in srcFiles) {
                val dstFile = File(destDir, srcFile.name)
                if (exclusionList == null || !exclusionList.contains(srcFile.canonicalPath)) {
                    if (srcFile.isDirectory) {
                        doCopyDirectory(srcFile, dstFile, filter, exclusionList)
                    } else {
                        doCopyFile(srcFile, dstFile)
                    }
                }
            }
            // preserve date
            destDir.setLastModified(srcDir.lastModified())
        }
    }

    /**
     * Copies a file or directory
     */
    @Throws(IOException::class)
    fun copyFile(srcFile: File, destFile: File) {
        require(srcFile.exists()) { "Source '$srcFile' does not exist" }
        require(!srcFile.isDirectory) { "Source '$srcFile' exists but is a directory" }
        require(srcFile.canonicalPath != destFile.canonicalPath) { "Source '$srcFile' and destination '$destFile' are the same" }

        val parentFile = destFile.parentFile
        if (parentFile != null && !parentFile.mkdirs() && !parentFile.isDirectory) {
            throw IOException("Destination '$parentFile' directory cannot be created")
        } else if (destFile.exists() && !destFile.canWrite()) {
            throw IOException("Destination '$destFile' exists but is read-only")
        } else {
            doCopyFile(srcFile, destFile)
        }
    }

    @Throws(IOException::class)
    private fun doCopyFile(srcFile: File, destFile: File) {
        if (destFile.exists() && destFile.isDirectory) {
            throw IOException("Destination '$destFile' exists but is a directory")
        }

        FileInputStream(srcFile).use { fis ->
            FileOutputStream(destFile).use { fos ->
                val input: FileChannel = fis.channel
                val output: FileChannel = fos.channel
                val size = input.size()
                var pos = 0L

                while (pos < size) {
                    var count = size - pos
                    if (count > 31457280L) count = 31457280L
                    pos += output.transferFrom(input, pos, count)
                }
            }
        }

        if (srcFile.length() != destFile.length()) {
            throw IOException("Failed to copy full contents from '$srcFile' to '$destFile'")
        } else {
            // preserve date
            destFile.setLastModified(srcFile.lastModified())
        }
    }

    /**
     * closes the closable without throwing an exception
     */
    fun closeQuietly(closable: Closeable?) {
        try {
            closable?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Throws(IOException::class)
    fun forceMkdir(directory: File) {
        if (directory.exists()) {
            if (!directory.isDirectory) {
                throw IOException("File $directory exists and is not a directory. Unable to create directory.")
            }
        } else if (!directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Unable to create directory $directory")
        }
    }
}