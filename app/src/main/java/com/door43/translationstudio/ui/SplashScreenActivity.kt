package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent

/**
 * This activity initializes the app
 */
class SplashScreenActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
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
