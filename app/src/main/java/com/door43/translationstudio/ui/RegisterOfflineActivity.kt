package com.door43.translationstudio.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.profile.RegisterOfflineScreen
import com.door43.translationstudio.ui.viewmodels.SettingsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class RegisterOfflineActivity : AppCompatActivity() {
    val profile: Profile by inject()

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
                RegisterOfflineScreen(
                    onCancel = { finish() },
                    onShowPrivacyNotice = {
                        ProfileActivity.showPrivacyNotice(this@RegisterOfflineActivity, null)
                    },
                    onContinue = { fullName ->
                        ProfileActivity.showPrivacyNotice(this@RegisterOfflineActivity) { _, _ ->
                            profile.login(fullName)
                            finish()
                        }
                    }
                )
            }
        }
    }
}
