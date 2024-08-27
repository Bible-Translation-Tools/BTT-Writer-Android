package com.door43.translationstudio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.door43.translationstudio.core.ArchiveDetails
import com.door43.translationstudio.core.Migration
import com.door43.translationstudio.core.NewLanguageRequest
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Util
import com.door43.translationstudio.services.BackupService
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.util.FileUtilities
import com.door43.util.StringUtilities
import com.door43.util.Zip
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.foreground.Foreground
import org.unfoldingword.tools.logger.LogLevel
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.NetworkCapabilities

/**
 * This class provides global access to the application context as well as other important tools
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        sInstance = this

        Foreground.init(this)

        userPreferences.getString(
            SettingsActivity.KEY_PREF_LOGGING_LEVEL,
            resources.getString(R.string.pref_default_logging_level)
        )?.let { minLogLevel ->
            configureLogger(minLogLevel.toInt())
        }

        val dir = File(externalAppDir(), "crashes")
        if (!dir.exists()) {
            try {
                FileUtilities.forceMkdir(dir)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        Logger.registerGlobalExceptionHandler(dir)

        // initialize default settings
        // NOTE: make sure to add any new preference files here in order to have their default values properly loaded.
        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.server_preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.sharing_preferences, false)
        PreferenceManager.setDefaultValues(this, R.xml.advanced_preferences, false)

        val defaultColorTheme = resources.getString(R.string.pref_default_color_theme)
        val colorTheme = getPref(SettingsActivity.KEY_PREF_COLOR_THEME, defaultColorTheme)
        updateColorTheme(colorTheme)
    }

    companion object {
        const val PREFERENCES_TAG: String = "com.door43.translationstudio"
        const val EXTRA_SOURCE_DRAFT_TRANSLATION_ID: String = "extra_source_translation_id"
        const val EXTRA_TARGET_TRANSLATION_ID: String = "extra_target_translation_id"
        const val EXTRA_START_WITH_MERGE_FILTER: String = "start_with_merge_filter"
        const val EXTRA_CHAPTER_ID: String = "extra_chapter_id"
        const val EXTRA_FRAME_ID: String = "extra_frame_id"
        const val EXTRA_VIEW_MODE: String = "extra_view_mode_id"
        const val PUBLIC_DATA_DIR: String = "BTT-Writer"
        const val TAG: String = "App"

        private const val PREFERENCES_NAME = "com.door43.translationstudio.general"

        //    private static final String DEFAULT_LIBRARY_ZIP = "library.zip";
        private const val LAST_VIEW_MODE = "last_view_mode_"
        private const val LAST_FOCUS_CHAPTER = "last_focus_chapter_"
        private const val LAST_FOCUS_FRAME = "last_focus_frame_"
        private const val OPEN_SOURCE_TRANSLATIONS = "open_source_translations_"
        private const val SELECTED_SOURCE_TRANSLATION = "selected_source_translation_"
        private const val LAST_CHECKED_SERVER_FOR_UPDATES = "last_checked_server_for_updates"

        //    private static final String ASSETS_DIR = "assets";
        const val MIN_CHECKING_LEVEL: Int = 3
        private var mImageLoader: ImageLoader? = null
        private var sInstance: App? = null
        /**
         * for keeping track of project that has changed
         * @param targetTranslationId
         */
        //@JvmStatic
        //@JvmField
        var notifyTargetTranslationWithUpdates: String? = null
        val imagesDir: File? = null
        // 96 MB, Minimum RAM needed for reliable operation
        const val MINIMUM_REQUIRED_RAM: Long = (96 * 1024 * 1024).toLong()
        // Minimum number of processors needed for reliable operationB
        const val MINIMUM_NUMBER_OF_PROCESSORS: Long = 2
        private var mBackupsRunning = false

        /**
         * Starts the backup service if it is not already running.
         */
        @JvmStatic
        fun startBackupService() {
            if (!mBackupsRunning) {
                mBackupsRunning = true
                val backupIntent = Intent(context(), BackupService::class.java)
                context()?.startService(backupIntent)
            }
        }

        @JvmStatic
        fun configureLogger(minLogLevel: Int) {
            Logger.configure(File(externalAppDir(), "log.txt"), LogLevel.getLevel(minLogLevel))
        }

        @JvmStatic
        val userPreferences: SharedPreferences
            /**
             * Returns an instance of the user preferences.
             * This is just the default shared preferences
             * @return
             */
            get() = PreferenceManager.getDefaultSharedPreferences(sInstance!!)

        @JvmStatic
        val isNetworkAvailable: Boolean
            /**
             * Checks if we have internet
             * @return
             */
            get() {
                val cm = sInstance!!.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val net = cm.activeNetwork ?: return false
                    val actNet = cm.getNetworkCapabilities(net) ?: return false
                    return when {
                        actNet.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                        actNet.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                        else -> false
                    }
                } else {
                    val activeNetwork = cm.activeNetworkInfo
                    return activeNetwork != null && activeNetwork.isConnectedOrConnecting
                }
            }

        @JvmStatic
        val deviceLanguageCode: String
            /**
             * Returns the language code used by the device.
             * This will trim off dangling special characters
             * @return
             */
            get() {
                val code = Locale.getDefault().language
                return code.replace("[_-]$".toRegex(), "")
            }

        /**
         * A temporary utility to retrieve the target language used in a target translation.
         * if the language does not exist it will be added as a temporary language if possible
         * @param t
         * @return
         */
        @JvmStatic
        @Deprecated("")
        fun languageFromTargetTranslation(t: TargetTranslation): TargetLanguage? {
            val library = library
            var language = library!!.index.getTargetLanguage(t.targetLanguageId)
            if (language == null && t.targetLanguageId.isEmpty()) {
                val name = t.targetLanguageName.ifEmpty { t.targetLanguageId }
                val direction =
                    if (t.targetLanguageDirection == null) "ltr" else t.targetLanguageDirection
                language = TargetLanguage(
                    t.targetLanguageId,
                    name,
                    "",
                    direction,
                    "unknown",
                    false
                )
                try {
                    library.index.addTempTargetLanguage(language)
                } catch (e: Exception) {
                    language = null
                    e.printStackTrace()
                }
            }
            return language
        }

        /**
         * Generates a new RSA key pair for use with ssh
         */
        @JvmStatic
        fun generateSSHKeys() {
            val jsch = JSch()
            val type = KeyPair.RSA
            val keysDir = keysFolder
            val privateKeyPath = keysDir.absolutePath + "/id_rsa"
            val publicKeyPath = keysDir.absolutePath + "/id_rsa.pub"

            try {
                val keyPair = KeyPair.genKeyPair(jsch, type)
                File(privateKeyPath).createNewFile()
                keyPair.writePrivateKey(privateKeyPath)
                File(publicKeyPath).createNewFile()
                keyPair.writePublicKey(publicKeyPath, udid())
                keyPair.dispose()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        @JvmStatic
        val publicKey: File
            /**
             * Returns the public key file
             * @return
             */
            get() {
                val keysDir = keysFolder
                return File(keysDir.absolutePath + "/id_rsa.pub")
            }

        val privateKey: File
            /**
             * Returns the private key file
             * @return
             */
            get() {
                val keysDir = keysFolder
                return File(keysDir.absolutePath + "/id_rsa")
            }

        /**
         * Checks if the ssh keys have already been generated
         * @return
         */
        @JvmStatic
        fun hasSSHKeys(): Boolean {
            val keysDir = keysFolder
            val privFile = File(keysDir.absolutePath + "/id_rsa")
            val pubFile = File(keysDir.absolutePath + "/id_rsa.pub")
            return privFile.exists() && pubFile.exists()
        }

        @JvmStatic
        val keysFolder: File
            /**
             * Returns the directory in which the ssh keys are stored
             * @return
             */
            get() {
                val folder = File(
                    internalAppDir(),
                    sInstance!!.resources.getString(R.string.keys_dir)
                )
                if (!folder.exists()) {
                    folder.mkdir()
                }
                return folder
            }

        val imageLoader: ImageLoader?
            /**
             * Generates and returns the image loader
             * @return
             */
            get() {
                if (mImageLoader == null) {
                    val config = ImageLoaderConfiguration.Builder(sInstance).build()
                    mImageLoader = ImageLoader.getInstance().apply {
                        init(config)
                    }
                }
                return mImageLoader
            }

        @JvmStatic
        val isStoreVersion: Boolean
            /**
             * Checks if this apk was installed from the playstore or sideloaded
             * @return
             */
            get() {
                val installer = sInstance!!.packageManager.getInstallerPackageName(
                    sInstance!!.packageName
                )
                return !TextUtils.isEmpty(installer)
            }

        /**
         * Moves an asset into the cache directory and returns a file reference to it
         * @param path
         * @return
         */
        @JvmStatic
        fun getAssetAsFile(path: String): File? {
            // TODO: we probably don't want to do this for everything.
            // think about changing this up a bit.
            // TODO: we need to figure out when the clear out these cached files. Probably just on version bumps.
            return sInstance?.let { context ->
                var cacheFile = File(context.cacheDir, "assets/$path")
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
                cacheFile
            }
        }

        /**
         * Looks up a string preference
         * @param key
         * @return
         */
        @JvmStatic
        fun getPref(key: String?, defaultValue: String?): String? {
            return userPreferences.getString(key, defaultValue)
        }

        /**
         * Looks up a string resource
         * @param id
         * @return
         */
        @JvmStatic
        fun getRes(id: Int): String {
            return sInstance!!.resources.getString(id)
        }

        /**
         * Returns the path to the directory where the database is stored
         * @return
         */
        fun databaseDir(): File {
            val root = sInstance!!.getExternalFilesDir(null)!!.parentFile
            val databaseDir = File(root, "databases")
            if (!databaseDir.exists()) {
                databaseDir.mkdirs()
            }
            return databaseDir
        }

        @JvmStatic
        val library: Door43Client?
            /**
             * Returns an instance of the door43 client
             * @return
             */
            get() {
                try {
                    return Door43Client(sInstance, dbFile(), containersDir())
                } catch (e: IOException) {
                    Logger.e(TAG, "Failed to initialize the door43 client", e)
                }
                return null
            }

        @JvmStatic
        val termsOfUseVersion: Int
            /**
             * Returns the version of the terms of use
             * @return
             */
            get() = sInstance!!.resources.getInteger(R.integer.terms_of_use_version)

        @JvmStatic
        val isTablet: Boolean
            /**
             * Checks if the device is a tablet
             * @return
             */
            get() = ((sInstance!!.resources.configuration.screenLayout
                    and Configuration.SCREENLAYOUT_SIZE_MASK)
                    >= Configuration.SCREENLAYOUT_SIZE_LARGE)

        /**
         * The index (database) file
         * @return
         */
        @JvmStatic
        fun dbFile(): File {
            return File(databaseDir(), "index.sqlite")
        }

        /**
         * The directory where all source resource containers will be stored
         * @return
         */
        private fun containersDir(): File {
            return File(externalAppDir(), "resource_containers")
        }

        /**
         * Deploys the default index and resource containers.
         *
         * @throws Exception
         */
        @JvmStatic
        @Throws(Exception::class)
        fun deployDefaultLibrary() {
            Logger.i(TAG, "deploying the default library to " + containersDir().parentFile)
            // copy index
            Util.writeStream(sInstance!!.assets.open("index.sqlite"), dbFile())
            // extract resource containers
            val dir = containersDir()
            dir.mkdirs()
            Zip.unzipFromStream(sInstance!!.assets.open("containers.zip"), dir)
        }

        @JvmStatic
        val isLibraryDeployed: Boolean
            /**
             * Check if the default index and resource containers have been deployed
             * @return
             */
            get() {
                val hasContainers =
                    containersDir().exists() &&
                            containersDir().isDirectory &&
                            containersDir().list()?.isNotEmpty() == true
                val library = library
                return library != null && library.index.sourceLanguages.size > 0 && hasContainers
            }

        /**
         * Nuke all the things!
         * ... or just the source content
         */
        @JvmStatic
        fun deleteLibrary() {
            try {
                val client = library
                client?.tearDown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            FileUtilities.deleteQuietly(dbFile())
            FileUtilities.deleteQuietly(containersDir())
        }

        @JvmStatic
        val translator: Translator
            /**
             * Returns an instance of the translator.
             * Target translations are stored in the public directory so that they persist if the app is uninstalled.
             * @return
             */
            get() = Translator(sInstance!!, profile, File(externalAppDir(), "translations"))

        /**
         * Returns the main application context
         * @return
         */
        @JvmStatic
        fun context(): App? {
            return sInstance
        }

        /**
         * Returns the unique device id for this device
         * @return
         */
        @SuppressLint("HardwareIds")
        @JvmStatic
        fun udid(): String {
            return Settings.Secure.getString(
                sInstance!!.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        }

        val publicDownloadsDirectory: File
            /**
             * Returns the file to the external public downloads directory
             * @return
             */
            get() {
                // TRICKY: KITKAT introduced changes to the external media that made sd cards read only
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    PUBLIC_DATA_DIR
                )
                dir.mkdirs()
                return dir
            }

        /**
         * Attempts to recover from a corrupt git history.
         *
         * @param t the translation to repair
         * @return
         */
        @JvmStatic
        fun recoverRepo(t: TargetTranslation?): Boolean {
            if (t == null) return false
            Logger.w(TAG, "Recovering repository for " + t.id)
            try {
                val gitDir = File(t.path, ".git")
                if (backupTargetTranslation(t, true)
                    && FileUtilities.deleteQuietly(gitDir)
                ) {
                    t.commitSync(".", false)
                    Logger.i(TAG, "History repaired for " + t.id)
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        /**
         * Backups up the resource container to the download directory
         * @param translation  the translation representing the rc that will be backed up
         * @return the backup file
         * @throws Exception
         */
        @JvmStatic
        @Throws(Exception::class)
        fun backupResourceContainer(translation: Translation): File {
            val dest = File(
                publicDownloadsDirectory,
                translation.resourceContainerSlug + "." + ResourceContainer.fileExtension
            )
            library!!.exportResourceContainer(
                dest,
                translation.language.slug,
                translation.project.slug,
                translation.resource.slug
            )
            return dest
        }

        /**
         * Creates a backup of a target translation in all the right places
         * @param targetTranslation the target translation that will be backed up
         * @param orphaned if true this backup will be orphaned (time stamped)
         * @return true if the backup was actually performed
         */
        @JvmStatic
        @Throws(Exception::class)
        fun backupTargetTranslation(
            targetTranslation: TargetTranslation?,
            orphaned: Boolean
        ): Boolean {
            if (targetTranslation != null && profile != null) {
                var name = targetTranslation.id
                val sdf = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.US)
                if (orphaned) {
                    name += "." + sdf.format(Date())
                }

                var archiveExtension = Translator.TSTUDIO_EXTENSION
                if (orphaned) {
                    archiveExtension = Translator.ZIP_EXTENSION
                }

                // backup locations
                val downloadsBackup = File(publicDownloadsDirectory, "$name.$archiveExtension")
                val publicBackup = File(externalAppDir(), "backups/$name.$archiveExtension")

                // check if we need to backup
                if (!orphaned) {
                    val downloadsDetails =
                        ArchiveDetails.newInstance(downloadsBackup, "en", library)
                    val publicDetails = ArchiveDetails.newInstance(publicBackup, "en", library)
                    // TRICKY: we only generate backups with a single target translation inside.
                    if (getCommitHash(downloadsDetails) == targetTranslation.commitHash && getCommitHash(
                            publicDetails
                        ) == targetTranslation.commitHash
                    ) {
                        return false
                    }
                }

                // run backup
                var temp: File? = null
                try {
                    temp = File.createTempFile(name, ".$archiveExtension")
                    targetTranslation.setDefaultContributor(profile!!.nativeSpeaker)
                    translator.exportArchive(targetTranslation, temp)
                    if (temp.exists() && temp.isFile) {
                        // copy into backup locations
                        downloadsBackup.parentFile.mkdirs()
                        publicBackup.parentFile.mkdirs()

                        FileUtilities.copyFile(temp, downloadsBackup)
                        FileUtilities.copyFile(temp, publicBackup)
                        return true
                    }
                } finally {
                    FileUtilities.deleteQuietly(temp)
                }
            }
            return false
        }

        /**
         * Creates a backup of a project directory in all the right places
         * @param projectDir the project directory that will be backed up
         * @return true if the backup was actually performed
         */
        @JvmStatic
        @Throws(Exception::class)
        fun backupTargetTranslation(projectDir: File): Boolean {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.US)
            val name = projectDir.name + "." + sdf.format(Date())


            // backup locations
            val downloadsBackup =
                File(publicDownloadsDirectory, name + "." + Translator.ZIP_EXTENSION)
            val publicBackup =
                File(externalAppDir(), "backups/" + name + "." + Translator.ZIP_EXTENSION)

            // run backup
            var temp: File? = null
            try {
                temp = File.createTempFile(name, "." + Translator.ZIP_EXTENSION)
                translator.exportArchive(projectDir, temp)
                if (temp.exists() && temp.isFile) {
                    // copy into backup locations
                    downloadsBackup.parentFile.mkdirs()
                    publicBackup.parentFile.mkdirs()

                    FileUtilities.copyFile(temp, downloadsBackup)
                    FileUtilities.copyFile(temp, publicBackup)
                    return true
                }
            } finally {
                FileUtilities.deleteQuietly(temp)
            }

            return false
        }

        /**
         * safe fetch of commit hash
         * @param details
         * @return
         */
        private fun getCommitHash(details: ArchiveDetails?): String? {
            var commitHash: String? = null
            commitHash =
                if ((details == null) || (details.targetTranslationDetails.size == 0)) {
                    "" // will not match existing commit hash
                } else {
                    details.targetTranslationDetails[0].commitHash
                }
            return commitHash
        }

        /**
         * Returns the path to the external files directory accessible by the app only.
         * This directory can be accessed by file managers.
         * It's good for storing user-created data, such as translations and backups.
         * Files saved in this directory will be removed when the application is uninstalled
         *
         * @return
         */
        @JvmStatic
        fun externalAppDir(): File? {
            return sInstance!!.getExternalFilesDir(null)
        }

        /**
         * Returns the path to the internal files directory accessible by the app only.
         * This directory is not accessible by other applications and file managers.
         * It's good to store private data, such as ssh keys.
         * Files saved in this directory will be removed when the application is uninstalled
         *
         * @return
         */
        fun internalAppDir(): File {
            return sInstance!!.filesDir
        }

        /**
         * Sets the last opened view mode for a target translation
         * @param targetTranslationId
         * @param viewMode
         */
        @JvmStatic
        fun setLastViewMode(targetTranslationId: String, viewMode: TranslationViewMode) {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString(
                LAST_VIEW_MODE + targetTranslationId,
                viewMode.name.uppercase(Locale.getDefault())
            )
            editor.apply()
        }

        /**
         * Returns the last view mode of the target translation.
         * The default view mode will be returned if there is no recorded last view mode
         *
         * @param targetTranslationId
         * @return
         */
        @JvmStatic
        fun getLastViewMode(targetTranslationId: String): TranslationViewMode {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            try {
                val modeName = prefs.getString(
                    LAST_VIEW_MODE + targetTranslationId,
                    TranslationViewMode.READ.name
                )
                return TranslationViewMode.valueOf(modeName!!.uppercase(Locale.getDefault()))
            } catch (e: Exception) {
            }
            return TranslationViewMode.READ
        }

        @JvmStatic
        var lastFocusTargetTranslation: String?
            /**
             * Returns the last focused target translation
             * @return
             */
            get() {
                val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                return prefs.getString("last_translation", null)
            }
            /**
             * Sets the last focused target translation
             * @param targetTranslationId
             */
            set(targetTranslationId) {
                val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("last_translation", targetTranslationId)
                editor.apply()
            }


        /**
         * Sets the last focused chapter and frame for a target translation
         * @param targetTranslationId
         * @param chapterId
         * @param frameId
         */
        @JvmStatic
        fun setLastFocus(targetTranslationId: String, chapterId: String?, frameId: String?) {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            val editor = prefs.edit()
            editor.putString(LAST_FOCUS_CHAPTER + targetTranslationId, chapterId)
            editor.putString(LAST_FOCUS_FRAME + targetTranslationId, frameId)
            editor.apply()
            lastFocusTargetTranslation = targetTranslationId
        }

        /**
         * Returns the id of the chapter that was last in focus for this target translation
         * @param targetTranslationId
         * @return
         */
        @JvmStatic
        fun getLastFocusChapterId(targetTranslationId: String): String? {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            return prefs.getString(LAST_FOCUS_CHAPTER + targetTranslationId, null)
        }

        /**
         * Returns the id of the frame that was last in focus for this target translation
         * @param targetTranslationId
         * @return
         */
        @JvmStatic
        fun getLastFocusFrameId(targetTranslationId: String): String? {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            return prefs.getString(LAST_FOCUS_FRAME + targetTranslationId, null)
        }

        /**
         * Adds a source translation to the list of open tabs on a target translation
         * @param targetTranslationId
         * @param sourceTranslationId
         */
        @JvmStatic
        fun addOpenSourceTranslation(targetTranslationId: String, sourceTranslationId: String?) {
            if (!sourceTranslationId.isNullOrEmpty()) {
                val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                val editor = prefs.edit()
                val sourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
                var newIdSet: String? = ""
                for (id in sourceTranslationIds) {
                    if (id != sourceTranslationId) {
                        newIdSet += "$id|"
                    }
                }
                newIdSet += sourceTranslationId
                editor.putString(OPEN_SOURCE_TRANSLATIONS + targetTranslationId, newIdSet)
                editor.apply()
            }
        }

        /**
         * Removes a source translation from the list of open tabs on a target translation
         * @param targetTranslationId
         * @param sourceTranslationId
         */
        @JvmStatic
        fun removeOpenSourceTranslation(targetTranslationId: String, sourceTranslationId: String?) {
            if (!sourceTranslationId.isNullOrEmpty()) {
                val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                val editor = prefs.edit()
                val sourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
                var newIdSet: String? = ""
                for (id in sourceTranslationIds) {
                    if (id != sourceTranslationId) {
                        if (newIdSet!!.isEmpty()) {
                            newIdSet = id
                        } else {
                            newIdSet += "|$id"
                        }
                    } else if (id == getSelectedSourceTranslationId(targetTranslationId)) {
                        // unset the selected tab if it is removed
                        setSelectedSourceTranslation(targetTranslationId, null)
                    }
                }
                editor.putString(OPEN_SOURCE_TRANSLATIONS + targetTranslationId, newIdSet)
                editor.apply()
            }
        }

        /**
         * Returns an array of open source translation tabs on a target translation
         * @param targetTranslationId
         * @return
         */
        @JvmStatic
        fun getOpenSourceTranslations(targetTranslationId: String): Array<String?> {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            val idSet = prefs.getString(OPEN_SOURCE_TRANSLATIONS + targetTranslationId, "")!!
                .trim { it <= ' ' }
            if (idSet.isEmpty()) {
                return arrayOfNulls(0)
            } else {
                val ids: Array<String?> =
                    idSet.split("\\|".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                for (i in ids.indices) {
                    ids[i] = Migration.migrateSourceTranslationSlug(ids[i])
                }
                return ids
            }
        }

        /**
         * Sets or removes the selected open source translation tab on a target translation
         * @param targetTranslationId
         * @param sourceTranslationId if null the selection will be unset
         */
        @JvmStatic
        fun setSelectedSourceTranslation(
            targetTranslationId: String,
            sourceTranslationId: String?
        ) {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            val editor = prefs.edit()
            if (!sourceTranslationId.isNullOrEmpty()) {
                editor.putString(
                    SELECTED_SOURCE_TRANSLATION + targetTranslationId,
                    Migration.migrateSourceTranslationSlug(sourceTranslationId)
                )
            } else {
                editor.remove(SELECTED_SOURCE_TRANSLATION + targetTranslationId)
            }
            editor.apply()
        }

        /**
         * Returns the selected open source translation tab on the target translation
         * If there is no selection the first open tab will be set as the selected tab
         * @param targetTranslationId
         * @return
         */
        @JvmStatic
        fun getSelectedSourceTranslationId(targetTranslationId: String): String {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            var selectedSourceTranslationId =
                prefs.getString(SELECTED_SOURCE_TRANSLATION + targetTranslationId, null)
            if (selectedSourceTranslationId.isNullOrEmpty()) {
                // default to first tab
                val openSourceTranslationIds = getOpenSourceTranslations(targetTranslationId)
                if (openSourceTranslationIds.isNotEmpty()) {
                    selectedSourceTranslationId = openSourceTranslationIds[0]
                    setSelectedSourceTranslation(targetTranslationId, selectedSourceTranslationId)
                }
            }
            return Migration.migrateSourceTranslationSlug(selectedSourceTranslationId)
        }

        /**
         * Removes all settings for a target translation
         * @param targetTranslationId
         */
        @JvmStatic
        fun clearTargetTranslationSettings(targetTranslationId: String) {
            val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            val editor = prefs.edit()
            editor.remove(SELECTED_SOURCE_TRANSLATION + targetTranslationId)
            editor.remove(OPEN_SOURCE_TRANSLATIONS + targetTranslationId)
            editor.remove(LAST_FOCUS_FRAME + targetTranslationId)
            editor.remove(LAST_FOCUS_CHAPTER + targetTranslationId)
            editor.remove(LAST_VIEW_MODE + targetTranslationId)
            editor.apply()
        }

        val mediaServer: String
            /**
             * Returns the address for the media server
             * @return
             */
            get() {
                val url = userPreferences.getString(
                    SettingsActivity.KEY_PREF_MEDIA_SERVER, context()!!
                        .resources.getString(R.string.pref_default_media_server)
                )
                return StringUtilities.ltrim(url, '/')
            }

        @JvmStatic
        val sharingDir: File
            /**
             * Returns the sharing directory
             * @return
             */
            get() {
                val file = File(sInstance!!.cacheDir, "sharing")
                file.mkdirs()
                return file
            }

        var lastCheckedForUpdates: Long
            /**
             * Returns the last time we checked the server for updates
             * @return
             */
            get() {
                val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                return prefs.getLong(LAST_CHECKED_SERVER_FOR_UPDATES, 0L)
            }
            /**
             * Sets the last time we checked the server for updates to the library
             * @param timeMillis
             */
            set(timeMillis) {
                val prefs = sInstance!!.getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putLong(LAST_CHECKED_SERVER_FOR_UPDATES, timeMillis)
                editor.apply()
            }

        @JvmStatic
        var deviceNetworkAlias: String?
            /**
             * Returns the alias to be displayed when others see this device on the network
             * @return
             */
            get() {
                val name = getUserString(SettingsActivity.KEY_PREF_DEVICE_ALIAS, "")
                return if (name!!.isEmpty()) {
                    null
                } else {
                    name
                }
            }
            /**
             * Sets the alias to be displayed when others see this device on the network
             * @param alias
             */
            set(alias) {
                var a = alias
                if (a!!.trim { it <= ' ' }.isEmpty()) {
                    a = null
                }
                setUserString(SettingsActivity.KEY_PREF_DEVICE_ALIAS, a)
            }

        @JvmStatic
        var profile: Profile?
            /**
             * Returns the current user profile
             * @return
             */
            get() {
                val profileString = getUserString("profile", null)

                try {
                    if (profileString != null) {
                        return Profile.fromJSON(JSONObject(profileString))
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to parse the profile", e)
                }
                return null
            }
            /**
             * Stores the user profile
             *
             * @param profile
             */
            set(profile) {
                try {
                    if (profile != null) {
                        val profileString = profile.toJSON().toString()
                        setUserString("profile", profileString)
                    } else {
                        setUserString("profile", null)
                        FileUtilities.deleteQuietly(keysFolder)
                    }
                } catch (e: JSONException) {
                    Logger.e(TAG, "setProfile: Failed to encode profile data", e)
                }
            }

        /**
         * Returns the string value of a user preference or the default value
         * @param preferenceKey
         * @param defaultResource
         * @return
         */
        @JvmStatic
        fun getUserString(preferenceKey: String?, defaultResource: Int): String? {
            return getUserString(preferenceKey, sInstance!!.resources.getString(defaultResource))
        }

        /**
         * shows the keyboard in the given activity and view
         * @param activity
         * @param view
         */
        @JvmStatic
        fun showKeyboard(activity: Activity?, view: View?, forced: Boolean) {
            if (activity != null) {
                if (activity.currentFocus != null) {
                    try {
                        val mgr =
                            activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        if (forced) {
                            mgr.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                        } else {
                            mgr.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                        }
                    } catch (e: Exception) {
                    }
                } else {
                    try {
                        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        /**
         * Closes the keyboard in the given activity
         * @param activity
         */
        @JvmStatic
        fun closeKeyboard(activity: Activity?) {
            if (activity != null) {
                if (activity.currentFocus != null) {
                    try {
                        val imm =
                            activity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(activity.currentFocus!!.windowToken, 0)
                    } catch (e: Exception) {
                    }
                } else {
                    try {
                        activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        /**
         * Closes the keyboard for view that has focus, need to use this in dialog fragments
         * @param context
         * @param view - value that has focus (usually EditText)
         */
        @JvmStatic
        fun closeKeyboard(context: Context?, view: View?) {
            if ((view != null) && (context != null)) {
                try {
                    val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                } catch (e: Exception) {
                }
            }
        }

        /**
         * Returns the string value of a user preference or the default value
         * @param preferenceKey
         * @param defaultValue
         * @return
         */
        @JvmStatic
        fun getUserString(preferenceKey: String?, defaultValue: String?): String? {
            return userPreferences.getString(preferenceKey, defaultValue)
        }

        /**
         * Sets the value of a user string.
         * @param preferenceKey
         * @param value if null the string will be removed
         */
        @JvmStatic
        fun setUserString(preferenceKey: String?, value: String?) {
            val editor = userPreferences.edit()
            if (value == null) {
                editor.remove(preferenceKey)
            } else {
                editor.putString(preferenceKey, value)
            }
            editor.apply()
        }

        /**
         * Returns the new language request if it exists
         * @param languageCode
         * @return
         */
        @JvmStatic
        fun getNewLanguageRequest(languageCode: String): NewLanguageRequest? {
            val requestFile = File(externalAppDir(), "new_languages/$languageCode.json")
            if (requestFile.exists() && requestFile.isFile) {
                var data: String? = null
                try {
                    data = FileUtilities.readFileToString(requestFile)
                } catch (e: IOException) {
                    Logger.e(App::class.java.name, "Failed to read the new language request", e)
                }
                return NewLanguageRequest.generate(data)
            }
            return null
        }

        @JvmStatic
        val newLanguageRequests: Array<NewLanguageRequest>
            /**
             * Returns an array of new language requests
             * @return
             */
            get() {
                val newLanguagesDir = File(externalAppDir(), "new_languages/")
                val requestFiles = newLanguagesDir.listFiles()
                val requests: MutableList<NewLanguageRequest> = ArrayList()
                if (!requestFiles.isNullOrEmpty()) {
                    for (f in requestFiles) {
                        try {
                            val data = FileUtilities.readFileToString(f)
                            val request = NewLanguageRequest.generate(data)
                            if (request != null) {
                                requests.add(request)
                            }
                        } catch (e: IOException) {
                            Logger.e(
                                App::class.java.name,
                                "Failed to read the language request file",
                                e
                            )
                        }
                    }
                }
                return requests.toTypedArray<NewLanguageRequest>()
            }

        /**
         * Adds a new language request.
         * This stores the request to the data path for later submission
         * and adds the temp language to the library for global use in the app
         * @param request
         */
        @JvmStatic
        fun addNewLanguageRequest(request: NewLanguageRequest?): Boolean {
            if (request != null) {
                val requestFile =
                    File(externalAppDir(), "new_languages/" + request.tempLanguageCode + ".json")
                requestFile.parentFile.mkdirs()
                try {
                    FileUtilities.writeStringToFile(requestFile, request.toJson())
                    return library!!.index.addTempTargetLanguage(request.tempTargetLanguage)
                } catch (e: Exception) {
                    Logger.e(App::class.java.name, "Failed to save the new language request", e)
                }
            }
            return false
        }

        /**
         * Deletes the new language request from the data path
         * @param request
         */
        fun removeNewLanguageRequest(request: NewLanguageRequest?) {
            if (request != null) {
                val requestFile =
                    File(externalAppDir(), "new_languages/" + request.tempLanguageCode + ".json")
                if (requestFile.exists()) {
                    FileUtilities.safeDelete(requestFile)
                }
            }
        }

        @JvmStatic
        fun hasImages(): Boolean {
            return false
        }

        /**
         * Generates a unique temporary directory
         * @return a new directory
         */
        fun makeTempDirectory(): File {
            return File(
                context()!!.externalCacheDir,
                System.currentTimeMillis().toString() + "_tmp"
            )
        }

        fun updateColorTheme(theme: Int) {
            AppCompatDelegate.setDefaultNightMode(theme)
        }

        @JvmStatic
        fun updateColorTheme(theme: String?) {
            updateColorTheme(getColorThemeId(theme))
        }

        @JvmStatic
        fun restart() {
            val packageName = context()!!.packageName
            val intent = context()!!.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context()!!.startActivity(Intent.makeRestartActivityTask(intent.component))
                Process.killProcess(Process.myPid())
                Runtime.getRuntime().exit(0)
            }
        }

        private fun getColorThemeId(theme: String?): Int {
            val colorTheme = when (theme) {
                "Light" -> AppCompatDelegate.MODE_NIGHT_NO
                "Dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            return colorTheme
        }
    }
}
