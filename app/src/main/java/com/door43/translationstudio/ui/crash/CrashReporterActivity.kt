package com.door43.translationstudio.ui.crash

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.door43.translationstudio.App
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.MainActivity
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
                        isNetworkAvailable = App.Companion.isNetworkAvailable,
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
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}