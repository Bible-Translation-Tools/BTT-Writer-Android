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
import android.widget.LinearLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ExportUsfm.BookData
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.MergeConflictsHandler.OnMergeConflictListener
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.DialogBackupBinding
import com.door43.translationstudio.tasks.CreateRepositoryTask
import com.door43.translationstudio.tasks.ExportProjectTask
import com.door43.translationstudio.tasks.ExportProjectTask.ExportResults
import com.door43.translationstudio.tasks.ExportToUsfmTask
import com.door43.translationstudio.tasks.LogoutTask
import com.door43.translationstudio.tasks.PullTargetTranslationTask
import com.door43.translationstudio.tasks.PushTargetTranslationTask
import com.door43.translationstudio.tasks.RegisterSSHKeysTask
import com.door43.translationstudio.ui.ProfileActivity
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.util.FileUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.tools.logger.Logger
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.SimpleTaskWatcher
import org.unfoldingword.tools.taskmanager.TaskManager
import java.io.File
import java.security.InvalidParameterException

/**
 * Created by joel on 10/5/2015.
 */
class BackupDialog : DialogFragment(), SimpleTaskWatcher.OnFinishedListener {
    private lateinit var targetTranslation: TargetTranslation
    private var taskWatcher: SimpleTaskWatcher? = null
    private var settingDeviceAlias = false
    private var mBackupToCloudButton: LinearLayout? = null
    private var mDialogShown = DialogShown.NONE
    private var mAccessFile: String? = null
    private var mDialogMessage: String? = null
    private var isOutputToDocumentFile = false
    private var mDestinationFolderUri: Uri? = null

    private lateinit var binding: DialogBackupBinding
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setCanceledOnTouchOutside(true)

        activityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                saveFile(data!!.data)
            }
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogBackupBinding.inflate(inflater, container, false)

        // get target translation to backup
        val args = arguments
        if (args != null && args.containsKey(ARG_TARGET_TRANSLATION_ID)) {
            val targetTranslationId = args.getString(ARG_TARGET_TRANSLATION_ID, null)
            App.translator.getTargetTranslation(targetTranslationId)?.let {
                targetTranslation = it
            } ?: throw InvalidParameterException(
                "The target translation '$targetTranslationId' is invalid"
            )
        } else {
            throw InvalidParameterException("The target translation id was not specified")
        }

        targetTranslation.setDefaultContributor(App.profile?.nativeSpeaker)
        val filename = targetTranslation.id + "." + Translator.TSTUDIO_EXTENSION
        initProgressWatcher(R.string.backup)

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
            isOutputToDocumentFile =
                savedInstanceState.getBoolean(STATE_OUTPUT_TO_DOCUMENT_FILE, false)
            mDestinationFolderUri =
                Uri.parse(savedInstanceState.getString(STATE_OUTPUT_FOLDER_URI, ""))
            restoreDialogs()
        }

        with(binding) {
            logoutButton.setOnClickListener {
                // log out
                val user = App.profile?.gogsUser
                val task = LogoutTask(user)
                taskWatcher?.watch(task)
                TaskManager.addTask(task, LogoutTask.TASK_ID)

                App.profile = null
                val logoutIntent = Intent(activity, ProfileActivity::class.java)
                startActivity(logoutIntent)
            }

            dismissButton.setOnClickListener { dismiss() }

            backupToDevice.setOnClickListener {
                // TODO: 11/18/2015 eventually we need to support bluetooth as well as an adhoc network
                showDeviceNetworkAliasDialog()
            }

            backupToCloud.setOnClickListener {
                if (App.isNetworkAvailable) {
                    // make sure we have a gogs user
                    if (App.profile?.gogsUser == null) {
                        showDoor43LoginDialog()
                        return@setOnClickListener
                    }
                    doPullTargetTranslationTask(targetTranslation, MergeStrategy.RECURSIVE)
                } else {
                    // replaced snack popup which could be hidden behind dialog
                    showNoInternetDialog()
                }
            }

            exportToPdf.setOnClickListener {
                val printDialog = PrintDialog()
                val printArgs = Bundle()
                printArgs.putString(PrintDialog.ARG_TARGET_TRANSLATION_ID, targetTranslation!!.id)
                printDialog.arguments = printArgs
                showDialogFragment(printDialog, PrintDialog.TAG)
            }

            backupToSd.setOnClickListener { showExportProjectPrompt() }

            exportToUsfm.setOnClickListener { showExportToUsfmPrompt() }

            if (targetTranslation.isObsProject) {
                exportToUsfmSeparator.visibility = View.GONE
                exportToUsfm.visibility = View.GONE
            }

            backupToApp.setOnClickListener {
                val exportFile = File(App.sharingDir, filename)
                try {
                    App.translator.exportArchive(targetTranslation, exportFile)
                } catch (e: Exception) {
                    Logger.e(
                        TAG,
                        "Failed to export the target translation " + targetTranslation!!.id,
                        e
                    )
                }
                if (exportFile.exists()) {
                    val u = FileProvider.getUriForFile(
                        requireActivity(),
                        "com.door43.translationstudio.fileprovider",
                        exportFile
                    )
                    val i = Intent(Intent.ACTION_SEND)
                    i.setType("application/zip")
                    i.putExtra(Intent.EXTRA_STREAM, u)
                    startActivity(Intent.createChooser(i, "Email:"))
                } else {
                    val snack = Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        R.string.translation_export_failed,
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                    snack.show()
                }
            }
        }

        // connect to existing tasks
        val pullTask =
            TaskManager.getTask(PullTargetTranslationTask.TASK_ID) as? PullTargetTranslationTask
        val keysTask = TaskManager.getTask(RegisterSSHKeysTask.TASK_ID) as? RegisterSSHKeysTask
        val repoTask = TaskManager.getTask(CreateRepositoryTask.TASK_ID) as? CreateRepositoryTask
        val pushTask =
            TaskManager.getTask(PushTargetTranslationTask.TASK_ID) as? PushTargetTranslationTask
        val projectExportTask = TaskManager.getTask(ExportProjectTask.TASK_ID) as? ExportProjectTask
        val usfmExportTask = TaskManager.getTask(ExportToUsfmTask.TASK_ID) as? ExportToUsfmTask

        if (pullTask != null) {
            taskWatcher!!.watch(pullTask)
        } else if (keysTask != null) {
            taskWatcher!!.watch(keysTask)
        } else if (repoTask != null) {
            taskWatcher!!.watch(repoTask)
        } else if (pushTask != null) {
            taskWatcher!!.watch(pushTask)
        } else if (projectExportTask != null) {
            taskWatcher!!.watch(projectExportTask)
        } else if (usfmExportTask != null) {
            taskWatcher!!.watch(usfmExportTask)
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
                DialogShown.EXPORT_TO_USFM_PROMPT -> showExportToUsfmPrompt()
                DialogShown.EXPORT_PROJECT_PROMPT -> showExportProjectPrompt()
                DialogShown.EXPORT_TO_USFM_RESULTS -> showUsfmExportResults(mDialogMessage)
                DialogShown.NONE -> {}
                else -> Logger.e(TAG, "Unsupported restore dialog: $mDialogShown")
            }
        }
    }

    /**
     * display confirmation prompt before USFM export (also allow entry of filename
     */
    private fun showExportToUsfmPrompt() {
        mDialogShown = DialogShown.EXPORT_TO_USFM_PROMPT
        val bookData = BookData.generate(targetTranslation)
        val defaultFileName = bookData.defaultUsfmFileName
        showExportPathPrompt(defaultFileName, "text/" + Translator.USFM_EXTENSION)
    }

    /**
     * display confirmation prompt before USFM export (also allow entry of filename
     */
    private fun showExportProjectPrompt() {
        mDialogShown = DialogShown.EXPORT_PROJECT_PROMPT
        val filename = targetTranslation!!.id + "." + Translator.TSTUDIO_EXTENSION
        showExportPathPrompt(filename, "application/" + Translator.TSTUDIO_EXTENSION)
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
        activityResultLauncher.launch(intent)
    }

    private fun saveFile(targetUri: Uri?) {
        val isUsfmExport = (mDialogShown == DialogShown.EXPORT_TO_USFM_PROMPT)
        if (isUsfmExport) {
            saveToUsfm(targetTranslation, targetUri)
        } else {
            saveProjectFile(targetTranslation, targetUri)
        }
    }

    /**
     * save to usfm file and give success notification
     * @param targetTranslation
     * @return false if not forced and file already is present
     */
    private fun saveToUsfm(targetTranslation: TargetTranslation?, fileUri: Uri?) {
        initProgressWatcher(R.string.exporting)
        val usfmExportTask = ExportToUsfmTask(activity, targetTranslation, fileUri)
        taskWatcher!!.watch(usfmExportTask)
        TaskManager.addTask(usfmExportTask, ExportToUsfmTask.TASK_ID)
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
            .show()
    }

    private fun showDoor43LoginDialog() {
        val dialog = Door43LoginDialog()
        showDialogFragment(dialog, Door43LoginDialog.TAG)
    }

    private fun showDeviceNetworkAliasDialog() {
        if (App.isNetworkAvailable) {
            if (App.deviceNetworkAlias == null) {
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

    /**
     * back up project - will try to write to user selected destination
     * @param fileUri
     * @return false if not forced and file already is present
     */
    private fun saveProjectFile(targetTranslation: TargetTranslation?, fileUri: Uri?) {
        initProgressWatcher(R.string.exporting)
        val sdExportTask = ExportProjectTask(activity, fileUri, targetTranslation)
        taskWatcher!!.watch(sdExportTask)
        TaskManager.addTask(sdExportTask, ExportProjectTask.TASK_ID)
    }

    /**
     * creates a new progress watcher with desired title
     * @param titleID
     */
    private fun initProgressWatcher(titleID: Int) {
        taskWatcher?.stop()
        taskWatcher = SimpleTaskWatcher(activity, titleID).apply {
            setOnFinishedListener(this@BackupDialog)
        }
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
            .show()
    }

    override fun onResume() {
        if (settingDeviceAlias && App.deviceNetworkAlias != null) {
            settingDeviceAlias = false
            showP2PDialog()
        }

        val userText = resources.getString(R.string.current_user, ProfileActivity.getCurrentUser())
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

    override fun onFinished(task: ManagedTask) {
        taskWatcher?.stop()
        if (task is PullTargetTranslationTask) {
            //            mRemoteURL = pullTask.getSourceURL();
            val status = task.status
            //  TRICKY: we continue to push for unknown status in case the repo was just created (the missing branch is an error)
            // the pull task will catch any errors
            if (status == PullTargetTranslationTask.Status.UP_TO_DATE
                || status == PullTargetTranslationTask.Status.UNKNOWN
            ) {
                Logger.i(
                    this.javaClass.name,
                    "Changes on the server were synced with " + targetTranslation.id
                )

                initProgressWatcher(R.string.backup)
                val pushtask = PushTargetTranslationTask(targetTranslation)
                taskWatcher?.watch(pushtask)
                TaskManager.addTask(pushtask, PushTargetTranslationTask.TASK_ID)
            } else if (status == PullTargetTranslationTask.Status.AUTH_FAILURE) {
                Logger.i(this.javaClass.name, "Authentication failed")
                // if we have already tried ask the user if they would like to try again
                if (App.hasSSHKeys()) {
                    showAuthFailure()
                    return
                }

                initProgressWatcher(R.string.backup)
                val keyTask = RegisterSSHKeysTask(false)
                taskWatcher?.watch(keyTask)
                TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID)
            } else if (status == PullTargetTranslationTask.Status.NO_REMOTE_REPO) {
                Logger.i(
                    this.javaClass.name,
                    "The repository " + targetTranslation.id + " could not be found"
                )
                // create missing repo
                initProgressWatcher(R.string.backup)
                val repoTask = CreateRepositoryTask(targetTranslation)
                taskWatcher?.watch(repoTask)
                TaskManager.addTask(repoTask, CreateRepositoryTask.TASK_ID)
            } else if (status == PullTargetTranslationTask.Status.MERGE_CONFLICTS) {
                Logger.i(
                    this.javaClass.name,
                    "The server contains conflicting changes for " + targetTranslation.id
                )
                MergeConflictsHandler.backgroundTestForConflictedChunks(
                    targetTranslation.id,
                    object : OnMergeConflictListener {
                        override fun onNoMergeConflict(targetTranslationId: String) {
                            // probably the manifest or license gave a false positive
                            Logger.i(
                                this.javaClass.name,
                                "Changes on the server were synced with " + targetTranslation.id
                            )

                            initProgressWatcher(R.string.backup)
                            val pushTask = PushTargetTranslationTask(targetTranslation)
                            taskWatcher?.watch(pushTask)
                            TaskManager.addTask(pushTask, PushTargetTranslationTask.TASK_ID)
                        }

                        override fun onMergeConflict(targetTranslationId: String) {
                            showMergeConflict(targetTranslation)
                        }
                    })
            } else {
                notifyBackupFailed(targetTranslation)
            }
        } else if (task is RegisterSSHKeysTask) {
            if (task.isSuccess) {
                Logger.i(this.javaClass.name, "SSH keys were registered with the server")
                // try to push again
                doPullTargetTranslationTask(targetTranslation, MergeStrategy.RECURSIVE)
            } else {
                notifyBackupFailed(targetTranslation)
            }
        } else if (task is CreateRepositoryTask) {
            if (task.isSuccess) {
                Logger.i(
                    this.javaClass.name,
                    "A new repository " + targetTranslation.id + " was created on the server"
                )
                doPullTargetTranslationTask(targetTranslation, MergeStrategy.RECURSIVE)
            } else {
                notifyBackupFailed(targetTranslation)
            }
        } else if (task is PushTargetTranslationTask) {
            val status = task.status
            val message = task.message

            if (status == PushTargetTranslationTask.Status.OK) {
                Logger.i(
                    this.javaClass.name,
                    "The target translation " + targetTranslation.id + " was pushed to the server"
                )
                showPushSuccess(message)
            } else if (status.isRejected) {
                Logger.i(this.javaClass.name, "Push Rejected")
                showPushRejection(targetTranslation)
            } else if (status == PushTargetTranslationTask.Status.AUTH_FAILURE) {
                Logger.i(this.javaClass.name, "Authentication failed")
                showAuthFailure()
            } else {
                notifyBackupFailed(targetTranslation)
            }
        } else if (task is ExportProjectTask) {
            val results = task.result as ExportResults

            Logger.i(TAG, "Project export success = " + results.success)
            if (results.success) {
                showBackupResults(R.string.backup_success, results.fileUri)
            } else {
                showBackupResults(R.string.backup_failed, results.fileUri)
            }
        } else if (task is ExportToUsfmTask) {
            val exportFile = task.result as? Uri
            val success = (exportFile != null)

            val message: String
            if (success) {
                val format = resources.getString(R.string.export_success)
                message =
                    String.format(
                        format,
                        FileUtilities.getUriDisplayName(requireContext(), exportFile)
                    )
            } else {
                message = resources.getString(R.string.export_failed)
            }

            showUsfmExportResults(message)
        }
    }

    private fun showPushSuccess(message: String?) {
        mDialogShown = DialogShown.SHOW_PUSH_SUCCESS
        mDialogMessage = message
        val apiURL = App.getPref(
            SettingsActivity.KEY_PREF_READER_SERVER,
            App.getRes(R.string.pref_default_reader_server)
        )
        val url =
            Uri.parse(apiURL + "/" + App.profile?.gogsUser?.username + "/" + targetTranslation.id)
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.upload_complete)
            .setMessage(
                String.format(
                    resources.getString(R.string.project_uploaded_to),
                    url.toString()
                )
            )
            .setNegativeButton(R.string.dismiss) { _, _ ->
                mDialogShown = DialogShown.NONE
            }
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
                    .setNeutralButton(R.string.dismiss) { _, _ ->
                        mDialogShown = DialogShown.NONE
                    }
                    .show()
            }.show()
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
            }.show()
    }

    /**
     * open review mode to let user resolve conflict
     */
    private fun doManualMerge() {
        if (activity is TargetTranslationActivity) {
            (activity as TargetTranslationActivity?)!!.redrawTarget()
            this@BackupDialog.dismiss()
            // TODO: 4/20/16 it would be nice to navigate directly to the first conflict
        } else {
            // ask parent activity to navigate to a new activity
            val intent = Intent(activity, TargetTranslationActivity::class.java)
            val args = Bundle()
            args.putString(App.EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
            // TODO: 4/20/16 it would be nice to navigate directly to the first conflict
//                args.putString(App.EXTRA_CHAPTER_ID, chapterId);
//                args.putString(App.EXTRA_FRAME_ID, frameId);
            args.putInt(App.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
            intent.putExtras(args)
            startActivity(intent)
            this@BackupDialog.dismiss()
        }
    }

    private fun doPullTargetTranslationTask(
        targetTranslation: TargetTranslation?,
        theirs: MergeStrategy
    ) {
        initProgressWatcher(R.string.backup)
        val pullTask = PullTargetTranslationTask(targetTranslation, theirs, null)
        taskWatcher?.watch(pullTask)
        TaskManager.addTask(pullTask, PullTargetTranslationTask.TASK_ID)
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
            }.show()
    }

    private fun showFeedbackDialog(targetTranslation: TargetTranslation) {
        val project = App.library
            ?.index()
            ?.getProject(
                "en",
                targetTranslation.projectId,
                true
            )

        // open bug report dialog
        val feedbackDialog = FeedbackDialog()
        val args = Bundle()
        val message =
            """Failed to upload the translation of ${project?.name} into ${targetTranslation.targetLanguageName}.
targetTranslation: ${targetTranslation.id}
--------

"""
        args.putString(FeedbackDialog.ARG_MESSAGE, message)
        feedbackDialog.arguments = args
        showDialogFragment(feedbackDialog, "feedback-dialog")
    }

    fun showPushRejection(targetTranslation: TargetTranslation) {
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
            }.show()
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

    fun showAuthFailure() {
        mDialogShown = DialogShown.AUTH_FAILURE
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.upload_failed)
            .setMessage(R.string.auth_failure_retry)
            .setPositiveButton(R.string.yes) { _, _ ->
                mDialogShown = DialogShown.NONE
                initProgressWatcher(R.string.backup)
                val keyTask = RegisterSSHKeysTask(true)
                taskWatcher!!.watch(keyTask)
                TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID)
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
        out.putBoolean(STATE_OUTPUT_TO_DOCUMENT_FILE, isOutputToDocumentFile)
        if (mDestinationFolderUri != null) {
            out.putString(STATE_OUTPUT_FOLDER_URI, mDestinationFolderUri.toString())
        }

        super.onSaveInstanceState(out)
    }

    override fun onDestroy() {
        taskWatcher?.stop()
        super.onDestroy()
    }

    /**
     * for keeping track which dialog is being shown for orientation changes (not for DialogFragments)
     */
    enum class DialogShown(val value: Int) {
        NONE(0),
        PUSH_REJECTED(1),
        AUTH_FAILURE(2),
        BACKUP_FAILED(3),
        EXPORT_PROJECT_PROMPT(4),
        SHOW_BACKUP_RESULTS(5),
        SHOW_PUSH_SUCCESS(6),
        MERGE_CONFLICT(7),
        EXPORT_TO_USFM_PROMPT(8),
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
        @JvmField
        val TAG: String = BackupDialog::class.java.name
        const val ARG_TARGET_TRANSLATION_ID: String = "target_translation_id"
        private const val STATE_SETTING_DEVICE_ALIAS = "state_setting_device_alias"
        const val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        const val STATE_DO_MERGE: String = "state_do_merge"
        const val STATE_ACCESS_FILE: String = "state_access_file"
        const val STATE_DIALOG_MESSAGE: String = "state_dialog_message"
        const val STATE_OUTPUT_TO_DOCUMENT_FILE: String = "state_output_to_document_file"
        const val STATE_OUTPUT_FOLDER_URI: String = "state_output_folder_uri"
        const val EXTRA_OUTPUT_TO_USFM: String = "extra_output_to_usfm"
    }
}
