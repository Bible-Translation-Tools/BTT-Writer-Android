package com.door43.translationstudio.ui.draft

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.viewmodels.DraftViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.security.InvalidParameterException

class DraftActivity : BaseActivity() {
    val typography: Typography by inject()
    val renderingProvider: RenderingProvider by inject()

    private val viewModel: DraftViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // validate parameters
        val targetTranslationId = intent.extras?.getString(EXTRA_TARGET_TRANSLATION_ID)
            ?: throw InvalidParameterException("This activity expects some arguments")

        viewModel.loadDraftTranslations(targetTranslationId)

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DraftScreen(
                        viewModel = viewModel,
                        typography = typography,
                        renderingProvider = renderingProvider,
                        onFinish = { finish() }
                    )
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val TAG: String = "DraftActivity"
        const val EXTRA_TARGET_TRANSLATION_ID: String = "target_translation_id"
    }
}