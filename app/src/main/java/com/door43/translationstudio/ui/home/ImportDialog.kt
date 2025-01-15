package com.door43.translationstudio.ui.home

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.DialogImportBinding
import com.door43.translationstudio.ui.ImportUsfmActivity
import com.door43.translationstudio.ui.ImportUsfmActivity.Companion.EXTRA_USFM_IMPORT_URI
import com.door43.translationstudio.ui.dialogs.DeviceNetworkAliasDialog
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.dialogs.ShareWithPeerDialog
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.viewmodels.ImportViewModel
import com.door43.util.FileUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.io.File
import javax.inject.Inject

/**
 * Created by joel on 10/5/2015.
 */
@AndroidEntryPoint
class ImportDialog : DialogFragment() {
    @Inject lateinit var profile: Profile
    @Inject lateinit var translator: Translator
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var library: Door43Client

    private val viewModel: ImportViewModel by viewModels()

    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private var settingDeviceAlias = false
    private var dialogShown: DialogShown = DialogShown.NONE
    private var dialogMessage: String? = null
    private var targetTranslationID: String? = null
    private var importUri: Uri? = null
    private var mergeSelection: MergeOptions? = MergeOptions.NONE
    private var mergeConflicted = false
    private val dialogs = arrayListOf<AlertDialog>()

    private lateinit var openTranslationContent: ActivityResultLauncher<String>
    private lateinit var openUSFMContent: ActivityResultLauncher<String>
    private lateinit var openDirectory: ActivityResultLauncher<Uri?>
    private lateinit var activityLauncher: ActivityResultLauncher<Intent>

    private var _binding: DialogImportBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(true)

        activityLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {}

        openTranslationContent = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val filename = FileUtilities.getUriDisplayName(requireContext(), uri)
                val isTstudio = filename.contains(Translator.TSTUDIO_EXTENSION, ignoreCase = true)
                val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)
                if (isTstudio || isZip) {
                    importLocal(uri, IMPORT_TRANSLATION_MIME)
                } else {
                    showImportResults(R.string.invalid_file, filename)
                }
            }
        }

        openUSFMContent = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                val filename = FileUtilities.getUriDisplayName(requireContext(), uri)
                val isUsfm = filename.contains(Translator.USFM_EXTENSION, ignoreCase = true)
                val isTxt = filename.contains(Translator.TXT_EXTENSION, ignoreCase = true)
                val isZip = filename.contains(Translator.ZIP_EXTENSION, ignoreCase = true)
                if (isUsfm || isTxt || isZip) {
                    importLocal(uri, IMPORT_USFM_MIME)
                } else {
                    showImportResults(R.string.invalid_file, filename)
                }
            }
        }

        openDirectory = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) {
            it?.let(::doImportSourceText)
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogImportBinding.inflate(inflater, container, false)

        progressDialog = ProgressHelper.newInstance(
            childFragmentManager,
            R.string.label_import,
            false
        )

        if (savedInstanceState != null) {
            // check if returning from device alias dialog
            settingDeviceAlias = savedInstanceState.getBoolean(
                STATE_SETTING_DEVICE_ALIAS,
                false
            )
            dialogShown = DialogShown.fromInt(
                savedInstanceState.getInt(
                    STATE_DIALOG_SHOWN,
                    DialogShown.NONE.value
                )
            )
            dialogMessage = savedInstanceState.getString(
                STATE_DIALOG_MESSAGE,
                null
            )
            targetTranslationID = savedInstanceState.getString(
                STATE_DIALOG_TRANSLATION_ID,
                null
            )
            mergeConflicted = savedInstanceState.getBoolean(
                STATE_MERGE_CONFLICT,
                false
            )
            mergeSelection = MergeOptions.fromInt(
                savedInstanceState.getInt(
                    STATE_MERGE_SELECTION,
                    MergeOptions.NONE.value
                )
            )
            val path = savedInstanceState.getString(STATE_IMPORT_URL, null)
            importUri = if ((path != null)) Uri.parse(path) else null
        }

        with(binding) {
            infoButton.setOnClickListener {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://help.door43.org/en/knowledgebase/9-translationstudio/docs/3-import-options")
                )
                startActivity(browserIntent)
            }

            importResourceContainer.setOnClickListener {
                onImportSourceText()
            }

            importFromDoor43.setOnClickListener {
                // open dialog for browsing repositories
                val dialog = ImportFromDoor43Dialog()
                showDialogFragment(dialog, ImportFromDoor43Dialog.TAG)
            }

            importTargetTranslation.setOnClickListener {
                mergeSelection = MergeOptions.NONE
                onImportTranslation()
            }

            importUsfm.setOnClickListener {
                mergeSelection = MergeOptions.NONE
                onImportUSFM()
            }

            importBackup.setOnClickListener {
                val dialog = ImportFromBackupDialog()
                dialog.setOnItemSelected(::onBackupFileSelected)
                showDialogFragment(dialog, ImportFromBackupDialog.TAG)
            }

            importFromDevice.setOnClickListener {
                mergeSelection = MergeOptions.NONE
                // TODO: 11/18/2015 eventually we need to support bluetooth as well as an adhoc network
                if (App.isNetworkAvailable) {
                    if (App.deviceNetworkAlias.isEmpty()) {
                        // get device alias
                        settingDeviceAlias = true
                        val dialog = DeviceNetworkAliasDialog()
                        showDialogFragment(dialog, "device-name-dialog")
                    } else {
                        showP2PDialog()
                    }
                } else {
                    val snack = Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        R.string.internet_not_available,
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                    snack.show()
                }
            }

            dismissButton.setOnClickListener { dismiss() }
        }

        setupObservers()
        restoreDialogs()
        return binding.root
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
        viewModel.importFromUriResult.observe(this) {
            it?.let { result ->
                importUri = result.filePath
                mergeConflicted = result.mergeConflict
                if (result.success && result.alreadyExists && mergeSelection == MergeOptions.NONE) {
                    showMergeOverwritePrompt(result.importedSlug)
                } else if (result.success) {
                    showImportResults(R.string.import_success, result.readablePath)
                } else if (result.invalidFileName) {
                    showImportResults(R.string.invalid_file, result.readablePath)
                } else {
                    showImportResults(R.string.import_failed, result.readablePath)
                }

                // TODO: terrible hack.
                (activity as? HomeActivity)?.loadTranslations()
            }
        }
        viewModel.importSourceResult.observe(this) {
            it?.let { result ->
                when {
                    result.success -> {
                        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.success)
                            .setMessage(R.string.title_import_success)
                            .setPositiveButton(R.string.dismiss, null)
                            .setOnDismissListener { clearResults() }
                            .show()
                            .also(dialogs::add)
                    }
                    result.hasConflict -> {
                        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.confirm)
                            .setMessage(result.error)
                            .setNegativeButton(R.string.menu_cancel, null)
                            .setPositiveButton(R.string.confirm) { _, _ ->
                                result.targetDir?.let(viewModel::importSource)
                            }
                            .setOnDismissListener { clearResults() }
                            .show()
                            .also(dialogs::add)
                    }
                    else -> {
                        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.could_not_import)
                            .setMessage(result.error)
                            .setPositiveButton(R.string.dismiss, null)
                            .setOnDismissListener { clearResults() }
                            .show()
                            .also(dialogs::add)
                    }
                }
            }
        }
    }

    private fun onBackupFileSelected(backup: File) {
        viewModel.restoreFromBackup(backup)
    }

    /**
     * restore the dialogs that were displayed before rotation
     */
    private fun restoreDialogs() {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            when (dialogShown) {
                DialogShown.SHOW_IMPORT_RESULTS -> showImportResults(dialogMessage)
                DialogShown.MERGE_CONFLICT -> showMergeOverwritePrompt(targetTranslationID)
                DialogShown.NONE -> {}
            }
            val importBackupDialog = parentFragmentManager
                .findFragmentByTag(ImportFromBackupDialog.TAG) as? ImportFromBackupDialog
            importBackupDialog?.setOnItemSelected(::onBackupFileSelected)
        }
    }

    private fun onImportTranslation() {
        openTranslationContent.launch("*/*")
    }

    private fun onImportUSFM() {
        openUSFMContent.launch("*/*")
    }

    private fun importLocal(fileUri: Uri, mimeType: String) {
        when (mimeType) {
            IMPORT_TRANSLATION_MIME -> {
                importUri = fileUri
                doProjectImport(fileUri)
            }
            IMPORT_USFM_MIME -> {
                importUri = fileUri
                doUSFMImportUri(fileUri)
            }
            else -> Logger.e(TAG, "Unsupported import mime type: $mimeType")
        }
    }

    private fun onImportSourceText() {
        openDirectory.launch(null)
    }

    override fun onResume() {
        if (settingDeviceAlias && App.deviceNetworkAlias.isNotEmpty()) {
            settingDeviceAlias = false
            showP2PDialog()
        }
        super.onResume()
    }

    /**
     * Displays the p2p dialog
     */
    private fun showP2PDialog() {
        val dialog = ShareWithPeerDialog()
        val args = Bundle()
        args.putInt(ShareWithPeerDialog.ARG_OPERATION_MODE, ShareWithPeerDialog.MODE_CLIENT)
        args.putString(ShareWithPeerDialog.ARG_DEVICE_ALIAS, App.deviceNetworkAlias)
        dialog.arguments = args
        showDialogFragment(dialog, "share-dialog")
    }

    /**
     * this is to fix old method which when called in onResume() would create a
     * second dialog overlaying the first.  The first was actually not removed.
     * Doing a commit after the remove() and starting a second FragmentTransaction
     * seems to fix the duplicate dialog bug.
     *
     * @param dialog
     * @param tag
     */
    private fun showDialogFragment(dialog: DialogFragment, tag: String) {
        var backupFt = parentFragmentManager.beginTransaction()
        val backupPrev = parentFragmentManager.findFragmentByTag(tag)
        if (backupPrev != null) {
            backupFt.remove(backupPrev)
            backupFt.commit() // apply the remove
            backupFt = parentFragmentManager.beginTransaction() // start a new transaction
        }
        backupFt.addToBackStack(null)

        dialog.show(backupFt, tag)
    }

    /**
     * start task to import a project
     * @param importUri
     */
    private fun doProjectImport(importUri: Uri) {
        this.importUri = importUri
        viewModel.importProjectFromUri(
            importUri,
            mergeSelection == MergeOptions.OVERWRITE
        )
    }

    /**
     * import USFM uri
     * @param uri
     */
    private fun doUSFMImportUri(uri: Uri) {
        val intent = Intent(requireActivity(), ImportUsfmActivity::class.java)
        intent.putExtra(EXTRA_USFM_IMPORT_URI, uri.toString()) // flag that we are using Uri
        activityLauncher.launch(intent)
    }

    private fun doImportSourceText(uri: Uri) {
        viewModel.importSource(uri)
    }

    /**
     * let user know there was a merge conflict
     * @param targetTranslationID
     */
    private fun showMergeOverwritePrompt(targetTranslationID: String?) {
        dialogShown = DialogShown.MERGE_CONFLICT
        this.targetTranslationID = targetTranslationID
        val messageID =
            if (mergeConflicted) {
                R.string.import_merge_conflict_project_name
            } else {
                R.string.import_project_already_exists
            }
        val message = requireActivity().getString(messageID, targetTranslationID)

        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.merge_conflict_title)
            .setMessage(message)
            .setPositiveButton(R.string.merge_projects_label) { _, _ ->
                mergeSelection = MergeOptions.OVERWRITE
                if (mergeConflicted) {
                    doManualMerge()
                } else {
                    showImportResults(R.string.title_import_success, null)
                }
                dismiss()
            }
            .setNeutralButton(R.string.title_cancel) { _, _ ->
                resetToMasterBackup()
                dismiss()
            }
            .setNegativeButton(R.string.overwrite_projects_label) { _, _ ->
                resetToMasterBackup()

                // re-import with overwrite
                mergeSelection = MergeOptions.OVERWRITE
                doProjectImport(importUri!!)
                dismiss()
            }
            .setOnDismissListener { clearResults() }
            .show()
            .also(dialogs::add)
    }

    /**
     * restore original version
     */
    private fun resetToMasterBackup() {
        val mTargetTranslation = translator.getTargetTranslation(targetTranslationID)
        mTargetTranslation?.resetToMasterBackup()
    }

    /**
     * open review mode to let user resolve conflict
     */
    private fun doManualMerge() {
        // ask parent activity to navigate to target translation review mode with merge filter on
        val intent = Intent(activity, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslationID)
        args.putBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, true)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)
        startActivity(intent)
        dismiss()
    }

    /**
     * show the import results to user
     * @param textResId
     * @param filePath
     */
    private fun showImportResults(textResId: Int, filePath: String?) {
        var message = resources.getString(textResId)
        if (filePath != null) {
            message += "\n$filePath"
        }
        showImportResults(message)
    }

    /**
     * show the import results message to user
     * @param message
     */
    private fun showImportResults(message: String?) {
        dialogShown = DialogShown.SHOW_IMPORT_RESULTS
        dialogMessage = message
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.import_from_storage)
            .setMessage(message)
            .setPositiveButton(R.string.dismiss, null)
            .setOnDismissListener { clearResults() }
            .show()
            .also(dialogs::add)
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean(STATE_SETTING_DEVICE_ALIAS, settingDeviceAlias)
        out.putInt(STATE_DIALOG_SHOWN, dialogShown.value)
        out.putString(STATE_DIALOG_MESSAGE, dialogMessage)
        out.putString(STATE_DIALOG_TRANSLATION_ID, targetTranslationID)
        out.putInt(STATE_MERGE_SELECTION, mergeSelection!!.value)
        out.putBoolean(STATE_MERGE_CONFLICT, mergeConflicted)
        if (importUri != null) {
            out.putString(STATE_IMPORT_URL, importUri.toString())
        }

        super.onSaveInstanceState(out)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dialogs.forEach { it.dismiss() }
        dialogs.clear()
    }

    private fun clearResults() {
        dialogShown = DialogShown.NONE
        dialogMessage = null
        viewModel.clearResults()
    }

    /**
     * for keeping track which dialog is being shown for orientation changes (not for DialogFragments)
     */
    enum class DialogShown(val value: Int) {
        NONE(0),
        SHOW_IMPORT_RESULTS(1),
        MERGE_CONFLICT(2);

        companion object {
            fun fromInt(i: Int): DialogShown {
                for (b in entries) {
                    if (b.value == i) {
                        return b
                    }
                }
                return NONE
            }
        }
    }

    /**
     * for keeping track of user's merge selection
     */
    enum class MergeOptions(val value: Int) {
        NONE(0),
        OVERWRITE(1),
        MERGE(2);

        companion object {
            fun fromInt(i: Int): MergeOptions {
                for (b in entries) {
                    if (b.value == i) {
                        return b
                    }
                }
                return NONE
            }
        }
    }

    companion object {
        const val TAG: String = "importDialog"

        private const val IMPORT_TRANSLATION_MIME = "application/tstudio"
        private const val IMPORT_USFM_MIME = "text/usfm"
        private const val STATE_SETTING_DEVICE_ALIAS = "state_setting_device_alias"
        private const val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        private const val STATE_DIALOG_MESSAGE: String = "state_dialog_message"
        private const val STATE_DIALOG_TRANSLATION_ID: String = "state_dialog_translationID"
        private const val STATE_MERGE_SELECTION: String = "state_merge_selection"
        private const val STATE_MERGE_CONFLICT: String = "state_merge_conflict"
        private const val STATE_IMPORT_URL: String = "state_import_url"
    }
}
