package com.door43.translationstudio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.util.FileUtilities
import org.unfoldingword.tools.foreground.Foreground
import org.unfoldingword.tools.logger.LogLevel
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.io.IOException
import java.util.Locale
import android.net.NetworkCapabilities
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.usecases.BackupRC
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * This class provides global access to the application context as well as other important tools
 */
@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var prefRepository: IPreferenceRepository
    @Inject
    lateinit var directoryProvider: IDirectoryProvider
    @Inject
    lateinit var backupRC: BackupRC

    override fun onCreate() {
        super.onCreate()

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
