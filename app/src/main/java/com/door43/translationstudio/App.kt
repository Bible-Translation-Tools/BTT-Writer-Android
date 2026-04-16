package com.door43.translationstudio

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.text.TextUtils
import androidx.preference.PreferenceManager
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.di.appModule
import com.door43.di.prodDataModule
import com.door43.usecases.BackupRC
import com.door43.util.FileUtilities
import com.door43.util.RuntimeWrapper
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.unfoldingword.tools.foreground.Foreground
import org.unfoldingword.tools.logger.LogLevel
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * This class provides global access to the application context as well as other important tools
 */
class App : Application() {

    val prefRepository: IPreferenceRepository by inject()
    val directoryProvider: IDirectoryProvider by inject()
    val backupRC: BackupRC by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.WARNING)
            androidContext(this@App)
            modules(appModule, prodDataModule)
        }

        instance = this
        directory = directoryProvider
        prefs = prefRepository
        backup = backupRC

        Foreground.init(this)

        prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_LOGGING_LEVEL,
            resources.getString(R.string.pref_default_logging_level)
        ).let { minLogLevel ->
            configureLogger(minLogLevel.toInt())
        }

        val dir = File(directoryProvider.externalAppDir, "crashes")
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
    }

    companion object {
        const val TAG: String = "App"
        const val MIN_CHECKING_LEVEL: Int = 3
        // 96 MB, Minimum RAM needed for reliable operation
        const val MINIMUM_REQUIRED_RAM: Long = (96 * 1024 * 1024).toLong()
        // Minimum number of processors needed for reliable operationB
        const val MINIMUM_NUMBER_OF_PROCESSORS: Long = 2

        private lateinit var instance: App
        private lateinit var prefs: IPreferenceRepository
        private lateinit var directory: IDirectoryProvider
        private lateinit var backup: BackupRC

        fun configureLogger(minLogLevel: Int) {
            Logger.configure(directory.logFile, LogLevel.getLevel(minLogLevel))
        }

        val isNetworkAvailable: Boolean
            /**
             * Checks if we have internet
             * @return
             */
            get() {
                val cm = instance.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                val net = cm.activeNetwork ?: return false
                val actNet = cm.getNetworkCapabilities(net) ?: return false
                return when {
                    actNet.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    actNet.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    else -> false
                }
            }

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

        val isStoreVersion: Boolean
            /**
             * Checks if this apk was installed from the playstore or sideloaded
             * @return
             */
            get() {
                val installer = instance.packageManager.getInstallerPackageName(
                    instance.packageName
                )
                return !TextUtils.isEmpty(installer)
            }

        /**
         * Returns the unique device id for this device
         * @return
         */
        @SuppressLint("HardwareIds")
        fun udid(): String {
            return Build.MODEL.lowercase().replace(" ", "_")
        }

        fun restart() {
            val packageName = instance.packageName
            val intent = instance.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                instance.startActivity(Intent.makeRestartActivityTask(intent.component))
                Process.killProcess(Process.myPid())
                RuntimeWrapper.exit(0)
            }
        }
    }
}
