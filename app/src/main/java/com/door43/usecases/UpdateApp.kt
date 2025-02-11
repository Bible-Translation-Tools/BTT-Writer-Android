package com.door43.usecases

import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.preference.PreferenceManager
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.getPrivatePref
import com.door43.data.setDefaultPref
import com.door43.data.setPrivatePref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.util.FileUtilities
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import javax.inject.Inject

class UpdateApp @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository,
    private val directoryProvider: IDirectoryProvider,
    private val library: Door43Client,
    private val backupRC: BackupRC,
    private val translator: Translator,
    private val migrator: TargetTranslationMigrator
) {
    private var updateLibrary = true

    fun execute(progressListener: OnProgressListener? = null) {
        var lastVersionCode = prefRepository.getPrivatePref(
            "last_version_code",
            0
        )
        val newInstall = lastVersionCode == 0

        val pInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        }

        pInfo?.let { info ->
            // use current version if fresh install
            lastVersionCode = if (lastVersionCode == 0) info.versionCode else lastVersionCode

            // record latest version
            prefRepository.setPrivatePref("last_version_code", pInfo.versionCode)

            // check if update is possible
            if (info.versionCode > lastVersionCode) {
                performUpdates(lastVersionCode, progressListener)
            } else {
                // update if not deployed or if a fresh install
                updateLibrary = !library.isLibraryDeployed || newInstall
            }
        }

        if (updateLibrary) {
            // preserve manually imported source translations
            val translations = library.index.getImportedTranslations()
            val backupFiles = arrayListOf<File>()
            if (translations.isNotEmpty()) {
                Logger.i("UpdateAppTask", "Backing up imported RCs")
            }
            for (t in translations) {
                try {
                    backupFiles.add(backupRC.backupResourceContainer(t))
                } catch (e: Exception) {
                    Logger.e("UpdateAppTask", "Failed exporting rc " + t.resourceContainerSlug)
                }
            }

            try {
                library.tearDown()
                directoryProvider.deleteLibrary()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                directoryProvider.deployDefaultLibrary()

                // restore backups
                if (backupFiles.size > 0) Logger.i("UpdateAppTask", "Restoring backed up RCs")
                for (f in backupFiles) {
                    // TRICKY: the backup generates closed RCs but the import requires opened RCs.
                    val opened = File("$f.tmp")
                    try {
                        ResourceContainer.open(f, opened)
                        library.importResourceContainer(opened)
                    } catch (importE: java.lang.Exception) {
                        Logger.e("UpdateAppTask", "Failed to restore RC from $f")
                    }
                    FileUtilities.deleteQuietly(opened)
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }

            App.restart()
            return
        }

        updateTargetTranslations()
        updateBuildNumbers()

        // initialize the language url (langnames) from preference
        try {
            val languageUrl = prefRepository.getDefaultPref(
                SettingsActivity.KEY_PREF_LANGUAGES_URL,
                context.resources.getString(R.string.pref_default_language_url)
            )
            library.updateLanguageUrl(languageUrl)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Performs required updates between the two app versions
     * @param lastVersion
     * @param progressListener
     */
    private fun performUpdates(lastVersion: Int, progressListener: OnProgressListener?) {
        // perform migrations
        if (lastVersion < 87) {
            upgradePre87(progressListener)
        }
        if (lastVersion < 103) {
            upgradePre103(progressListener)
        }
        if (lastVersion < 111) {
            upgradePre111()
        }
        if (lastVersion < 122) {
            Looper.prepare()
            PreferenceManager.setDefaultValues(context, R.xml.general_preferences, true)
        }
        if (lastVersion < 139) {
            // TRICKY: this was the old name of the database
            context.deleteDatabase("app")
        }
        if (lastVersion < 142) {
            // TRICKY: this was another old name of the database
            context.deleteDatabase("library")
        }

        if (lastVersion < 175) {
            upgradePre175(progressListener)
        }

        // this should always be the latest version in which the library was updated
        if (lastVersion >= 174) {
            // TRICKY: the default is to always update
            updateLibrary = false
        }
    }

    /**
     * Updates the target translations
     * NOTE: we used to do this manually but now we run this every time so we don't have to manually
     * add a new migration path each time
     */
    private fun updateTargetTranslations() {
        // TRICKY: we manually list the target translations because they won't be viewable until updated
        val translatorDir: File = translator.path
        val dirs = translatorDir.listFiles { pathname ->
            pathname.isDirectory && pathname.name != "cache"
        }
        if (dirs != null) {
            for (tt in dirs) {
                Logger.i(
                    this.javaClass.simpleName,
                    "Migrating: $tt"
                )
                if (migrator.migrate(tt) == null) {
                    Logger.w(
                        this.javaClass.name,
                        "Failed to migrate the target translation " + tt.name
                    )
                }
            }
        }

        // commit migration changes
        for (tt in translator.targetTranslations) {
            try {
                tt.unlockRepo() // TRICKY: prune dangling locks
                tt.commitSync()
            } catch (e: java.lang.Exception) {
                Logger.e(
                    this.javaClass.name,
                    "Failed to commit migration changes to target translation " + tt.id
                )
            }
        }
    }

    /**
     * Updates the generator information for the target translations
     */
    private fun updateBuildNumbers() {
        for (tt in translator.targetTranslations) {
            try {
                TargetTranslation.updateGenerator(context, tt)
            } catch (e: java.lang.Exception) {
                Logger.e(
                    this.javaClass.name,
                    "Failed to update the generator in the target translation " + tt.id
                )
            }
        }
    }

    /**
     * We moved the target translations to the public files directory so that they persist when the
     * app is uninstalled
     */
    private fun upgradePre111() {
        val legacyTranslationsDir = File(directoryProvider.internalAppDir, "translations")
        val translationsDir = translator.path

        if (legacyTranslationsDir.exists()) {
            translationsDir.mkdirs()
            val oldFiles = legacyTranslationsDir.listFiles()
            var errors = false
            for (file in oldFiles) {
                val newFile = File(translationsDir, file.name)
                try {
                    if (file.isDirectory) {
                        FileUtilities.copyDirectory(file, newFile, null)
                    } else {
                        FileUtilities.copyFile(file, newFile)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                    Logger.e(this.javaClass.name, "Failed to move targetTranslation", e)
                    errors = true
                }
            }
            // remove old files if there were no errors
            if (!errors) {
                FileUtilities.deleteQuietly(legacyTranslationsDir)
            }
        }
    }

    /**
     * Major changes.
     * Moved to the new object management system.
     */
    private fun upgradePre103(progressListener: OnProgressListener?) {
        progressListener?.onProgress(-1, 100, "Updating translations")
        Logger.i(this.javaClass.name, "Upgrading source data management from pre 103")

        // migrate target translations and profile
        val oldTranslationsDir = File(directoryProvider.internalAppDir, "git")
        val newTranslationsDir: File = translator.path
        newTranslationsDir.mkdirs()
        val oldProfileDir = File(oldTranslationsDir, "profile")
        val newProfileDir = File(directoryProvider.internalAppDir, "profiles/profile")
        newProfileDir.parentFile?.mkdirs()
        if (oldProfileDir.exists()) {
            FileUtilities.deleteQuietly(newProfileDir)
            FileUtilities.moveOrCopyQuietly(oldProfileDir, newProfileDir)
        }
        if (oldTranslationsDir.exists() && !oldTranslationsDir.list().isNullOrEmpty()) {
            FileUtilities.deleteQuietly(newTranslationsDir)
            FileUtilities.moveOrCopyQuietly(oldTranslationsDir, newTranslationsDir)
        } else if (oldTranslationsDir.exists()) {
            FileUtilities.deleteQuietly(oldTranslationsDir)
        }

        // remove old source
        val oldSourceDir = File(directoryProvider.internalAppDir, "assets")
        val oldTempSourceDir = File(directoryProvider.cacheDir, "assets")
        val oldIndexDir = File(directoryProvider.cacheDir, "index")
        FileUtilities.deleteQuietly(oldSourceDir)
        FileUtilities.deleteQuietly(oldTempSourceDir)
        FileUtilities.deleteQuietly(oldIndexDir)

        // remove old caches
        val oldP2PDir = File(context.externalCacheDir, "transferred")
        val oldExportDir = File(directoryProvider.cacheDir, "exported")
        val oldImportDir = File(directoryProvider.cacheDir, "imported")
        val oldSharingDir = File(directoryProvider.cacheDir, "sharing")
        FileUtilities.deleteQuietly(oldP2PDir)
        FileUtilities.deleteQuietly(oldExportDir)
        FileUtilities.deleteQuietly(oldImportDir)
        FileUtilities.deleteQuietly(oldSharingDir)

        // clear old logs and crash reports
        Logger.flush()
    }

    /**
     * Change default font to noto because most of the others do not work
     */
    private fun upgradePre87(progressListener: OnProgressListener?) {
        progressListener?.onProgress(-1, 100, "Updating fonts")
        Logger.i(this.javaClass.name, "Upgrading fonts from pre 87")

        prefRepository.setDefaultPref(
            SettingsActivity.KEY_PREF_TRANSLATION_TYPEFACE,
            context.getString(R.string.pref_default_translation_typeface)
        )
    }

    /**
     * "NotoSans-Regular.ttf" font has been removed, replace with new default font
     */
    private fun upgradePre175(progressListener: OnProgressListener?) {
        progressListener?.onProgress(-1, 100, "Updating fonts")
        Logger.i(this.javaClass.name, "Upgrading fonts from pre 175")
        // this has been removed, replace with new default font
        val oldDefault = "NotoSans-Regular.ttf"

        var fontName = prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_TRANSLATION_TYPEFACE,
            context.getString(R.string.pref_default_translation_typeface)
        )
        if (oldDefault.equals(fontName, ignoreCase = true)) {
            prefRepository.setDefaultPref(
                SettingsActivity.KEY_PREF_TRANSLATION_TYPEFACE,
                context.getString(R.string.pref_default_translation_typeface)
            )
        }

        fontName = prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_SOURCE_TYPEFACE,
            context.getString(R.string.pref_default_translation_typeface)
        )
        if (oldDefault.equals(fontName, ignoreCase = true)) {
            prefRepository.setDefaultPref(
                SettingsActivity.KEY_PREF_SOURCE_TYPEFACE,
                context.getString(R.string.pref_default_translation_typeface)
            )
        }
    }
}