package com.door43.translationstudio.ui.dialogs

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.DialogPrintBinding
import com.door43.translationstudio.ui.viewmodels.ExportViewModel
import com.door43.usecases.ExportProjects
import com.door43.util.FileUtilities
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.security.InvalidParameterException
import javax.inject.Inject
import kotlin.math.min

/**
 * Created by joel on 11/16/2015.
 */
@AndroidEntryPoint
class PrintDialog : DialogFragment() {

    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client

    private var progressDialog: ProgressHelper.ProgressDialog? = null
    private lateinit var targetTranslation: TargetTranslation
    private var includeImages = false
    private var includeIncompleteFrames = true
    private var destinationFilename: Uri? = null
    private var mAlertShown = DialogShown.NONE
    private val mPrompt: AlertDialog? = null

    private val viewModel: ExportViewModel by viewModels()

    private var _binding: DialogPrintBinding? = null
    private val binding get() = _binding!!

    private var activityResultLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                destinationFilename = data?.data
                startPdfPrinting()
            }
        }
        progressDialog = ProgressHelper.newInstance(
            requireContext(),
            R.string.printing,
            false
        )
        return super.onCreateDialog(savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mPrompt?.dismiss()
        _binding = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogPrintBinding.inflate(inflater, container, false)

        val args = arguments
        if (args != null && args.containsKey(BackupDialog.ARG_TARGET_TRANSLATION_ID)) {
            val targetTranslationId = args.getString(BackupDialog.ARG_TARGET_TRANSLATION_ID, null)
            viewModel.loadTargetTranslation(targetTranslationId)
        } else {
            throw InvalidParameterException("The target translation id was not specified")
        }

        setupObservers()

        if (savedInstanceState != null) {
            includeImages = savedInstanceState.getBoolean(STATE_INCLUDE_IMAGES, includeImages)
            includeIncompleteFrames =
                savedInstanceState.getBoolean(STATE_INCLUDE_INCOMPLETE, includeIncompleteFrames)
            mAlertShown = DialogShown.fromInt(
                savedInstanceState.getInt(STATE_DIALOG_SHOWN, INVALID),
                DialogShown.NONE
            )
            destinationFilename = savedInstanceState.getParcelable(STATE_OUTPUT_FILENAME)
        }

        with(binding) {
            printIncompleteFrames.isEnabled = true
            printIncompleteFrames.isChecked = includeIncompleteFrames

            cancelButton.setOnClickListener { dismiss() }

            printButton.setOnClickListener {
                includeImages = printImages.isChecked
                includeIncompleteFrames = printIncompleteFrames.isChecked
                selectDestinationFile()
            }
        }

        restoreDialogs()
        return binding.root
    }

    private fun setupObservers() {
        viewModel.translation.observe(this) {
            targetTranslation = it
                ?: throw NullPointerException("Target translation not found.")
            onTranslationLoaded()
        }
        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog?.show()
                progressDialog?.setMessage(it.message)
                progressDialog?.setMax(it.max)
                progressDialog?.setProgress(it.progress)
            } else {
                progressDialog?.dismiss()
            }
        }
        viewModel.exportResult.observe(this) {
            it?.let { result ->
                when (result.taskName) {
                    ExportProjects.TaskName.EXPORT_PDF -> {
                        if (result.success) {
                            val filename = FileUtilities.getUriDisplayName(
                                requireContext(),
                                destinationFilename
                            )
                            val message = requireContext().resources.getString(R.string.print_success, filename)
                            AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                                .setTitle(R.string.success)
                                .setMessage(message)
                                .setPositiveButton(R.string.dismiss) { dialog, _ ->
                                    dialog.dismiss()
                                }
                                .setOnDismissListener {
                                    this@PrintDialog.dismiss()
                                }
                                .show()
                        } else {
                            AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                                .setTitle(R.string.error)
                                .setMessage(R.string.print_failed)
                                .setPositiveButton(R.string.dismiss, null)
                                .setOnDismissListener {
                                    this@PrintDialog.dismiss()
                                }
                                .show()
                        }
                    }
                    else -> {}
                }
            }
        }
        viewModel.downloadResult.observe(this) {
            it?.let { result ->
                if (result.success) {
                    printPDF(result.imagesDir)
                } else {
                    AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                        .setTitle(R.string.download_failed)
                        .setMessage(R.string.downloading_images_for_print_failed)
                        .setPositiveButton(R.string.label_ok, null)
                        .show()
                }
            }
        }
    }

    /**
     * Restores dialogs
     */
    private fun restoreDialogs() {
        Handler(Looper.getMainLooper()).post { // restore alert dialogs
            when (mAlertShown) {
                DialogShown.INTERNET_PROMPT -> showInternetUsePrompt()
                DialogShown.NONE -> {}
                else -> Logger.e(TAG, "Unsupported restore dialog: $mAlertShown")
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // widen dialog to accommodate more text
        val desiredWidth = 750
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val density = displayMetrics.density
        val correctedWidth = width / density
        var screenWidthFactor = desiredWidth / correctedWidth
        screenWidthFactor = min(screenWidthFactor.toDouble(), 1.0).toFloat() // sanity check
        dialog?.window?.setLayout(
            (width * screenWidthFactor).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    /**
     * starts activity to let user select output folder
     */
    private fun selectDestinationFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType("application/pdf")
        intent.putExtra(Intent.EXTRA_TITLE, targetTranslation.id + ".pdf")
        activityResultLauncher?.launch(intent)
    }

    /**
     * start PDF printing. If image printing is selected, will prompt to warn for internet usage
     * @return void
     */
    private fun startPdfPrinting() {
        if (includeImages) {
            showInternetUsePrompt()
        } else {
            printPDF()
        }
    }

    private fun printPDF(imagesDir: File? = null) {
        destinationFilename?.let { uri ->
            viewModel.exportPDF(
                uri,
                includeImages,
                includeIncompleteFrames,
                imagesDir
            )
        }
    }

    private fun downloadImages() {
        viewModel.downloadImages()
    }

    /**
     * prompt user to show internet
     */
    private fun showInternetUsePrompt() {
        mAlertShown = DialogShown.INTERNET_PROMPT

        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.use_internet_confirmation)
            .setMessage(R.string.image_large_download)
            .setNegativeButton(R.string.title_cancel) { _, _ ->
                mAlertShown = DialogShown.NONE
            }
            .setPositiveButton(R.string.label_ok) { _, _ ->
                mAlertShown = DialogShown.NONE
                downloadImages()
            }
            .show()
    }

    private fun onTranslationLoaded() {
        with(binding) {
            // set typeface for language
            val targetLanguage = targetTranslation.targetLanguage
            val typeface = Typography.getBestFontForLanguage(
                requireContext(),
                TranslationType.SOURCE,
                targetLanguage.slug,
                targetLanguage.direction
            )
            projectTitle.setTypeface(typeface, Typeface.NORMAL)

            var title = targetTranslation.projectTranslation.title.replace("\n+$".toRegex(), "")
            if (title.isEmpty()) {
                val sourceContainer = ContainerCache.cacheClosest(
                    library,
                    null,
                    targetTranslation.projectId,
                    targetTranslation.resourceSlug
                )
                if (sourceContainer != null) {
                    title = sourceContainer.readChunk("front", "title")
                        .replace("\n+$".toRegex(), "")
                }
            }
            if (title.isEmpty()) {
                title = targetTranslation.projectId
            }
            projectTitle.text = title + " - " + targetTranslation.targetLanguageName

            val isObsProject = targetTranslation.isObsProject
            if (isObsProject) {
                printImages.isEnabled = true
                printImages.isChecked = includeImages
            } else {
                printImages.visibility = View.GONE
                printImages.isChecked = false
            }
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean(STATE_INCLUDE_IMAGES, includeImages)
        out.putBoolean(STATE_INCLUDE_INCOMPLETE, includeIncompleteFrames)
        out.putInt(STATE_DIALOG_SHOWN, mAlertShown.value)
        out.putParcelable(STATE_OUTPUT_FILENAME, destinationFilename)

        super.onSaveInstanceState(out)
    }

    /**
     * for keeping track if dialog is being shown for orientation changes
     */
    enum class DialogShown {
        NONE,
        INTERNET_PROMPT,
        FILENAME_PROMPT,
        OVERWRITE_PROMPT;

        val value: Int
            get() = this.ordinal

        companion object {
            fun fromInt(ordinal: Int, defaultValue: DialogShown): DialogShown {
                if (ordinal > 0 && ordinal < entries.size) {
                    return entries[ordinal]
                }
                return defaultValue
            }
        }
    }

    companion object {
        const val TAG: String = "printDialog"
        const val ARG_TARGET_TRANSLATION_ID: String = "target_translation_id"
        const val STATE_INCLUDE_IMAGES: String = "include_images"
        const val STATE_INCLUDE_INCOMPLETE: String = "include_incomplete"
        const val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        const val INVALID: Int = -1
        const val STATE_OUTPUT_FILENAME: String = "state_output_filename"
    }
}
