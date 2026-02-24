package com.door43.translationstudio.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.profile.LoginScreen
import org.koin.android.ext.android.inject

class LoginDoor43Activity : BaseActivity() {

    private val profile: Profile by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
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
