package com.door43.translationstudio.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.profile.LoginScreen
import com.door43.translationstudio.ui.viewmodels.SettingsViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class LoginDoor43Activity : AppCompatActivity() {

    private val profile: Profile by inject()

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
                Surface(color = MaterialTheme.colorScheme.background) {
                    LoginScreen(
                        profileFullName = profile.fullName,
                        isNetworkAvailable = isNetworkAvailable,
                        onLoginSuccess = { user ->
                            if (user.fullName.isNullOrEmpty()) {
                                user.fullName = user.username
                            }
                            profile.login(user.fullName, user)
                            finish()
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}
