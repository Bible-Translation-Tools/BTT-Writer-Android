package com.door43.translationstudio.ui.dialogs

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.App.Companion.isStoreVersion
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.DialogFeedbackBinding
import com.door43.translationstudio.ui.viewmodels.FeedbackViewModel
import com.door43.usecases.CheckForLatestRelease
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by joel on 9/17/2015.
 */
@AndroidEntryPoint
class FeedbackDialog : DialogFragment() {
    private var message = ""

    private var _binding: DialogFeedbackBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FeedbackViewModel by viewModels()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogFeedbackBinding.inflate(inflater, container, false)

        if (savedInstanceState == null) {
            val args = arguments
            if (args != null) {
                message = args.getString(ARG_MESSAGE, "")
            }
        }

        setupObservers()

        with(binding) {
            ViewUtil.tintViewDrawable(
                wifiIcon,
                requireActivity().resources.getColor(R.color.dark_secondary_text)
            )
            editText.setText(message)
            editText.setSelection(editText.text.length)

            cancelButton.setOnClickListener { dismiss() }
            confirmButton.setOnClickListener {
                if (editText.text.toString().isEmpty()) {
                    // requires text
                    notifyInputRequired()
                } else {
                    reportBug(editText.text.toString().trim())
                }
            }
        }

        if (savedInstanceState != null) {
            message = savedInstanceState.getString(STATE_NOTES, "")
        }

        return binding.root
    }

    private fun setupObservers() {
        viewModel.loading.observe(this) {
            if (it) {
                showLoadingUI()
            } else {
                hideLoadingUI()
            }
        }
        viewModel.latestRelease.observe(this) {
            it?.let { result ->
                if (result.release != null) {
                    val hand = Handler(Looper.getMainLooper())
                    hand.post { notifyLatestRelease(result.release) }
                } else {
                    if (message.isNotEmpty()) {
                        viewModel.uploadFeedback(message)
                    } else {
                        notifyInputRequired()
                        dismiss()
                    }
                }
            }
        }
        viewModel.success.observe(this) {
            it?.let { success ->
                if (success) {
                    val snack = Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        R.string.success,
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                    dismiss()
                } else {
                    val networkAvailable = isNetworkAvailable
                    val hand = Handler(Looper.getMainLooper())
                    hand.post {
                        val messageId = if (networkAvailable) R.string.upload_feedback_failed else R.string.internet_not_available
                        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.upload_failed)
                            .setMessage(messageId)
                            .setPositiveButton(R.string.retry_label) { _, _ -> viewModel.uploadFeedback(message) }
                            .setNegativeButton(R.string.label_close, null)
                            .show()
                    }
                }
            }
        }
    }

    private fun notifyInputRequired() {
        val snack = Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            R.string.input_required,
            Snackbar.LENGTH_SHORT
        )
        ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
        snack.show()
    }

    private fun reportBug(message: String) {
        this.message = message
        viewModel.checkForLatestRelease()
    }

    private fun showLoadingUI() {
        binding.formLayout.visibility = View.GONE
        binding.controlsLayout.visibility = View.GONE
        binding.loadingLayout.visibility = View.VISIBLE
    }

    private fun hideLoadingUI() {
        binding.formLayout.visibility = View.VISIBLE
        binding.controlsLayout.visibility = View.VISIBLE
        binding.loadingLayout.visibility = View.GONE
    }

    /**
     * Displays a dialog to the user telling them there is an apk update.
     * @param release
     */
    private fun notifyLatestRelease(release: CheckForLatestRelease.Release) {
        val isStoreVersion = isStoreVersion

        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.apk_update_available)
            .setMessage(R.string.upload_report_or_download_latest_apk)
            .setNegativeButton(R.string.title_cancel) { _, _ ->
                viewModel.clearResults()
                this@FeedbackDialog.dismiss()
            }
            .setNeutralButton(R.string.download_update) { _, _ ->
                if (isStoreVersion) {
                    // open play store
                    val appPackageName = requireActivity().packageName
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$appPackageName")
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")
                            )
                        )
                    }
                } else {
                    // download from github
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))
                    startActivity(browserIntent)
                }
                this@FeedbackDialog.dismiss()
            }
            .setPositiveButton(R.string.label_continue) { _, _ ->
                if (message.isNotEmpty()) {
                    viewModel.uploadFeedback(message)
                } else {
                    notifyInputRequired()
                    this@FeedbackDialog.dismiss()
                }
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_NOTES, message)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        viewModel.cancelJobs()
        viewModel.clearResults()
        super.dismiss()
    }

    companion object {
        private const val STATE_NOTES = "bug_notes"
        const val ARG_MESSAGE: String = "arg_message"
    }
}
