package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.ActivityCrashReporterBinding
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.viewmodels.CrashReporterViewModel
import org.unfoldingword.tools.logger.Logger

class CrashReporterActivity : BaseActivity() {
    private var notes = ""
    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private lateinit var binding: ActivityCrashReporterBinding
    private val viewModel: CrashReporterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCrashReporterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.okButton.setOnClickListener {
            notes = binding.crashDescription.text.toString().trim()

            AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.title_upload)
                .setMessage(R.string.use_internet_confirmation)
                .setPositiveButton(R.string.label_continue) { _, _ ->
                    viewModel.checkForLatestRelease()
                }
                .setNegativeButton(R.string.label_close) { _, _ ->
                    Logger.flush()
                    openSplash()
                }
                .show()
        }

        binding.cancelButton.setOnClickListener {
            Logger.flush()
            openSplash()
        }

        setupObservers()
    }

    override fun onStart() {
        super.onStart()
        progressDialog = ProgressHelper.newInstance(
            supportFragmentManager,
            R.string.loading,
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
        viewModel.latestRelease.observe(this) {
            it?.let { result ->
                if (result.release != null) {
                    // ask user if they would like to download updates
                    val hand = Handler(Looper.getMainLooper())
                    hand.post { notifyLatestRelease() }
                } else {
                    val report = binding.crashDescription.text.toString().trim()
                    viewModel.uploadCrashReport(report)
                }
            }
        }
        viewModel.crashReportUploaded.observe(this) {
            it?.let { uploaded ->
                Logger.i(this.javaClass.simpleName, "UploadCrashReportTask success:$uploaded")
                if (uploaded) {
                    openSplash()
                } else { // upload failed
                    val networkAvailable = isNetworkAvailable

                    val hand = Handler(Looper.getMainLooper())
                    hand.post {
                        val messageId = if (networkAvailable) {
                            R.string.upload_crash_report_failed
                        } else {
                            R.string.internet_not_available
                        }
                        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                            .setTitle(R.string.upload_failed)
                            .setMessage(messageId)
                            .setPositiveButton(R.string.label_ok, null)
                            .show()
                    }
                }
            }
        }
    }

    public override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        notes = savedInstanceState.getString(STATE_NOTES, "")
        binding.crashDescription.setText(notes)
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.latestRelease.value != null) {
            notifyLatestRelease()
        }
    }

    /**
     * Displays a dialog to the user telling them there is an apk update.
     */
    private fun notifyLatestRelease() {
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.apk_update_available)
            .setMessage(R.string.upload_report_or_download_latest_apk)
            .setNegativeButton(R.string.title_cancel) { _, _ ->
                Logger.flush()
                openSplash()
            }
            .setNeutralButton(R.string.download_update) { _, _ ->
                Logger.flush()
                viewModel.downloadLatestRelease()
                finish()
            }
            .setPositiveButton(R.string.label_continue) { _, _ ->
                viewModel.uploadCrashReport(notes)
            }
            .show()
    }

    private fun openSplash() {
        val intent = Intent(this, SplashScreenActivity::class.java)
        startActivity(intent)
        finish()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            STATE_NOTES,
            binding.crashDescription.text.toString().trim()
        )
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val STATE_NOTES = "state_notes"
    }
}
