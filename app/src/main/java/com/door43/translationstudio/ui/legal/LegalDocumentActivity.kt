package com.door43.translationstudio.ui.legal

import android.os.Bundle
import androidx.activity.compose.setContent
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity

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
