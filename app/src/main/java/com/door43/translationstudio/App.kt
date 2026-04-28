package com.door43.translationstudio

import android.app.Application
import androidx.preference.PreferenceManager
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.di.appModule
import com.door43.di.prodDataModule
import com.door43.util.FileUtilities
import org.bibletranslationtools.logger.Logger
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.unfoldingword.tools.foreground.Foreground
import java.io.File
import java.io.IOException

/**
 * This class provides global access to the application context as well as other important tools
 */
class App : Application() {

    private val prefRepository: IPreferenceRepository by inject()
    private val directoryProvider: IDirectoryProvider by inject()
    private val platform: Platform by inject()

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.WARNING)
            androidContext(this@App)
            modules(appModule, prodDataModule)
        }

        Foreground.init(this)

        prefRepository.getDefaultPref(
            IPreferenceRepository.KEY_PREF_LOGGING_LEVEL,
            resources.getString(R.string.pref_default_logging_level)
        ).let { minLogLevel ->
            platform.configureLogger(minLogLevel.toInt())
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
    }
}
