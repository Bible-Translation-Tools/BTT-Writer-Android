package com.door43.translationstudio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Process
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.di.appModule
import com.door43.translationstudio.ui.SettingsActivity
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
            androidLogger(Level.DEBUG)
            androidContext(this@App)
            modules(appModule)
        }

        instance = this
        directory = directoryProvider
        prefs = prefRepository
        backup = backupRC

        Foreground.init(this)

        prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_LOGGING_LEVEL,
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

        val defaultColorTheme = resources.getString(R.string.pref_default_color_theme)
        val colorTheme = prefRepository.getDefaultPref(SettingsActivity.KEY_PREF_COLOR_THEME, defaultColorTheme)
        updateColorTheme(colorTheme)
    }

    companion object {
        const val PUBLIC_DATA_DIR: String = "BTT-Writer"
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

        @JvmStatic
        fun configureLogger(minLogLevel: Int) {
            Logger.configure(directory.logFile, LogLevel.getLevel(minLogLevel))
        }

        @JvmStatic
        val isNetworkAvailable: Boolean
            /**
             * Checks if we have internet
             * @return
             */
            get() {
                val cm = instance.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
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

        @JvmStatic
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

        @JvmStatic
        val isTablet: Boolean
            /**
             * Checks if the device is a tablet
             * @return
             */
            get() = ((instance.resources.configuration.screenLayout
                    and Configuration.SCREENLAYOUT_SIZE_MASK)
                    >= Configuration.SCREENLAYOUT_SIZE_LARGE)

        /**
         * Returns the unique device id for this device
         * @return
         */
        @SuppressLint("HardwareIds")
        @JvmStatic
        fun udid(): String {
            return Build.MODEL.lowercase().replace(" ", "_")
        }

        @JvmStatic
        var deviceNetworkAlias: String
            /**
             * Returns the alias to be displayed when others see this device on the network
             * @return
             */
            get() = prefs.getDefaultPref(SettingsActivity.KEY_PREF_DEVICE_ALIAS, "")
            /**
             * Sets the alias to be displayed when others see this device on the network
             * @param alias
             */
            set(alias) {
                prefs.setDefaultPref(SettingsActivity.KEY_PREF_DEVICE_ALIAS, alias)
            }

        /**
         * shows the keyboard in the given activity and view
         * @param activity
         * @param view
         */
        @JvmStatic
        fun showKeyboard(activity: Activity?, view: View?) {
            if (activity != null && view != null) {
                val controller = WindowCompat.getInsetsController(activity.window, view)
                controller.show(WindowInsetsCompat.Type.ime())
            }
        }

        /**
         * Closes the keyboard in the given activity
         * @param activity
         */
        @JvmStatic
        fun closeKeyboard(activity: Activity?) {
            if (activity != null && activity.window != null) {
                val decorView: View = activity.window.decorView
                val controller = WindowCompat.getInsetsController(activity.window, decorView)
                controller.hide(WindowInsetsCompat.Type.ime())
            }
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
            val packageName = instance.packageName
            val intent = instance.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                instance.startActivity(Intent.makeRestartActivityTask(intent.component))
                Process.killProcess(Process.myPid())
                RuntimeWrapper.exit(0)
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
