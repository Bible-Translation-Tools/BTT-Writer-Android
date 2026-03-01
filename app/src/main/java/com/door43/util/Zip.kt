package com.door43.util

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * This class handles zipping and unzipping files and directories
 */
object Zip {
    /**
     * Creates a zip archive
     * http://stackoverflow.com/questions/6683600/zip-compress-a-folder-full-of-files-on-android
     * @param sourcePath
     * @param destPath
     * @throws java.io.IOException
     */
    @Throws(IOException::class)
    fun zip(sourcePath: String, destPath: String) {
        val buffer = 2048
        val sourceFile = File(sourcePath)
        val dest = FileOutputStream(destPath)
        val out = ZipOutputStream(BufferedOutputStream(dest))

        if (sourceFile.isDirectory) {
            // TRICKY: we add 1 to the base path length to exclude the leading path separator
            zipSubFolder(out, sourceFile, sourceFile.parent!!.length + 1)
        } else {
            val data = ByteArray(buffer)
            FileInputStream(sourcePath).use { fi ->
                BufferedInputStream(fi, buffer).use { origin ->
                    val segments = sourcePath.split("/".toRegex()).toTypedArray()
                    val lastPathComponent = segments[segments.size - 1]
                    val entry = ZipEntry(lastPathComponent)
                    out.putNextEntry(entry)
                    var count: Int
                    while (origin.read(data, 0, buffer).also { count = it } != -1) {
                        out.write(data, 0, count)
                    }
                }
            }
        }
        out.close()
    }

    /**
     * Adds a file to a zip archive
     * http://stackoverflow.com/questions/3048669/how-can-i-add-entries-to-an-existing-zip-file-in-java
     * @param zipFile
     * @param files
     * @throws IOException
     */
    @Deprecated("")
    @Throws(IOException::class)
    fun addFilesToExistingZip(zipFile: File, files: Array<File>) {
        // get a temp file
        val tempFile = File.createTempFile(zipFile.name, null)
        // delete it, otherwise you cannot rename your existing zip to it.
        tempFile.delete()

        val renameOk = zipFile.renameTo(tempFile)
        if (!renameOk) {
            throw RuntimeException("could not rename the file " + zipFile.absolutePath + " to " + tempFile.absolutePath)
        }
        val buf = ByteArray(1024)

        val zin = ZipInputStream(FileInputStream(tempFile))
        val out = ZipOutputStream(FileOutputStream(zipFile))

        var entry = zin.nextEntry
        while (entry != null) {
            val name = entry.name
            var notInFiles = true
            for (f in files) {
                if (f.name == name) {
                    notInFiles = false
                    break
                }
            }
            if (notInFiles) {
                // Add ZIP entry to output stream.
                out.putNextEntry(ZipEntry(name))
                // Transfer bytes from the ZIP file to the output file
                var len: Int
                while (zin.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
            }
            entry = zin.nextEntry
        }
        // Close the streams
        zin.close()
        // Compress the files
        for (i in files.indices) {
            FileInputStream(files[i]).use { `in` ->
                // Add ZIP entry to output stream.
                out.putNextEntry(ZipEntry(files[i].name))
                // Transfer bytes from the file to the ZIP file
                var len: Int
                while (`in`.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
                // Complete the entry
                out.closeEntry()
            }
        }
        // Complete the ZIP file
        out.close()
        tempFile.delete()
    }

    /**
     * Zips up a list of files to file
     * @param files
     * @param archivePath - destination file
     */
    @Throws(IOException::class)
    fun zip(files: Array<File>, archivePath: File) {
        val dest = FileOutputStream(archivePath)
        zipToStream(files, dest)
    }

    /**
     * Zips up a list of files to output stream
     * @param files
     * @param dest - destination output stream
     */
    @Throws(IOException::class)
    fun zipToStream(files: Array<File>, dest: OutputStream) {
        val buffer = 2048
        val out = ZipOutputStream(BufferedOutputStream(dest))

        for (f in files) {
            if (f.isDirectory) {
                // TRICKY: we add 1 to the base path length to exclude the leading path separator
                zipSubFolder(out, f, f.parent!!.length + 1)
            } else {
                val data = ByteArray(buffer)
                FileInputStream(f).use { fi ->
                    BufferedInputStream(fi, buffer).use { origin ->
                        val segments = f.absolutePath.split("/".toRegex()).toTypedArray()
                        val lastPathComponent = segments[segments.size - 1]
                        val entry = ZipEntry(lastPathComponent)
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(data, 0, buffer).also { count = it } != -1) {
                            out.write(data, 0, count)
                        }
                    }
                }
            }
        }

        out.close()
    }

    /**
     * Zips up a list of files that are mapped to a relative directory in the resulting archive.
     * TODO: we'd like to begin using this so we don't have to organize everything into a directory before zipping
     * @param files
     * @param archivePath
     * @throws IOException
     */
    @Throws(IOException::class)
    fun zip(files: MutableMap<File, String>, archivePath: File) {
        val buffer = 2048
        val dest = FileOutputStream(archivePath)
        val out = ZipOutputStream(BufferedOutputStream(dest))

        val it = files.entries.iterator()
        while (it.hasNext()) {
            val pair = it.next()
            // clean the target path
            pair.setValue(pair.value.replace("^/+".toRegex(), "").replace("/*$".toRegex(), "") + "/")
            if (pair.key.isDirectory) {
                // TRICKY: we add 1 to the base path length to exclude the leading path separator
                zipSubFolder(out, pair.key, pair.value + pair.key.name)
            } else {
                val data = ByteArray(buffer)
                FileInputStream(pair.key).use { fi ->
                    BufferedInputStream(fi, buffer).use { origin ->
                        val entry = ZipEntry(pair.value)
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(data, 0, buffer).also { count = it } != -1) {
                            out.write(data, 0, count)
                        }
                    }
                }
            }
        }

        out.close()
    }

    /**
     * Zips up a sub folder
     * @param out
     * @param folder
     * @param basePathLength
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun zipSubFolder(out: ZipOutputStream, folder: File, basePathLength: Int) {
        val buffer = 2048
        val fileList = folder.listFiles() ?: return // skip empty folders

        for (file in fileList) {
            if (file.isDirectory) {
                zipSubFolder(out, file, basePathLength)
            } else {
                val data = ByteArray(buffer)
                val unmodifiedFilePath = file.path
                val relativePath = unmodifiedFilePath.substring(basePathLength)
                FileInputStream(unmodifiedFilePath).use { fi ->
                    BufferedInputStream(fi, buffer).use { origin ->
                        val entry = ZipEntry(relativePath)
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(data, 0, buffer).also { count = it } != -1) {
                            out.write(data, 0, count)
                        }
                    }
                }
            }
        }
    }

    /**
     * Zips up a sub folder
     * @param out
     * @param folder
     * @param relativePath
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun zipSubFolder(out: ZipOutputStream, folder: File, relativePath: String) {
        val buffer = 2048
        val fileList = folder.listFiles() ?: return

        for (file in fileList) {
            if (file.isDirectory) {
                zipSubFolder(out, file, relativePath + "/" + file.name)
            } else {
                val data = ByteArray(buffer)
                FileInputStream(file.path).use { fi ->
                    BufferedInputStream(fi, buffer).use { origin ->
                        val entry = ZipEntry(relativePath + "/" + file.name)
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(data, 0, buffer).also { count = it } != -1) {
                            out.write(data, 0, count)
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts a zip archive
     * @param zipPath
     * @throws IOException
     */
    @Throws(IOException::class)
    fun unzip(zipPath: String, destPath: String) {
        val buffer = ByteArray(1024)
        val `is` = FileInputStream(zipPath)
        val zis = ZipInputStream(BufferedInputStream(`is`))

        val destDir = File(destPath)
        destDir.mkdirs()

        var ze: ZipEntry? = zis.nextEntry
        while (ze != null) {
            val filename = ze.name
            val f = File(destPath, filename)
            if (ze.isDirectory) {
                f.mkdirs()
                ze = zis.nextEntry
                continue
            }
            f.parentFile?.mkdirs()
            f.createNewFile()
            FileOutputStream(f.absolutePath).use { fout ->
                var count: Int
                while (zis.read(buffer).also { count = it } != -1) {
                    fout.write(buffer, 0, count)
                }
            }
            zis.closeEntry()
            ze = zis.nextEntry
        }
        zis.close()
    }

    /**
     * Extracts a zip archive
     * @param zipArchive
     * @param destDir - place to store unzipped file
     * @throws IOException
     */
    @Throws(IOException::class)
    fun unzip(zipArchive: File, destDir: File) {
        val `is`: InputStream = FileInputStream(zipArchive)
        unzipFromStream(`is`, destDir)
    }

    /**
     * Extracts a zip archive from a stream
     * @param is - input stream of zip file
     * @param destDir - place to store unzipped file
     * @throws IOException
     */
    @Throws(IOException::class)
    fun unzipFromStream(`is`: InputStream, destDir: File) {
        val buffer = ByteArray(1024)
        val zis = ZipInputStream(BufferedInputStream(`is`))

        destDir.mkdirs()

        var ze: ZipEntry? = zis.nextEntry
        while (ze != null) {
            val filename = ze.name
            val f = File(destDir, filename)
            if (ze.isDirectory) {
                f.mkdirs()
                ze = zis.nextEntry
                continue
            }
            f.parentFile?.mkdirs()
            f.createNewFile()
            FileOutputStream(f.absolutePath).use { fout ->
                var count: Int
                while (zis.read(buffer).also { count = it } != -1) {
                    fout.write(buffer, 0, count)
                }
            }
            zis.closeEntry()
            ze = zis.nextEntry
        }
        zis.close()
    }

    /**
     * Lists the contents of the zip file
     * @param zipArchive
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun list(zipArchive: File): Array<String> {
        val `is`: InputStream = FileInputStream(zipArchive)
        val zis = ZipInputStream(BufferedInputStream(`is`))

        val files = ArrayList<String>()
        var ze: ZipEntry? = zis.nextEntry
        while (ze != null) {
            files.add(ze.name)
            if (ze.isDirectory) {
                ze = zis.nextEntry
                continue
            }
            zis.closeEntry()
            ze = zis.nextEntry
        }
        zis.close()
        return files.toTypedArray()
    }

    /**
     * Reads the contents of a file from the zip archive
     * @param zipArchive
     * @param path
     * @return
     */
    @Throws(IOException::class)
    fun read(zipArchive: File, path: String): String? {
        val `is`: InputStream = FileInputStream(zipArchive)
        return readInputStream(`is`, path)
    }

    /**
     * Reads the contents of a file from the zip archive
     * @param zipStream
     * @param path
     * @return
     */
    @Throws(IOException::class)
    fun readInputStream(zipStream: InputStream, path: String): String? {
        var contents: String? = null
        val zis = ZipInputStream(BufferedInputStream(zipStream))

        var ze: ZipEntry? = zis.nextEntry
        while (ze != null) {
            if (ze.isDirectory) {
                ze = zis.nextEntry
                continue
            }
            if (ze.name.equals(path, ignoreCase = true)) {
                // We use standard Reader, but we do not use Kotlin's .useLines or .use{} block here
                // because it would close the ZipInputStream and break the loop.
                val reader = BufferedReader(InputStreamReader(zis))
                val sb = StringBuilder()

                var line: String? = reader.readLine()
                while (line != null) {
                    sb.append(line).append('\n')
                    line = reader.readLine()
                }
                contents = sb.toString()
            }
            zis.closeEntry()
            if (contents != null) {
                break
            }
            ze = zis.nextEntry
        }
        zis.close()
        return contents
    }
}