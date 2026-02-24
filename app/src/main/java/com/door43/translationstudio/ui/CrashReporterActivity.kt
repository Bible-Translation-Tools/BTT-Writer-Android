package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.ui.crash.CrashReporterScreen
import com.door43.translationstudio.ui.viewmodels.CrashReporterViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.unfoldingword.tools.logger.Logger

class CrashReporterActivity : BaseActivity() {

    override val isBootActivity: Boolean = true

    private val viewModel: CrashReporterViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CrashReporterScreen(
                        viewModel = viewModel,
                        isNetworkAvailable = isNetworkAvailable,
                        onFlushAndSplash = {
                            Logger.flush()
                            openSplash()
                        },
                        onDownloadUpdate = {
                            Logger.flush()
                            viewModel.downloadLatestRelease()
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun openSplash() {
        val intent = Intent(this, SplashScreenActivity::class.java)
        startActivity(intent)
        finish()
    }
}
