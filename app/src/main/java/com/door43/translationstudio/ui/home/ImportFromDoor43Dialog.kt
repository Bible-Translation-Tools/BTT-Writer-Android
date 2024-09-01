package com.door43.translationstudio.ui.home

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.door43.translationstudio.App
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.App.Companion.hasSSHKeys
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.DialogImportFromDoor43Binding
import com.door43.translationstudio.tasks.AdvancedGogsRepoSearchTask
import com.door43.translationstudio.tasks.CloneRepositoryTask
import com.door43.translationstudio.tasks.RegisterSSHKeysTask
import com.door43.translationstudio.tasks.SearchGogsRepositoriesTask
import com.door43.translationstudio.ui.home.ImportDialog.MergeOptions
import com.door43.translationstudio.ui.home.ImportDialog.MergeOptions.Companion.fromInt
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.util.FileUtilities.deleteQuietly
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.tools.logger.Logger
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.SimpleTaskWatcher
import org.unfoldingword.tools.taskmanager.TaskManager
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.math.min

/**
 * Created by joel on 5/10/16.
 */
@AndroidEntryPoint
open class ImportFromDoor43Dialog : DialogFragment(), SimpleTaskWatcher.OnFinishedListener {
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile

    private var taskWatcher: SimpleTaskWatcher? = null
    private var adapter: TranslationRepositoryAdapter? = null
    private var repositories: MutableList<Repository> = ArrayList()
    private var mCloneHtmlUrl: String? = null
    private var cloneDestDir: File? = null
    private var mDialogShown: DialogShown = DialogShown.NONE
    private var mTargetTranslation: TargetTranslation? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mMergeSelection = MergeOptions.NONE
    private var mMergeConflicted = false

    private var _binding: DialogImportFromDoor43Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogImportFromDoor43Binding.inflate(inflater, container, false)

        this.taskWatcher = SimpleTaskWatcher(activity, R.string.loading)
        taskWatcher?.setOnFinishedListener(this)

        with(binding) {
            dismissButton.setOnClickListener {
                taskWatcher?.stop()
                val task = TaskManager.getTask(
                    SearchGogsRepositoriesTask.TASK_ID
                ) as? SearchGogsRepositoriesTask

                if (task != null) {
                    task.stop()
                    TaskManager.cancelTask(task)
                    TaskManager.clearTask(task)
                }
                dismiss()
            }

            searchButton.setOnClickListener {
                val userQuery = username.text.toString()
                val repoQuery = translationId.text.toString()

                if (username.hasFocus()) {
                    closeKeyboard(activity, username)
                }
                if (translationId.hasFocus()) {
                    closeKeyboard(activity, translationId)
                }

                if (profile.gogsUser != null) {
                    val task = AdvancedGogsRepoSearchTask(profile.gogsUser, userQuery, repoQuery, 50)
                    TaskManager.addTask(task, AdvancedGogsRepoSearchTask.TASK_ID)
                    taskWatcher?.watch(task)
                } else {
                    val snack = Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        resources.getString(R.string.login_doo43),
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                    snack.show()
                    dismiss()
                }
            }
        }

        (binding.root.findViewById<View>(R.id.list) as? ListView)?.let { list ->
            adapter = TranslationRepositoryAdapter().apply {
                setTextOnlyResources(true) // only allow importing of text resources
                list.adapter = this
                list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    if (isSupported(position)) {
                        doImportProject(position)
                    } else {
                        val projectName = getProjectName(position)
                        val message = requireActivity().getString(R.string.import_warning, projectName)
                        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.import_from_door43)
                            .setMessage(message)
                            .setPositiveButton(R.string.label_import) { _, _ ->
                                doImportProject(position)
                            }
                            .setNegativeButton(R.string.title_cancel, null)
                            .show()
                    }
                }
            }
        }

        // restore state
        if (savedInstanceState != null) {
            mDialogShown = DialogShown.fromInt(
                savedInstanceState.getInt(
                    STATE_DIALOG_SHOWN,
                    DialogShown.NONE.value
                )
            )
            mCloneHtmlUrl = savedInstanceState.getString(STATE_CLONE_URL, null)
            mMergeConflicted = savedInstanceState.getBoolean(STATE_MERGE_CONFLICT, false)
            mMergeSelection =
                fromInt(savedInstanceState.getInt(STATE_MERGE_SELECTION, MergeOptions.NONE.value))
            val targetTranslationId = savedInstanceState.getString(STATE_TARGET_TRANSLATION, null)
            if (targetTranslationId != null) {
                mTargetTranslation = translator.getTargetTranslation(targetTranslationId)
            }

            adapter?.let { listAdapter ->
                val repoJsonArray = savedInstanceState.getStringArray(STATE_REPOSITORIES)
                if (repoJsonArray != null) {
                    for (json in repoJsonArray) {
                        try {
                            val repo = Repository.fromJSON(JSONObject(json))
                            if (json != null) {
                                repositories.add(repo)
                            }
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                    listAdapter.setRepositories(repositories)
                }
            }
        }

        // connect to existing task
        val searchTask =
            TaskManager.getTask(AdvancedGogsRepoSearchTask.TASK_ID) as? AdvancedGogsRepoSearchTask
        val cloneTask = TaskManager.getTask(CloneRepositoryTask.TASK_ID) as? CloneRepositoryTask
        if (searchTask != null) {
            taskWatcher?.watch(searchTask)
        } else if (cloneTask != null) {
            taskWatcher?.watch(cloneTask)
        }

        restoreDialogs()
        return binding.root
    }

    /**
     * do import of project at position
     * @param position
     */
    private fun doImportProject(position: Int) {
        showProgressDialog()
        val repo = adapter?.getItem(position)
        val repoName = repo?.fullName?.replace("/", "-")
        if (repo != null && repoName != null) {
            cloneDestDir = File(requireContext().cacheDir, repoName + System.currentTimeMillis() + "/")
            mCloneHtmlUrl = repo.htmlUrl
            cloneRepository(MergeOptions.NONE)
        }
    }

    /**
     * start a clone task
     */
    private fun cloneRepository(mergeSelection: MergeOptions) {
        showProgressDialog()
        mMergeSelection = mergeSelection
        val task = CloneRepositoryTask(mCloneHtmlUrl, cloneDestDir)
        taskWatcher?.watch(task)
        TaskManager.addTask(task, CloneRepositoryTask.TASK_ID)
    }

    /**
     * restore the dialogs that were displayed before rotation
     */
    private fun restoreDialogs() {
        //recreate dialog last shown

        when (mDialogShown) {
            DialogShown.IMPORT_FAILED -> notifyImportFailed()
            DialogShown.AUTH_FAILURE -> showAuthFailure()
            DialogShown.MERGE_CONFLICT -> showMergeOverwritePrompt(mTargetTranslation)
            DialogShown.NONE -> {}
            else -> Logger.e(TAG, "Unsupported restore dialog: " + mDialogShown.toString())
        }
    }

    /**
     * creates and displays progress dialog
     */
    private fun showProgressDialog() {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            dismissProgressDialog()
            mProgressDialog = ProgressDialog(activity).apply {
                setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                setCancelable(false)
                setCanceledOnTouchOutside(true)
                isIndeterminate = true
                setTitle(R.string.import_project_file)
                show()
            }
        }
    }

    /**
     * removes the progress dialog
     */
    protected fun dismissProgressDialog() {
        if (null != mProgressDialog) {
            mProgressDialog!!.dismiss()
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
        dialog!!.window!!
            .setLayout((width * screenWidthFactor).toInt(), WindowManager.LayoutParams.MATCH_PARENT)
    }

    override fun onFinished(task: ManagedTask) {
        taskWatcher?.stop()
        TaskManager.clearTask(task)

        if (task is AdvancedGogsRepoSearchTask) {
            this.repositories = task.repositories
            adapter?.setRepositories(repositories)
        } else if (task is CloneRepositoryTask) {
            if (!task.isCanceled()) {
                val status = task.status
                var tempPath = task.destDir
                val cloneUrl = task.cloneUrl
                var alreadyExisted = false

                if (status == CloneRepositoryTask.Status.SUCCESS) {
                    Logger.i(this.javaClass.name, "Repository cloned from $cloneUrl")
                    tempPath = TargetTranslationMigrator.migrate(tempPath)
                    val tempTargetTranslation = TargetTranslation.open(tempPath)
                    var importFailed = false
                    mMergeConflicted = false
                    if (tempTargetTranslation != null) {
                        val existingTargetTranslation =
                            translator!!.getTargetTranslation(tempTargetTranslation.id)
                        alreadyExisted = (existingTargetTranslation != null)
                        if (alreadyExisted && (mMergeSelection != MergeOptions.OVERWRITE)) {
                            // merge target translation
                            try {
                                val success = existingTargetTranslation!!.merge(tempPath)
                                if (!success) {
                                    if (MergeConflictsHandler.isTranslationMergeConflicted(
                                            existingTargetTranslation.id
                                        )
                                    ) {
                                        mMergeConflicted = true
                                    }
                                }
                                showMergeOverwritePrompt(existingTargetTranslation)
                            } catch (e: Exception) {
                                Logger.e(
                                    this.javaClass.name,
                                    "Failed to merge the target translation",
                                    e
                                )
                                notifyImportFailed()
                                importFailed = true
                            }
                        } else {
                            // restore the new target translation
                            try {
                                translator!!.restoreTargetTranslation(tempTargetTranslation)
                            } catch (e: IOException) {
                                Logger.e(
                                    this.javaClass.name,
                                    "Failed to import the target translation " + tempTargetTranslation.id,
                                    e
                                )
                                notifyImportFailed()
                                importFailed = true
                            }
                            alreadyExisted = false
                        }
                    } else {
                        Logger.e(this.javaClass.name, "Failed to open the online backup")
                        notifyImportFailed()
                        importFailed = true
                    }
                    deleteQuietly(tempPath)

                    if (!importFailed && !alreadyExisted) {
                        // todo: terrible hack. We should instead register a listener with the dialog
                        (activity as? HomeActivity)?.notifyDatasetChanged()
                        showImportSuccess()
                    }
                } else if (status == CloneRepositoryTask.Status.AUTH_FAILURE) {
                    Logger.i(this.javaClass.name, "Authentication failed")
                    // if we have already tried ask the user if they would like to try again
                    if (hasSSHKeys()) {
                        dismissProgressDialog()
                        showAuthFailure()
                        return
                    }

                    val keyTask = RegisterSSHKeysTask(false)
                    taskWatcher?.watch(keyTask)
                    TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID)
                } else {
                    notifyImportFailed()
                }
            }
        } else if (task is RegisterSSHKeysTask) {
            if (task.isSuccess) {
                Logger.i(this.javaClass.name, "SSH keys were registered with the server")
                cloneRepository(mMergeSelection)
            } else {
                notifyImportFailed()
            }
        }
        dismissProgressDialog()
    }

    /**
     * tell user that import was successful
     */
    fun showImportSuccess() {
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.import_from_door43)
            .setMessage(R.string.title_import_success)
            .setPositiveButton(R.string.dismiss, null)
            .show()
    }

    /**
     * let user know there was a merge conflict
     * @param targetTranslation
     */
    fun showMergeOverwritePrompt(targetTranslation: TargetTranslation?) {
        mDialogShown = DialogShown.MERGE_CONFLICT
        mTargetTranslation = targetTranslation
        val messageID =
            if (mMergeConflicted) R.string.import_merge_conflict_project_name else R.string.import_project_already_exists
        val message = requireActivity().getString(messageID, targetTranslation!!.id)
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.merge_conflict_title)
            .setMessage(message)
            .setPositiveButton(R.string.merge_projects_label) { _, _ ->
                mDialogShown = DialogShown.NONE
                if (mMergeConflicted) {
                    doManualMerge()
                } else {
                    showImportSuccess()
                }
            }
            .setNeutralButton(R.string.title_cancel) { dialog, _ ->
                mDialogShown = DialogShown.NONE
                resetToMasterBackup()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.overwrite_projects_label) { _, _ ->
                mDialogShown = DialogShown.NONE
                resetToMasterBackup() // restore and now overwrite
                cloneRepository(MergeOptions.OVERWRITE)
            }.show()
    }

    /**
     * restore original version
     */
    private fun resetToMasterBackup() {
        mTargetTranslation?.resetToMasterBackup()
    }

    /**
     * open review mode to let user resolve conflict
     */
    private fun doManualMerge() {
        // ask parent activity to navigate to target translation review mode with merge filter on
        val intent = Intent(activity, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(App.EXTRA_TARGET_TRANSLATION_ID, mTargetTranslation!!.id)
        args.putBoolean(App.EXTRA_START_WITH_MERGE_FILTER, true)
        args.putInt(App.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)
        startActivity(intent)
        dismiss()
    }

    fun showAuthFailure() {
        mDialogShown = DialogShown.AUTH_FAILURE
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.error).setMessage(R.string.auth_failure_retry)
            .setPositiveButton(R.string.yes) { _, _ ->
                mDialogShown = DialogShown.NONE
                val keyTask = RegisterSSHKeysTask(true)
                taskWatcher!!.watch(keyTask)
                TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                mDialogShown = DialogShown.NONE
                notifyImportFailed()
            }.show()
    }

    fun notifyImportFailed() {
        mDialogShown = DialogShown.IMPORT_FAILED
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.error)
            .setMessage(R.string.restore_failed)
            .setPositiveButton(R.string.dismiss) { _, _ ->
                mDialogShown = DialogShown.NONE
            }
            .show()
    }

    override fun onSaveInstanceState(out: Bundle) {
        val repoJsonList: MutableList<String> = ArrayList()
        for (r in repositories) {
            repoJsonList.add(r.toJSON().toString())
        }
        out.putStringArray(STATE_REPOSITORIES, repoJsonList.toTypedArray<String>())
        out.putInt(STATE_DIALOG_SHOWN, mDialogShown!!.value)
        out.putInt(STATE_MERGE_SELECTION, mMergeSelection.value)
        out.putBoolean(STATE_MERGE_CONFLICT, mMergeConflicted)
        if (mCloneHtmlUrl != null) {
            out.putString(STATE_CLONE_URL, mCloneHtmlUrl)
        }

        if (mTargetTranslation != null) {
            val targetTranslationId = mTargetTranslation!!.id
            out.putString(STATE_TARGET_TRANSLATION, targetTranslationId)
        }

        super.onSaveInstanceState(out)
    }

    override fun onDestroy() {
        dismissProgressDialog()
        taskWatcher!!.stop()
        super.onDestroy()
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
        IMPORT_FAILED(1),
        AUTH_FAILURE(2),
        MERGE_CONFLICT(3);

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
        val TAG: String = ImportFromDoor43Dialog::class.java.simpleName

        private const val STATE_REPOSITORIES = "state_repositories"
        private const val STATE_DIALOG_SHOWN = "state_dialog_shown"
        const val STATE_CLONE_URL: String = "state_clone_url"
        const val STATE_TARGET_TRANSLATION: String = "state_target_translation"
        const val STATE_MERGE_SELECTION: String = "state_merge_selection"
        const val STATE_MERGE_CONFLICT: String = "state_merge_conflict"
    }
}
