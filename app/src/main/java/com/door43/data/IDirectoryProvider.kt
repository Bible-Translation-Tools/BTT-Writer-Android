package com.door43.data

import java.io.File
import java.io.IOException

interface IDirectoryProvider {

    /**
     * Returns the path to the internal files directory accessible by the app only.
     * This directory is not accessible by other applications and file managers.
     * It's good for storing private data, such as ssh keys.
     * Files saved in this directory will be removed when the application is uninstalled
     */
    val internalAppDir: File

    /**
     * Returns the path to the external files directory accessible by the app only.
     * This directory can be accessed by file managers.
     * It's good for storing user-created data, such as translations and backups.
     * Files saved in this directory will be removed when the application is uninstalled
     */
    val externalAppDir: File

    /**
     * Returns the absolute path to the application specific cache directory on the filesystem.
     */
    val cacheDir: File

    val translationsDir: File

    /**
     * Returns the local translations cache directory.
     * This is where import and export operations can expand files.
     */
    val translationsCacheDir: File

    val databaseDir: File

    /**
     * The index (database) file
     */
    val databaseFile: File

    /**
     * The directory where all source resource containers will be stored
     */
    val containersDir: File

    /**
     * The directory where all backup files will be stored
     */
    val backupsDir: File

    /**
     * Returns the sharing directory
     * @return
     */
    val sharingDir: File

    /**
     * Returns the log file
     */
    val logFile: File

    /**
     * Returns the directory in which the ssh keys are stored
     */
    val sshKeysDir: File

    /**
     * Returns the public key file
     */
    val publicKey: File

    /**
     * Returns the private key file
     */
    val privateKey: File

    /**
     * Returns the directory in which the p2p keys are stored
     */
    val p2pKeysDir: File

    /**
     * Returns the P2P public key file
     */
    val p2pPublicKey: File

    /**
     * Returns the P2P private key file
     */
    val p2pPrivateKey: File

    /**
     * Checks if the ssh keys have already been generated
     * @return Boolean
     */
    fun hasSSHKeys(): Boolean

    /**
     * Generates a new RSA key pair for use with ssh
     */
    fun generateSSHKeys()

    /**
     * Moves an asset into the cache directory and returns a file reference to it
     * @param path
     * @return File
     */
    fun getAssetAsFile(path: String): File?

    /**
     * Deploys the default index and resource containers.
     * @throws Exception
     */
    fun deployDefaultLibrary()

    /**
     * Nuke all the things!
     * ... or just the source content
     */
    fun deleteLibrary()

    /**
     * Creates a temporary directory.
     */
    fun createTempDir(name: String? = null): File

    /**
     * Creates a temporary file.
     * @param prefix Temp file prefix
     * @param suffix Temp file suffix
     * @param dir Directory to create temp file in
     */
    fun createTempFile(prefix: String, suffix: String? = null, dir: File? = null): File

    /**
     * Writes a string to a file
     * @param file
     * @param contents
     * @throws IOException
     */
    @Throws(IOException::class)
    fun writeStringToFile(file: File, contents: String)

    /**
     * Deletes all files in all directories
     * Should not be implemented in production code (too dangerous)
     */
    fun deleteAll()
}