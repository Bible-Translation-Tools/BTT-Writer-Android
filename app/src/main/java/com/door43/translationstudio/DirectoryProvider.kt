package com.door43.translationstudio

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.udid
import com.door43.util.FileUtilities
import com.door43.util.Zip
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

open class DirectoryProvider (private val context: Context) : IDirectoryProvider {

    companion object {
        const val TAG = "DirectoryProvider"
    }

    override val cacheDir: File
        get() = context.cacheDir

    override val internalAppDir: File
        get() = context.filesDir

    override val externalAppDir: File
        get() = context.getExternalFilesDir(null)
            ?: throw NullPointerException("External storage is currently unavailable.")

    override val translationsDir: File
        get() = File(externalAppDir, "translations")

    override val translationsCacheDir: File
        get() = File(translationsDir, "cache")

    override val databaseDir: File
        get() = run {
            val root = externalAppDir.parentFile
            val databaseDir = File(root, "databases")
            if (!databaseDir.exists()) {
                databaseDir.mkdirs()
            }
            databaseDir
        }

    override val databaseFile: File
        get() = File(databaseDir, "index.sqlite")

    override val backupsDir: File
        get() = File(externalAppDir, "backups")

    override val containersDir: File
        get() = File(externalAppDir, "resource_containers")

    override val sharingDir: File
        get() = run {
            val file = File(cacheDir, "sharing")
            file.mkdirs()
            file
        }

    override val logFile: File
        get() = File(externalAppDir, "log.txt")

    override val sshKeysDir: File
        get() = run {
            val dir = File(
                internalAppDir,
                context.resources.getString(R.string.keys_dir)
            )
            if (!dir.exists()) {
                dir.mkdir()
            }
            dir
        }

    override val publicKey: File
        get() = File(sshKeysDir, "id_rsa.pub")

    override val privateKey: File
        get() = File(sshKeysDir, "id_rsa")

    override val p2pKeysDir: File
        get() = run {
            val dir = File(
                internalAppDir,
                context.resources.getString(R.string.p2p_keys_dir)
            )
            if (!dir.exists()) {
                dir.mkdir()
            }
            dir
        }

    override val p2pPublicKey: File
        get() = File(p2pKeysDir, "id_rsa.pub")

    override val p2pPrivateKey: File
        get() = File(p2pKeysDir, "id_rsa")

    override fun hasSSHKeys(): Boolean {
        return privateKey.exists() && publicKey.exists()
    }

    override fun generateSSHKeys() {
        val jsch = JSch()
        val type = KeyPair.RSA

        try {
            val keyPair = KeyPair.genKeyPair(jsch, type)
            File(privateKey.absolutePath).createNewFile()
            keyPair.writePrivateKey(privateKey.absolutePath)
            File(publicKey.absolutePath).createNewFile()
            keyPair.writePublicKey(publicKey.absolutePath, udid())
            keyPair.dispose()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getAssetAsFile(path: String): File? {
        val cacheFile = File(cacheDir, "assets/$path")
        if (!cacheFile.exists()) {
            cacheFile.parentFile?.mkdirs()
            try {
                context.assets.open(path).use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        val buf = ByteArray(1024)
                        var len: Int
                        while ((inputStream.read(buf).also { len = it }) > 0) {
                            outputStream.write(buf, 0, len)
                        }
                    }
                }
            } catch (e: IOException) {
                return null
            }
        }
        return cacheFile
    }

    override fun deployDefaultLibrary() {
        Logger.i(TAG, "Deploying the default library to " + containersDir.parentFile)

        // delete old database first
        FileUtilities.deleteQuietly(databaseFile)

        // copy index
        context.assets.open("index.sqlite").use { inputStream ->
            FileOutputStream(databaseFile).use { outputStream ->
                val buf = ByteArray(1024)
                var len: Int
                while ((inputStream.read(buf).also { len = it }) > 0) {
                    outputStream.write(buf, 0, len)
                }
            }
        }

        // Delete old journal to avoid corrupt database errors
        val shmFile = File(databaseFile.absolutePath + "-shm")
        if (shmFile.exists()) { FileUtilities.deleteQuietly(shmFile) }
        val walFile = File(databaseFile.absolutePath + "-wal")
        if (walFile.exists()) { FileUtilities.deleteQuietly(walFile) }

        // extract resource containers
        containersDir.mkdirs()
        Zip.unzipFromStream(context.assets.open("containers.zip"), containersDir)
    }

    override fun deleteLibrary() {
        FileUtilities.deleteQuietly(databaseFile)
        FileUtilities.deleteQuietly(containersDir)
    }

    override fun createTempDir(name: String?): File {
        val tempName = name ?: System.currentTimeMillis().toString()
        val tempDir = File(cacheDir, tempName)
        tempDir.mkdirs()
        return tempDir
    }

    override fun createTempFile(prefix: String, suffix: String?, dir: File?): File {
        return File.createTempFile(prefix, suffix, dir ?: cacheDir)
    }

    override fun writeStringToFile(file: File, contents: String) {
        FileOutputStream(file).use { fos ->
            fos.write(contents.toByteArray())
        }
    }

    override fun clearCache() {
        cacheDir.listFiles()?.forEach {
            FileUtilities.deleteQuietly(it)
        }
    }
}