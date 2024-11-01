package com.door43.translationstudio.ui.dialogs

import android.app.Activity
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
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.MergeConflictsHandler.OnMergeConflictListener
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Translator.Companion.TSTUDIO_EXTENSION
import com.door43.translationstudio.core.Translator.Companion.USFM_EXTENSION
import com.door43.translationstudio.databinding.DialogBackupBinding
import com.door43.translationstudio.ui.ProfileActivity
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.viewmodels.ExportViewModel
import com.door43.usecases.ExportProjects
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.util.FileUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.security.InvalidParameterException
import javax.inject.Inject

/**
 * Created by joel on 10/5/2015.
 */
@AndroidEntryPoint
class BackupDialog : DialogFragment() {
    private var settingDeviceAlias = false
    private var mDialogShown = DialogShown.NONE
    private var mAccessFile: String? = null
    private var mDialogMessage: String? = null

    private var progressDialog: ProgressHelper.ProgressDialog? = null

    @Inject lateinit var profile: Profile
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var translator: Translator
    @Inject lateinit var library: Door43Client

    private val viewModel: ExportViewModel by viewModels()

    private lateinit var targetTranslation: TargetTranslation
    private lateinit var exportTranslationLauncher: ActivityResultLauncher<Intent>
    private lateinit var exportUSFMLauncher: ActivityResultLauncher<Intent>

    private var _binding: DialogBackupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(true)

        exportTranslationLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                if (validateUriExtension(uri, TSTUDIO_EXTENSION)) {
                    viewModel.exportProject(uri)
                } else {
                    notifyBackupFailed(targetTranslation)
                }
            }
        }

        exportUSFMLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) {
                if (validateUriExtension(uri, USFM_EXTENSION)) {
                    viewModel.exportUSFM(uri)
                } else {
                    notifyBackupFailed(targetTranslation)
                }
            }
        }

        progressDialog = ProgressHelper.newInstance(
            parentFragmentManager,
            R.string.backup_to_sd,
            false
        )

        return dialog
    }

    private fun validateUriExtension(uri: Uri, extension: String): Boolean {
        val filename = FileUtilities.getUriDisplayName(requireContext(), uri)
        val filenameRegex = Regex(".*\\.$extension(\\s\\(\\d+\\))?$")
        return filename.matches(filenameRegex)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogBackupBinding.inflate(inflater, container, false)

        // get target translation to backup
        val args = arguments
        if (args != null && args.containsKey(ARG_TARGET_TRANSLATION_ID)) {
            val targetTranslationId = args.getString(ARG_TARGET_TRANSLATION_ID, null)
            viewModel.loadTargetTranslation(targetTranslationId)
        } else {
            throw InvalidParameterException("The target translation id was not specified")
        }

        setupObservers()

        if (savedInstanceState != null) {
            // check if returning from device alias dialog
            settingDeviceAlias = savedInstanceState.getBoolean(STATE_SETTING_DEVICE_ALIAS, false)
            mDialogShown = DialogShown.fromInt(
                savedInstanceState.getInt(
                    STATE_DIALOG_SHOWN,
                    DialogShown.NONE.value
                )
            )
            mAccessFile = savedInstanceState.getString(STATE_ACCESS_FILE, null)
            mDialogMessage = savedInstanceState.getString(STATE_DIALOG_MESSAGE, null)
            restoreDialogs()
        }

        with(binding) {
            logoutButton.setOnClickListener {
                // log out
                viewModel.logout()
                profile.logout()
                val logoutIntent = Intent(requireContext(), ProfileActivity::class.java)
                startActivity(logoutIntent)
            }

            backupToCloud.setOnClickListener {
                if (App.isNetworkAvailable) {
                    // make sure we have a gogs user
                    if (profile.gogsUser == null) {
                        showDoor43LoginDialog()
                        return@setOnClickListener
                    }
                    pullTargetTranslation(MergeStrategy.RECURSIVE)
                } else {
                    // replaced snack popup which could be hidden behind dialog
                    showNoInternetDialog()
                }
            }

            exportToPdf.setOnClickListener {
                val printDialog = PrintDialog()
                val printArgs = Bundle()
                printArgs.putString(PrintDialog.ARG_TARGET_TRANSLATION_ID, targetTranslation.id)
                printDialog.arguments = printArgs
                showDialogFragment(printDialog, PrintDialog.TAG)
            }

            exportToProject.setOnClickListener { showExportProjectPrompt() }

            exportToUsfm.setOnClickListener { showExportToUsfmPrompt() }

            if (viewModel.translation.value?.isObsProject == true) {
                exportToUsfmSeparator.visibility = View.GONE
                exportToUsfm.visibility = View.GONE
            }

            backupToDevice.setOnClickListener {
                // TODO: 11/18/2015 eventually we need to support bluetooth as well as an adhoc network
                showDeviceNetworkAliasDialog()
            }

            backupToApp.setOnClickListener {
                viewModel.exportToApp()
            }

            dismissButton.setOnClickListener { dismiss() }
        }

        return binding.root
    }

    /**
     * restore the dialogs that were displayed before rotation
     */
    private fun restoreDialogs() {
        val hand = Handler(Looper.getMainLooper())
        // wait for backup dialog to be drawn before showing popups
        hand.post {
            when (mDialogShown) {
                DialogShown.PUSH_REJECTED -> showPushRejection(targetTranslation)
                DialogShown.AUTH_FAILURE -> showAuthFailure()
                DialogShown.BACKUP_FAILED -> showUploadFailedDialog(targetTranslation)
                DialogShown.NO_INTERNET -> showNoInternetDialog()
                DialogShown.SHOW_BACKUP_RESULTS -> showBackupResults(mDialogMessage)
                DialogShown.SHOW_PUSH_SUCCESS -> showPushSuccess(mDialogMessage)
                DialogShown.MERGE_CONFLICT -> showMergeConflict(targetTranslation)
                DialogShown.EXPORT_TO_USFM_RESULTS -> showUsfmExportResults(mDialogMessage)
                DialogShown.NONE -> {}
            }
        }
    }

    private fun setupObservers() {
        viewModel.translation.observe(this) {
            targetTranslation = it
                ?: throw NullPointerException("Target translation not found.")
        }
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
        viewModel.exportResult.observe(this) {
            it?.let { result ->
                when (result.exportType) {
                    ExportProjects.ExportType.USFM -> {
                        val message = if (result.success) {
                            val format = resources.getString(R.string.export_success)
                            String.format(
                                format,
                                FileUtilities.getUriDisplayName(requireContext(), result.uri)
                            )
                        } else {
                            resources.getString(R.string.export_failed)
                        }
                        Logger.i(TAG, "USFM export success = " + result.success)
                        showUsfmExportResults(message)
                    }
                    ExportProjects.ExportType.PROJECT -> {
                        if (result.success) {
                            showBackupResults(R.string.backup_success, result.uri)
                        } else {
                            showBackupResults(R.string.backup_failed, result.uri)
                        }
                        Logger.i(TAG, "Project export success = " + result.success)
                    }
                    else -> {}
                }
                viewModel.clearResults()
            }
        }
        viewModel.pullTranslationResult.observe(this) {
            it?.let { result ->
                val status = result.status
                // TRICKY: we continue to push for unknown status in case
                // the repo was just created (the missing branch is an error)
                // the pull task will catch any errors
                when (status) {
                    PullTargetTranslation.Status.UP_TO_DATE,
                    PullTargetTranslation.Status.UNKNOWN -> {
                        Logger.i(
                            this.javaClass.name,
                            "Changes on the server were synced with " + targetTranslation.id
                        )
                        lifecycleScope.launch(Dispatchers.Main) {
                            viewModel.pushTargetTranslation()
                        }
                    }
                    PullTargetTranslation.Status.AUTH_FAILURE -> {
                        Logger.i(this.javaClass.name, "Authentication failed")
                        // if we have already tried ask the user if they would like to try again
                        if (directoryProvider.hasSSHKeys()) {
                            showAuthFailure()
                        } else {
                            viewModel.registerSSHKeys(false)
                        }
                    }
                    PullTargetTranslation.Status.NO_REMOTE_REPO -> {
                        Logger.i(
                            this.javaClass.name,
                            "The repository " + targetTranslation.id + " could not be found"
                        )
                        // create missing repo
                        viewModel.createRepository()
                    }
                    PullTargetTranslation.Status.MERGE_CONFLICTS -> {
                        Logger.i(
                            this.javaClass.name,
                            "The server contains conflicting changes for " + targetTranslation.id
                        )
                        MergeConflictsHandler.backgroundTestForConflictedChunks(
                            targetTranslation.id,
                            translator,
                            object : OnMergeConflictListener {
                                override fun onNoMergeConflict(targetTranslationId: String) {
                                    // probably the manifest or license gave a false positive
                                    Logger.i(
                                        this.javaClass.name,
                                        "Changes on the server were synced with " + targetTranslation.id
                                    )
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        viewModel.pushTargetTranslation()
                                    }
                                }

                                override fun onMergeConflict(targetTranslationId: String) {
                                    showMergeConflict(targetTranslation)
                                }
                            })
                    }
                    else -> {
                        notifyBackupFailed(targetTranslation)
                    }
                }
            }
        }
        viewModel.pushTranslationResult.observe(this) {
            it?.let { result ->
                when {
                    result.status == PushTargetTranslation.Status.OK -> {
                        Logger.i(
                            this.javaClass.name,
                            "The target translation " + targetTranslation.id + " was pushed to the server"
                        )
                        showPushSuccess(result.message)
                    }
                    result.status == PushTargetTranslation.Status.AUTH_FAILURE -> {
                        Logger.i(this.javaClass.name, "Authentication failed")
                        showAuthFailure()
                    }
                    result.status.isRejected -> {
                        Logger.i(this.javaClass.name, "Push Rejected")
                        showPushRejection(targetTranslation)
                    }
                    else -> notifyBackupFailed(targetTranslation)
                }
            }
        }
        viewModel.registeredSSHKeys.observe(this) {
            it?.let { registered ->
                if (registered) {
                    Logger.i(this.javaClass.name, "SSH keys were registered with the server")
                    // try to push again
                    pullTargetTranslation(MergeStrategy.RECURSIVE)
                } else {
                    notifyBackupFailed(targetTranslation)
                }
            }
        }
        viewModel.repoCreated.observe(this) {
            it?.let { created ->
                if (created) {
                    Logger.i(
                        this.javaClass.name,
                        "A new repository " + targetTranslation.id + " was created on the server"
                    )
                    pullTargetTranslation(MergeStrategy.RECURSIVE)
                } else {
                    notifyBackupFailed(targetTranslation)
                }
            }
        }
        viewModel.exportedToApp.observe(this) {
            it?.let { exportFile ->
                if (exportFile.exists()) {
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        "${requireActivity().application.packageName}.fileprovider",
                        exportFile
                    )
                    val i = Intent(Intent.ACTION_SEND)
                    i.setType("application/zip")
                    i.putExtra(Intent.EXTRA_STREAM, uri)
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(Intent.createChooser(i, "Send to:"))
                } else {
                    val snack = Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        R.string.translation_export_failed,
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        requireContext().resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                }
            }
        }
    }

    /**
     * display confirmation prompt before USFM export (also allow entry of filename
     */
    private fun showExportToUsfmPrompt() {
        val bookData = ExportProjects.BookData.generate(targetTranslation, library)
        val defaultFileName = bookData.defaultUSFMFileName
        showExportPathPrompt(defaultFileName, EXPORT_USFM_MIME_TYPE)
    }

    /**
     * display confirmation prompt before USFM export (also allow entry of filename
     */
    private fun showExportProjectPrompt() {
        val filename = targetTranslation.id + "." + TSTUDIO_EXTENSION
        showExportPathPrompt(filename, EXPORT_TSTUDIO_MIME_TYPE)
    }

    /**
     * display confirmation prompt before USFM export (also allow entry of filename
     * @param defaultFileName
     */
    private fun showExportPathPrompt(defaultFileName: String, mimeType: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.setType(mimeType)
        intent.putExtra(Intent.EXTRA_TITLE, defaultFileName)
        intent.putExtra(
            Intent.EXTRA_MIME_TYPES,
            arrayOf(mimeType, EXPORT_GENERIC_MIME)
        )
        when (mimeType) {
            EXPORT_TSTUDIO_MIME_TYPE -> exportTranslationLauncher.launch(intent)
            EXPORT_USFM_MIME_TYPE -> exportUSFMLauncher.launch(intent)
        }
    }

    /**
     * show USFM export results
     * @param message
     */
    private fun showUsfmExportResults(message: String?) {
        mDialogShown = DialogShown.EXPORT_TO_USFM_RESULTS
        mDialogMessage = message

        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.title_export_usfm)
            .setMessage(message)
            .setPositiveButton(R.string.dismiss) { _, _ ->
                mDialogShown = DialogShown.NONE
            }
            .setOnDismissListener {
                mDialogShown = DialogShown.NONE
                viewModel.clearResults()
            }
            .show()
    }

    private fun showDoor43LoginDialog() {
        val dialog = Door43LoginDialog()
        showDialogFragment(dialog, Door43LoginDialog.TAG)
    }

    private fun showDeviceNetworkAliasDialog() {
        if (App.isNetworkAvailable) {
            if (App.deviceNetworkAlias.isEmpty()) {
                showDeviceNetworkAliasDialogSub()
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

    private fun showDeviceNetworkAliasDialogSub() {
        settingDeviceAlias = true
        val dialog = DeviceNetworkAliasDialog()
        showDialogFragment(dialog, "device-name-dialog")
    }

    private fun showBackupResults(textResId: Int, fileUri: Uri?) {
        var message = resources.getString(textResId)
        if (fileUri != null) {
            message += "\n${FileUtilities.getUriDisplayName(requireContext(), fileUri)}"
        }
        showBackupResults(message)
    }

    private fun showBackupResults(message: String?) {
        mDialogShown = DialogShown.SHOW_BACKUP_RESULTS
        mDialogMessage = message
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.backup_to_sd)
            .setMessage(message)
            .setNeutralButton(R.string.dismiss) { _, _ ->
                mDialogShown = DialogShown.NONE
            }
            .setOnDismissListener { viewModel.clearResults() }
            .show()
    }

    override fun onResume() {
        if (settingDeviceAlias && App.deviceNetworkAlias.isNotEmpty()) {
            settingDeviceAlias = false
            showP2PDialog()
        }

        val userText = resources.getString(R.string.current_user, profile.currentUser)
        binding.currentUser.text = userText

        super.onResume()
    }

    /**
     * Displays the dialog for p2p sharing
     */
    private fun showP2PDialog() {
        val dialog = ShareWithPeerDialog()
        val args = Bundle()
        args.putInt(ShareWithPeerDialog.ARG_OPERATION_MODE, ShareWithPeerDialog.MODE_SERVER)
        args.putString(ShareWithPeerDialog.ARG_TARGET_TRANSLATION, targetTranslation.id)
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

    private fun showPushSuccess(message: String?) {
        mDialogShown = DialogShown.SHOW_PUSH_SUCCESS
        mDialogMessage = message
        val apiURL = prefRepository.getDefaultPref(
            SettingsActivity.KEY_PREF_READER_SERVER,
            resources.getString(R.string.pref_default_reader_server)
        )
        val url = Uri.parse(
            apiURL + "/" + profile.gogsUser?.username + "/" + targetTranslation.id
        )
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.upload_complete)
            .setMessage(
                String.format(
                    resources.getString(R.string.project_uploaded_to),
                    url.toString()
                )
            )
            .setNegativeButton(R.string.dismiss, null)
            .setPositiveButton(R.string.view_online) { _, _ ->
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        url
                    )
                )
            }
            .setNeutralButton(R.string.label_details) { _, _ ->
                AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                    .setTitle(R.string.project_uploaded)
                    .setMessage(message)
                    .setPositiveButton(R.string.view_online) { _, _ ->
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                url
                            )
                        )
                    }
                    .setNeutralButton(R.string.dismiss, null)
                    .setOnDismissListener {
                        viewModel.clearResults()
                        mDialogShown = DialogShown.NONE
                    }
                    .show()
            }
            .setOnDismissListener {
                viewModel.clearResults()
                mDialogShown = DialogShown.NONE
            }
            .show()
    }

    /**
     * let user know there was a merge conflict
     * @param targetTranslation
     */
    private fun showMergeConflict(targetTranslation: TargetTranslation?) {
        mDialogShown = DialogShown.MERGE_CONFLICT

        val projectID = targetTranslation!!.projectId
        val message = String.format(
            resources.getString(R.string.merge_request),
            projectID, targetTranslation.targetLanguageName
        )

        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.change_detected)
            .setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ ->
                mDialogShown = DialogShown.NONE
                doManualMerge()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                mDialogShown = DialogShown.NONE
                resetToMasterBackup(targetTranslation)
                this@BackupDialog.dismiss()
            }
            .setOnDismissListener { viewModel.clearResults() }
            .show()
    }

    /**
     * open review mode to let user resolve conflict
     */
    private fun doManualMerge() {
        if (activity is TargetTranslationActivity) {
            (activity as? TargetTranslationActivity)?.redrawTarget()
            this@BackupDialog.dismiss()
            // TODO: 4/20/16 it would be nice to navigate directly to the first conflict
        } else {
            // ask parent activity to navigate to a new activity
            val intent = Intent(activity, TargetTranslationActivity::class.java)
            val args = Bundle()
            args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
            // TODO: 4/20/16 it would be nice to navigate directly to the first conflict
//                args.putString(App.EXTRA_CHAPTER_ID, chapterId);
//                args.putString(App.EXTRA_FRAME_ID, frameId);
            args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
            intent.putExtras(args)
            startActivity(intent)
            this@BackupDialog.dismiss()
        }
    }

    private fun pullTargetTranslation(strategy: MergeStrategy) {
        viewModel.pullTargetTranslation(strategy)
    }

    /**
     * Displays a dialog to the user indicating the publish failed.
     * Includes an option to submit a bug report
     * @param targetTranslation
     */
    private fun notifyBackupFailed(targetTranslation: TargetTranslation) {
        if (!App.isNetworkAvailable) {
            showNoInternetDialog()
        } else {
            showUploadFailedDialog(targetTranslation)
        }
    }

    /**
     * show internet not available dialog
     */
    private fun showNoInternetDialog() {
        mDialogShown = DialogShown.NO_INTERNET
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.upload_failed)
            .setMessage(R.string.internet_not_available)
            .setPositiveButton(R.string.label_ok) { _, _ ->
                mDialogShown = DialogShown.NONE
            }
            .show()
    }

    /**
     * show general upload failed dialog
     * @param targetTranslation
     */
    private fun showUploadFailedDialog(targetTranslation: TargetTranslation) {
        mDialogShown = DialogShown.BACKUP_FAILED
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.backup)
            .setMessage(R.string.upload_failed)
            .setPositiveButton(R.string.dismiss) { _, _ ->
                mDialogShown = DialogShown.NONE
            }
            .setNeutralButton(R.string.menu_bug) { _, _ ->
                mDialogShown = DialogShown.NONE
                showFeedbackDialog(targetTranslation)
            }
            .setOnDismissListener { viewModel.clearResults() }
            .show()
    }

    private fun showFeedbackDialog(targetTranslation: TargetTranslation) {
        val project = viewModel.getProject(targetTranslation)
        // open bug report dialog
        val feedbackDialog = FeedbackDialog()
        val args = Bundle()
        val message = "Failed to upload the translation of ${project.name}" +
                "into ${targetTranslation.targetLanguageName}.\n" +
                "targetTranslation: ${targetTranslation.id}" +
                "\n--------\n\n"
        args.putString(FeedbackDialog.ARG_MESSAGE, message)
        feedbackDialog.arguments = args
        showDialogFragment(feedbackDialog, "feedback-dialog")
    }

    private fun showPushRejection(targetTranslation: TargetTranslation) {
        mDialogShown = DialogShown.PUSH_REJECTED

        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.upload_failed)
            .setMessage(R.string.push_rejected)
            .setPositiveButton(R.string.yes) { _, _ ->
                mDialogShown = DialogShown.NONE
                doManualMerge()
            }
            .setNegativeButton(R.string.no) { _, _ ->
                mDialogShown = DialogShown.NONE
                resetToMasterBackup(targetTranslation)
                this@BackupDialog.dismiss()
            }
            .setOnDismissListener { viewModel.clearResults() }
            .show()
    }

    private fun resetToMasterBackup(targetTranslation: TargetTranslation): Boolean {
        try { // restore state before the pull
            val git = targetTranslation.repo.git
            val resetCommand = git.reset()
            resetCommand.setMode(ResetCommand.ResetType.HARD)
                .setRef("backup-master")
                .call()
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
        return true
    }

    private fun showAuthFailure() {
        mDialogShown = DialogShown.AUTH_FAILURE
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.upload_failed)
            .setMessage(R.string.auth_failure_retry)
            .setPositiveButton(R.string.yes) { _, _ ->
                mDialogShown = DialogShown.NONE
                viewModel.registerSSHKeys(true)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                mDialogShown = DialogShown.NONE
                notifyBackupFailed(targetTranslation)
            }.show()
    }

    override fun onSaveInstanceState(out: Bundle) {
        // remember if the device alias dialog is open
        out.putBoolean(STATE_SETTING_DEVICE_ALIAS, settingDeviceAlias)
        out.putInt(STATE_DO_MERGE, mDialogShown.value)
        if (mAccessFile != null) {
            out.putString(STATE_ACCESS_FILE, mAccessFile)
        }
        out.putInt(STATE_DIALOG_SHOWN, mDialogShown.value)
        if (mDialogMessage != null) {
            out.putString(STATE_DIALOG_MESSAGE, mDialogMessage)
        }

        super.onSaveInstanceState(out)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * for keeping track which dialog is being shown for orientation changes (not for DialogFragments)
     */
    enum class DialogShown(val value: Int) {
        NONE(0),
        PUSH_REJECTED(1),
        AUTH_FAILURE(2),
        BACKUP_FAILED(3),
        SHOW_BACKUP_RESULTS(5),
        SHOW_PUSH_SUCCESS(6),
        MERGE_CONFLICT(7),
        EXPORT_TO_USFM_RESULTS(9),
        NO_INTERNET(10);

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

    companion object {
        const val TAG: String = "BackupDialog"
        const val ARG_TARGET_TRANSLATION_ID: String = "target_translation_id"

        private const val STATE_SETTING_DEVICE_ALIAS = "state_setting_device_alias"
        private const val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        private const val STATE_DO_MERGE: String = "state_do_merge"
        private const val STATE_ACCESS_FILE: String = "state_access_file"
        private const val STATE_DIALOG_MESSAGE: String = "state_dialog_message"
        private const val EXPORT_GENERIC_MIME = "application/octet-stream"
        private const val EXPORT_TSTUDIO_MIME_TYPE: String = "application/tstudio"
        private const val EXPORT_USFM_MIME_TYPE: String = "text/usfm"
    }
}
