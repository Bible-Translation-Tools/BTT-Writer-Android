package com.door43.translationstudio.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.crash.CrashReporterActivity
import com.door43.translationstudio.ui.profile.ProfileActivity

/**
 * This activity initializes the app
 */
class SplashScreenActivity : BaseActivity() {

    override val isBootActivity: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
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