package com.door43.translationstudio.ui.profile

import android.os.Bundle
import androidx.activity.compose.setContent
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import org.koin.android.ext.android.inject

class LoginOfflineActivity : BaseActivity() {
    val profile: Profile by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                LoginOfflineScreen(
                    onCancel = { finish() },
                    onContinue = { fullName ->
                        profile.login(fullName)
                        finish()
                    }
                )
            }
        }
    }
}