package com.door43.translationstudio.ui.legal

import android.os.Bundle
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

        val dialog = LegalDocumentDialog()
        dialog.setOnDismissListener { finish() }
        val ft = supportFragmentManager.beginTransaction()
        val prev = supportFragmentManager.findFragmentByTag("dialog")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        dialog.arguments = intent.extras
        dialog.show(ft, "dialog")
    }

    companion object {
        const val ARG_RESOURCE: String = "arg_resource_id"
    }
}
