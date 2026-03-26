package com.door43.translationstudio.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.BuildConfig
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.devtools.DeveloperToolsActivity
import com.door43.translationstudio.ui.profile.ProfileActivity
import org.koin.androidx.compose.koinViewModel

/**
 * A [SettingsActivity] that presents a set of application settings.
 */
class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val model by viewModel.state.collectAsStateWithLifecycle()

            val lightValue = resources.getString(R.string.theme_value_light)
            val darkValue = resources.getString(R.string.theme_value_dark)
            val isDark = when (model.currentThemeValue) {
                lightValue -> false
                darkValue -> true
                else -> isSystemInDarkTheme()
            }

            AppTheme(darkTheme = isDark) {
                SettingsScreen(
                    appVersion = "${BuildConfig.VERSION_NAME} - ${BuildConfig.VERSION_CODE}",
                    onNavigateBack = { finish() },
                    onNavigateToProfile = {
                        val intent = Intent(this, ProfileActivity::class.java)
                        startActivity(intent)
                    },
                    onNavigateToDeveloperTools = {
                        val intent = Intent(this, DeveloperToolsActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    companion object {
        const val KEY_PREF_CONTENT_SERVER = "content_server"
        const val KEY_PREF_GIT_SERVER_PORT = "git_server_port"
        const val KEY_PREF_ALWAYS_SHARE = "always_share"
        const val KEY_PREF_MEDIA_SERVER = "media_server"
        const val KEY_PREF_READER_SERVER = "reader_server"
        const val KEY_PREF_CREATE_ACCOUNT_URL = "create_account_url"
        const val KEY_PREF_LANGUAGES_URL = "lang_names_url"
        const val KEY_PREF_INDEX_SQLITE_URL = "index_sqlite_url"
        const val KEY_PREF_COLOR_THEME = "color_theme"

        const val KEY_PREF_TRANSLATION_TYPEFACE = "translation_typeface"
        const val KEY_PREF_TRANSLATION_TYPEFACE_SIZE = "typeface_size"
        const val KEY_PREF_SOURCE_TYPEFACE = "source_typeface"
        const val KEY_PREF_SOURCE_TYPEFACE_SIZE = "source_typeface_size"

        const val KEY_PREF_LOGGING_LEVEL = "logging_level"
        const val KEY_PREF_BACKUP_INTERVAL = "backup_interval"
        const val KEY_PREF_DEVICE_ALIAS = "device_name"
        const val KEY_PREF_GOGS_API = "gogs_api"
        const val KEY_PREF_CHECK_HARDWARE = "check_hardware_requirements"

        const val KEY_PREF_APP_UPDATES = "app_updates"
        const val KEY_PREF_APP_VERSION = "app_version"
        const val KEY_PREF_LICENSE_AGREEMENT = "license_agreement"
        const val KEY_PREF_STATEMENT_OF_FAITH = "statement_of_faith"
        const val KEY_PREF_TRANSLATION_GUIDELINES = "translation_guidelines"
        const val KEY_PREF_SOFTWARE_LICENSES = "software_licenses"

        const val KEY_PREF_MIGRATE_OLD_APP = "migrate_old_app"
        const val KEY_PREF_TM_URL = "tm_url"
        const val KEY_PREF_ENABLE_TM_LINKS = "enable_tm_links"
    }
}