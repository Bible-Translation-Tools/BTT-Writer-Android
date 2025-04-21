package com.door43.translationstudio.ui.home

import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.viewmodels.ImportIndexViewModel
import com.door43.util.FileUtilities

class ImportIndexActivity : BaseActivity() {

    private lateinit var openIndexContent: ActivityResultLauncher<String>

    private val viewModel: ImportIndexViewModel by viewModels()
    private var progressDialog: ProgressHelper.ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openIndexContent = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val filename = FileUtilities.getUriDisplayName(this, uri)
                val isSqlite = filename.contains(".sqlite", ignoreCase = true)
                if (isSqlite) {
                    viewModel.importIndex(uri)
                } else {
                    AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.error)
                        .setMessage(R.string.import_index_failed)
                        .setPositiveButton(R.string.label_ok, null)
                        .show()
                }
            }
        }

        setupObservers()

        if (savedInstanceState == null) {
            // validate parameters
            val args = intent.extras
            checkNotNull(args)

            val action = args.getInt(IMPORT_ACTION, DOWNLOAD_INDEX)

            if (action == IMPORT_INDEX) {
                openIndexContent.launch("*/*")
            } else {
                viewModel.downloadIndex()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        progressDialog = ProgressHelper.newInstance(
            supportFragmentManager,
            R.string.import_index,
            false
        )
    }

    private fun setupObservers() {
        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog?.show()
                progressDialog?.setProgress(it.progress)
                progressDialog?.setMessage(it.message)
                progressDialog?.setMax(it.max)
            } else {
                progressDialog?.dismiss()
            }
        }
        viewModel.indexDownloaded.observe(this) {
            it?.let { success ->
                if (success) {
                    showUpdateResultDialog(
                        message = resources.getString(R.string.download_index_success),
                        onConfirm = App::restart,
                        onDismiss = App::restart
                    )
                } else {
                    showUpdateResultDialog(
                        R.string.error,
                        resources.getString(R.string.options_update_failed)
                    )
                }
            }
        }
    }

    private fun showUpdateResultDialog(
        titleId: Int = R.string.update_success,
        message: String = resources.getString(R.string.update_success),
        onConfirm: () -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        val dialog = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(titleId)
            .setMessage(message)
            .setPositiveButton(
                R.string.dismiss
            ) { _, _ ->
                onConfirm()
            }
            .setOnDismissListener {
                onDismiss()
            }

        dialog.show()
    }

    companion object {
        const val IMPORT_ACTION = "import_action"
        const val IMPORT_INDEX = 1
        const val DOWNLOAD_INDEX = 2
    }
}