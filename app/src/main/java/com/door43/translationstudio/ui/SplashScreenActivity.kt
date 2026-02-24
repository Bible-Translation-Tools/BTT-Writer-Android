package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.splash.SplashScreen
import com.door43.translationstudio.ui.viewmodels.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * This activity initializes the app
 */
class SplashScreenActivity : BaseActivity() {

    override val isBootActivity: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: SettingsViewModel = koinViewModel()
            val model by viewModel.model.collectAsStateWithLifecycle()

            val lightValue = resources.getString(R.string.theme_value_light)
            val darkValue = resources.getString(R.string.theme_value_dark)
            val isDarkTheme = when (model.currentThemeValue) {
                lightValue -> false
                darkValue -> true
                else -> isSystemInDarkTheme()
            }

            AppTheme(darkTheme = isDarkTheme) {
                SplashScreen(
                    onNavigateToProfile = {
                        startActivity(Intent(this, ProfileActivity::class.java))
                        finish()
                    },
                    onNavigateToCrashReporter = {
                        startActivity(Intent(this, CrashReporterActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}
