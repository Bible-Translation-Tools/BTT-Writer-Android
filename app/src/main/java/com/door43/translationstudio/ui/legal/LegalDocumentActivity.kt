package com.door43.translationstudio.ui.legal

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.viewmodels.SettingsViewModel
import org.koin.androidx.compose.koinViewModel

class LegalDocumentActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = intent.extras
        var resourceId = 0
        if (args != null) {
            resourceId = args.getInt(ARG_RESOURCE, 0)
        }
        if (resourceId == 0) {
            finish()
            return
        }

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
                LegalDocumentDialog(
                    htmlResourceId = resourceId,
                    onDismissRequest = { finish() }
                )
            }
        }
    }

    companion object {
        const val ARG_RESOURCE: String = "arg_resource_id"
    }
}
