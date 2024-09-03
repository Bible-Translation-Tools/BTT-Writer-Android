package com.door43.translationstudio.ui.home

import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.deviceLanguageCode
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.App.Companion.restart
import com.door43.translationstudio.App.Companion.startBackupService
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.MergeConflictsHandler.OnMergeConflictListener
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.ActivityHomeBinding
import com.door43.translationstudio.tasks.CheckForLatestReleaseTask
import com.door43.translationstudio.tasks.DownloadIndexTask
import com.door43.translationstudio.tasks.ExamineImportsForCollisionsTask
import com.door43.translationstudio.tasks.GetAvailableSourcesTask
import com.door43.translationstudio.tasks.ImportProjectsTask
import com.door43.translationstudio.tasks.LogoutTask
import com.door43.translationstudio.tasks.PullTargetTranslationTask
import com.door43.translationstudio.tasks.RegisterSSHKeysTask
import com.door43.translationstudio.tasks.UpdateAllTask
import com.door43.translationstudio.tasks.UpdateCatalogsTask
import com.door43.translationstudio.tasks.UpdateSourceTask
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.ProfileActivity
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.dialogs.Door43LoginDialog
import com.door43.translationstudio.ui.dialogs.DownloadSourcesDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.home.WelcomeFragment.OnCreateNewTargetTranslation
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.util.FileUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.eventbuffer.EventBuffer
import org.unfoldingword.tools.eventbuffer.EventBuffer.OnEventTalker
import org.unfoldingword.tools.logger.Logger
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.SimpleTaskWatcher
import org.unfoldingword.tools.taskmanager.TaskManager
import java.io.File
import java.text.NumberFormat
import javax.inject.Inject

@AndroidEntryPoint
class HomeActivity : BaseActivity(), SimpleTaskWatcher.OnFinishedListener,
    OnCreateNewTargetTranslation, TargetTranslationListFragment.OnItemClickListener,
    EventBuffer.OnEventListener, ManagedTask.OnProgressListener, ManagedTask.OnFinishedListener,
    DialogInterface.OnCancelListener {

    @Inject lateinit var library: Door43Client
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var directoryProvider: IDirectoryProvider

    private var mFragment: Fragment? = null
    private var taskWatcher: SimpleTaskWatcher? = null
    private var mExamineTask: ExamineImportsForCollisionsTask? = null
    private var mAlertShown = DialogShown.NONE
    private var mTargetTranslationWithUpdates: String? = null
    private var mTargetTranslationID: String? = null
    private var progressDialog: ProgressDialog? = null
    private var mUpdateDialog: UpdateLibraryDialog? = null

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startBackupService()

        taskWatcher = SimpleTaskWatcher(this, R.string.loading).apply {
            setOnFinishedListener(this@HomeActivity)
        }

        with(binding) {
            addTargetTranslationButton.setOnClickListener { onCreateNewTargetTranslation() }

            if (savedInstanceState != null) {
                // use current fragment
                mFragment = supportFragmentManager.findFragmentById(fragmentContainer.id)
            } else {
                if (translator.targetTranslationIDs.isNotEmpty()) {
                    mFragment = TargetTranslationListFragment()
                    mFragment?.setArguments(intent.extras)
                } else {
                    mFragment = WelcomeFragment()
                    mFragment?.setArguments(intent.extras)
                }

                supportFragmentManager.beginTransaction().add(R.id.fragment_container, mFragment!!)
                    .commit()
            }

            logoutButton.setOnClickListener { doLogout() }
        }

        val moreButton = findViewById<View>(R.id.action_more) as ImageButton
        moreButton.setOnClickListener { v ->
            val moreMenu = PopupMenu(this@HomeActivity, v)
            ViewUtil.forcePopupMenuIcons(moreMenu)
            moreMenu.menuInflater.inflate(R.menu.menu_home, moreMenu.menu)
            moreMenu.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_update -> {
                        mUpdateDialog = UpdateLibraryDialog()
                        showDialogFragment(mUpdateDialog!!, UpdateLibraryDialog.TAG)
                        true
                    }
                    R.id.action_import -> {
                        val importDialog = ImportDialog()
                        showDialogFragment(importDialog, ImportDialog.TAG)
                        true
                    }
                    R.id.action_feedback -> {
                        val dialog = FeedbackDialog()
                        showDialogFragment(dialog, "feedback-dialog")
                        true
                    }
                    R.id.action_share_apk -> {
                        try {
                            val pInfo = packageManager.getPackageInfo(packageName, 0)
                            val apkFile = File(pInfo.applicationInfo.publicSourceDir)
                            val exportFile = File(
                                directoryProvider.sharingDir, pInfo.applicationInfo.loadLabel(
                                    packageManager
                                ).toString() + "_" + pInfo.versionName + ".apk"
                            )
                            FileUtilities.copyFile(apkFile, exportFile)
                            if (exportFile.exists()) {
                                val u = FileProvider.getUriForFile(
                                    this@HomeActivity,
                                    "com.door43.translationstudio.fileprovider",
                                    exportFile
                                )
                                val i = Intent(Intent.ACTION_SEND)
                                i.setType("application/zip")
                                i.putExtra(Intent.EXTRA_STREAM, u)
                                startActivity(
                                    Intent.createChooser(
                                        i,
                                        resources.getString(R.string.send_to)
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // todo notify user app could not be shared
                        }
                        true
                    }
                    R.id.action_log_out -> {
                        doLogout()
                        true
                    }
                    R.id.action_settings -> {
                        val intent = Intent(this@HomeActivity, SettingsActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    else -> false
                }
            }
            moreMenu.show()
        }

        // check if user is trying to open a tstudio file
        if (intent != null) {
            val action = intent.action
            if (action != null) {
                if (action.compareTo(Intent.ACTION_VIEW) == 0 || action.compareTo(Intent.ACTION_DEFAULT) == 0) {
                    val scheme = intent.scheme
                    val resolver = contentResolver
                    val contentUri = intent.data
                    Logger.i(TAG, "Opening: " + contentUri.toString())
                    if (scheme!!.compareTo(ContentResolver.SCHEME_CONTENT) == 0) {
                        importFromUri(resolver, contentUri)
                        return
                    } else if (scheme.compareTo(ContentResolver.SCHEME_FILE) == 0) {
                        importFromUri(resolver, contentUri)
                        return
                    }
                }
            }
        }

        // open last project when starting the first time
        if (savedInstanceState == null) {
            val targetTranslation = lastOpened
            if (targetTranslation != null) {
                onItemClick(targetTranslation)
                return
            }
        } else {
            mAlertShown = DialogShown.fromInt(
                savedInstanceState.getInt(STATE_DIALOG_SHOWN, INVALID),
                DialogShown.NONE
            )
            mTargetTranslationID = savedInstanceState.getString(
                STATE_DIALOG_TRANSLATION_ID,
                null
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressedHandler()
            }
        })
    }

    /**
     * do logout activity
     */
    private fun doLogout() {
        profile.gogsUser?.let { user ->
            val task = LogoutTask(user)
            TaskManager.addTask(task, LogoutTask.TASK_ID)
            task.addOnFinishedListener(this)
        }
        profile.logout()
        val logoutIntent = Intent(this@HomeActivity, ProfileActivity::class.java)
        startActivity(logoutIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        translator.lastFocusTargetTranslation = null

        val currentUser = findViewById<View>(R.id.current_user) as TextView
        val userText = resources.getString(R.string.current_user, ProfileActivity.currentUser)
        currentUser.text = userText

        val numTranslations = translator!!.targetTranslationIDs.size
        if (numTranslations > 0 && mFragment is WelcomeFragment) {
            // display target translations list
            mFragment = TargetTranslationListFragment().apply {
                setArguments(intent.extras)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, this)
                    .commit()
            }

            val hand = Handler(Looper.getMainLooper())
            hand.post { // delay to load list after fragment initializes
                (mFragment as TargetTranslationListFragment).reloadList()
            }
        } else if (numTranslations == 0 && mFragment is TargetTranslationListFragment) {
            // display welcome screen
            mFragment = WelcomeFragment().apply {
                setArguments(intent.extras)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, this)
                    .commit()
            }
        } else if (numTranslations > 0 && mFragment is TargetTranslationListFragment) {
            // reload list
            (mFragment as TargetTranslationListFragment).reloadList()
        }

        // re-connect to tasks
        var task = TaskManager.getTask(PullTargetTranslationTask.TASK_ID)
        if (task != null) {
            taskWatcher?.watch(task)
        }
        task = TaskManager.getTask(UpdateAllTask.TASK_ID)
        if (task != null) {
            task.addOnProgressListener(this)
            task.addOnFinishedListener(this)
        }
        task = TaskManager.getTask(UpdateSourceTask.TASK_ID)
        if (task != null) {
            task.addOnProgressListener(this)
            task.addOnFinishedListener(this)
        }
        task = TaskManager.getTask(DownloadIndexTask.TASK_ID)
        if (task != null) {
            task.addOnProgressListener(this)
            task.addOnFinishedListener(this)
        }
        task = TaskManager.getTask(UpdateCatalogsTask.TASK_ID)
        if (task != null) {
            task.addOnProgressListener(this)
            task.addOnFinishedListener(this)
        }

        mTargetTranslationWithUpdates = translator.notifyTargetTranslationWithUpdates
        if (mTargetTranslationWithUpdates != null && task == null) {
            showTranslationUpdatePrompt(mTargetTranslationWithUpdates!!)
        }

        restoreDialogs()
    }

    /**
     * Restores dialogs
     */
    private fun restoreDialogs() {
        // restore alert dialogs
        when (mAlertShown) {
            DialogShown.IMPORT_VERIFICATION -> displayImportVerification()
            DialogShown.MERGE_CONFLICT -> showMergeConflict(mTargetTranslationID)
            DialogShown.NONE -> {}
            else -> Logger.e(TAG, "Unsupported restore dialog: $mAlertShown")
        }
        // re-connect to dialog fragments
        val dialog = supportFragmentManager.findFragmentByTag(UpdateLibraryDialog.TAG)
        if (dialog is OnEventTalker) {
            (dialog as OnEventTalker).eventBuffer.addOnEventListener(this)
        }
    }

    /**
     * Displays a dialog while replacing any duplicate dialog
     *
     * @param dialog
     * @param tag
     */
    private fun showDialogFragment(dialog: DialogFragment, tag: String) {
        var ft = supportFragmentManager.beginTransaction()
        val prev = supportFragmentManager.findFragmentByTag(tag)
        if (prev != null) {
            ft.remove(prev)
            // TODO: 10/7/16 I don't think we need this
            ft.commit()
            ft = supportFragmentManager.beginTransaction()
        }
        ft.addToBackStack(null)
        // attach to any available event buffers
        if (dialog is OnEventTalker) {
            (dialog as OnEventTalker).eventBuffer.addOnEventListener(this)
        }
        dialog.show(ft, tag)
    }

    override fun onFinished(task: ManagedTask) {
        taskWatcher?.stop()
        if (task is ExamineImportsForCollisionsTask) {
            val hand = Handler(Looper.getMainLooper())
            hand.post {
                if (task.mSuccess) {
                    displayImportVerification()
                } else {
                    Logger.e(
                        TAG,
                        "Could not process content URI: " + task.mContentUri.toString()
                    )
                    showImportResults(mExamineTask?.mContentUri.toString(), null, false)
                    task.cleanup()
                }
            }
        } else if (task is ImportProjectsTask) {
            val hand = Handler(Looper.getMainLooper())
            hand.post {
                val importResults = task.importResults
                val success = importResults.isSuccess
                if (success && importResults.mergeConflict) {
                    MergeConflictsHandler.backgroundTestForConflictedChunks(
                        importResults.importedSlug,
                        object : OnMergeConflictListener {
                            override fun onNoMergeConflict(targetTranslationId: String) {
                                showImportResults(
                                    mExamineTask?.mContentUri.toString(),
                                    mExamineTask?.mProjectsFound,
                                    success
                                )
                            }

                            override fun onMergeConflict(targetTranslationId: String) {
                                showMergeConflict(targetTranslationId)
                            }
                        })
                } else {
                    showImportResults(
                        mExamineTask?.mContentUri.toString(),
                        mExamineTask?.mProjectsFound,
                        success
                    )
                }
            }
            mExamineTask?.cleanup()
        } else if (task is PullTargetTranslationTask) {
            val status = task.status
            if (status == PullTargetTranslationTask.Status.UP_TO_DATE || status == PullTargetTranslationTask.Status.UNKNOWN) {
                AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                    .setTitle(R.string.success)
                    .setMessage(R.string.success_translation_update)
                    .setPositiveButton(R.string.dismiss, null)
                    .show()
            } else if (status == PullTargetTranslationTask.Status.AUTH_FAILURE) {
                // regenerate ssh keys
                // if we have already tried ask the user if they would like to try again
                if (directoryProvider.hasSSHKeys()) {
                    showAuthFailure()
                    return
                }

                val keyTask = RegisterSSHKeysTask(false)
                taskWatcher?.watch(keyTask)
                TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID)
            } else if (status == PullTargetTranslationTask.Status.MERGE_CONFLICTS) {
                AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                    .setTitle(R.string.success)
                    .setMessage(R.string.success_translation_update_with_conflicts)
                    .setNeutralButton(
                        R.string.review
                    ) { _, _ ->
                        val intent =
                            Intent(this@HomeActivity, TargetTranslationActivity::class.java)
                        intent.putExtra(
                            Translator.EXTRA_TARGET_TRANSLATION_ID,
                            task.targetTranslation.id
                        )
                        startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST)
                    }
                    .show()
            } else {
                notifyTranslationUpdateFailed()
            }
            translator.notifyTargetTranslationWithUpdates = null
        } else if (task is RegisterSSHKeysTask) {
            if (task.isSuccess && mTargetTranslationWithUpdates != null) {
                Logger.i(this.javaClass.name, "SSH keys were registered with the server")
                // try to pull again
                downloadTargetTranslationUpdates(mTargetTranslationWithUpdates!!)
            } else {
                notifyTranslationUpdateFailed()
            }
        }
    }

    /**
     * let user know there was a merge conflict
     * @param targetTranslationID
     */
    fun showMergeConflict(targetTranslationID: String?) {
        mAlertShown = DialogShown.MERGE_CONFLICT
        mTargetTranslationID = targetTranslationID
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.merge_conflict_title).setMessage(R.string.import_merge_conflict)
            .setPositiveButton(
                R.string.label_ok
            ) { _, _ ->
                mAlertShown = DialogShown.NONE
                doManualMerge(mTargetTranslationID)
            }.show()
    }

    /**
     * open review mode to let user resolve conflict
     */
    fun doManualMerge(mTargetTranslationID: String?) {
        // ask parent activity to navigate to a new activity
        val intent = Intent(this, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, mTargetTranslationID)
        args.putBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, true)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)
        startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST)
    }

    fun showAuthFailure() {
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.error).setMessage(R.string.auth_failure_retry)
            .setPositiveButton(
                R.string.yes
            ) { _, _ ->
                mAlertShown = DialogShown.NONE
                val keyTask = RegisterSSHKeysTask(true)
                taskWatcher!!.watch(keyTask)
                TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID)
            }
            .setNegativeButton(
                R.string.no
            ) { _, _ ->
                mAlertShown = DialogShown.NONE
                notifyTranslationUpdateFailed()
            }.show()
    }

    private fun notifyTranslationUpdateFailed() {
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.error)
            .setMessage(R.string.update_failed)
            .setNeutralButton(R.string.dismiss, null)
            .show()
    }

    /**
     * display the final import Results.
     */
    private fun showImportResults(projectPath: String, projectNames: String?, success: Boolean) {
        mAlertShown = DialogShown.IMPORT_RESULTS
        val message: String
        if (success) {
            val format = resources.getString(R.string.import_project_success)
            message = String.format(format, projectNames, projectPath)
        } else {
            val format = resources.getString(R.string.import_failed)
            message = format + "\n" + projectPath
        }

        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(if (success) R.string.title_import_success else R.string.title_import_failed)
            .setMessage(message)
            .setPositiveButton(
                R.string.label_ok
            ) { _, _ ->
                mAlertShown = DialogShown.NONE
                mExamineTask!!.cleanup()
                this@HomeActivity.finish()
            }
            .show()
    }

    /**
     * begin the uri import
     * @param resolver
     * @param contentUri
     * @return
     * @throws Exception
     */
    private fun importFromUri(resolver: ContentResolver, contentUri: Uri?) {
        mExamineTask = ExamineImportsForCollisionsTask(resolver, contentUri)
        taskWatcher?.watch(mExamineTask)
        TaskManager.addTask(mExamineTask, ExamineImportsForCollisionsTask.TASK_ID)
    }

    /**
     * show dialog to verify that we want to import, restore or cancel.
     */
    private fun displayImportVerification() {
        mAlertShown = DialogShown.IMPORT_VERIFICATION
        val dlg = AlertDialog.Builder(this, R.style.AppTheme_Dialog)
        dlg.setTitle(R.string.label_import)
            .setMessage(
                String.format(
                    resources.getString(R.string.confirm_import_target_translation),
                    mExamineTask!!.mProjectsFound
                )
            )
            .setNegativeButton(
                R.string.title_cancel
            ) { _, _ ->
                mAlertShown = DialogShown.NONE
                mExamineTask!!.cleanup()
                this@HomeActivity.finish()
            }
            .setPositiveButton(
                R.string.label_restore
            ) { _, _ ->
                mAlertShown = DialogShown.NONE
                doArchiveImport(true)
            }

        if (mExamineTask!!.mAlreadyPresent) { // add merge option
            dlg.setNeutralButton(
                R.string.label_import
            ) { dialog, _ ->
                mAlertShown = DialogShown.NONE
                doArchiveImport(false)
                dialog.dismiss()
            }
        }
        dlg.show()
    }

    /**
     * import specified file
     * @param overwrite
     */
    private fun doArchiveImport(overwrite: Boolean) {
        val importTask = ImportProjectsTask(mExamineTask?.mProjectsFolder, overwrite)
        taskWatcher?.watch(importTask)
        TaskManager.addTask(importTask, ImportProjectsTask.TASK_ID)
    }

    private val lastOpened: TargetTranslation?
        /**
         * get last project opened and make sure it is still present
         * @return
         */
        get() {
            val lastTarget = translator.lastFocusTargetTranslation
            if (lastTarget != null) {
                return translator.getTargetTranslation(lastTarget)
            }
            return null
        }

    fun onBackPressedHandler() {
        // display confirmation before closing the app
        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setMessage(R.string.exit_confirmation)
            .setPositiveButton(
                R.string.yes
            ) { _, _ -> onBackPressedDispatcher.onBackPressed() }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (NEW_TARGET_TRANSLATION_REQUEST == requestCode) {
            if (RESULT_OK == resultCode) {
                if (mFragment is WelcomeFragment) {
                    // display target translations list
                    mFragment = TargetTranslationListFragment().apply {
                        setArguments(intent.extras)
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, this).commit()
                    }
                } else {
                    (mFragment as TargetTranslationListFragment?)!!.reloadList()
                }

                val intent = Intent(this@HomeActivity, TargetTranslationActivity::class.java)
                intent.putExtra(
                    Translator.EXTRA_TARGET_TRANSLATION_ID,
                    data!!.getStringExtra(NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID)
                )
                startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST)
            } else if (NewTargetTranslationActivity.RESULT_DUPLICATE == resultCode) {
                // display duplicate notice to user
                val targetTranslationId = data?.getStringExtra(
                    NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID
                )
                val existingTranslation = translator.getTargetTranslation(targetTranslationId)
                if (existingTranslation != null) {
                    val project = library.index()
                        .getProject(deviceLanguageCode, existingTranslation.projectId, true)
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content), String.format(
                            resources.getString(R.string.duplicate_target_translation),
                            project.name,
                            existingTranslation.targetLanguageName
                        ), Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                }
            } else if (NewTargetTranslationActivity.RESULT_ERROR == resultCode) {
                val snack = Snackbar.make(
                    findViewById(android.R.id.content),
                    resources.getString(R.string.error),
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                snack.show()
            }
        } else if (TARGET_TRANSLATION_VIEW_REQUEST == requestCode) {
            if (TargetTranslationActivity.RESULT_DO_UPDATE == resultCode) {
                val task = UpdateSourceTask()
                task.prefix = this.resources.getString(R.string.updating_languages)
                val taskId = UpdateSourceTask.TASK_ID
                task.addOnProgressListener(this)
                task.addOnFinishedListener(this)
                TaskManager.addTask(task, taskId)
            }
        }
    }

    /**
     * prompt user that project has changed
     * @param targetTranslationId
     */
    fun showTranslationUpdatePrompt(targetTranslationId: String) {
        val targetTranslation = translator.getTargetTranslation(targetTranslationId)
        if (targetTranslation == null) {
            Logger.e(TAG, "invalid target translation id:$targetTranslationId")
            return
        }

        val projectID = targetTranslation.projectId
        val project =
            library.index().getProject(targetTranslation.targetLanguageName, projectID, true)
        if (project == null) {
            Logger.e(TAG, "invalid project id:$projectID")
            return
        }

        val message = String.format(
            resources.getString(R.string.merge_request),
            project.name, targetTranslation.targetLanguageName
        )

        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.change_detected)
            .setMessage(message)
            .setPositiveButton(
                R.string.yes
            ) { _, _ ->
                mAlertShown = DialogShown.NONE
                downloadTargetTranslationUpdates(targetTranslationId)
            }
            .setNegativeButton(
                R.string.no
            ) { _, _ ->
                translator.notifyTargetTranslationWithUpdates = null
                mAlertShown = DialogShown.NONE
            }
            .show()
    }

    /**
     * Updates a single target translation
     * @param targetTranslationId
     */
    private fun downloadTargetTranslationUpdates(targetTranslationId: String) {
        if (isNetworkAvailable) {
            if (profile.gogsUser == null) {
                val dialog = Door43LoginDialog()
                showDialogFragment(dialog, Door43LoginDialog.TAG)
                return
            }

            val task = PullTargetTranslationTask(
                translator.getTargetTranslation(targetTranslationId),
                MergeStrategy.RECURSIVE,
                null
            )
            taskWatcher?.watch(task)
            TaskManager.addTask(task, PullTargetTranslationTask.TASK_ID)
        } else {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.internet_not_available,
                Snackbar.LENGTH_LONG
            )
            ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
            snack.show()
        }
    }

    override fun onCreateNewTargetTranslation() {
        val intent = Intent(this@HomeActivity, NewTargetTranslationActivity::class.java)
        startActivityForResult(intent, NEW_TARGET_TRANSLATION_REQUEST)
    }

    override fun onItemDeleted(targetTranslationId: String) {
        if (translator.targetTranslationIDs.isNotEmpty()) {
            (mFragment as TargetTranslationListFragment?)!!.reloadList()
        } else {
            // display welcome screen
            mFragment = WelcomeFragment().apply {
                setArguments(intent.extras)
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, this)
                    .commit()
            }
        }
    }

    override fun onItemClick(targetTranslation: TargetTranslation) {
        // validate project and target language

        val project = library.index().getProject("en", targetTranslation.projectId, true)
        val language = translator.languageFromTargetTranslation(targetTranslation)
        if (project == null || language == null) {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.missing_source,
                Snackbar.LENGTH_LONG
            )
            snack.setAction(R.string.check_for_updates) {
                mUpdateDialog = UpdateLibraryDialog()
                showDialogFragment(mUpdateDialog!!, UpdateLibraryDialog.TAG)
            }
            snack.setActionTextColor(resources.getColor(R.color.light_primary_text))
            ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
            snack.show()
        } else {
            val intent = Intent(this@HomeActivity, TargetTranslationActivity::class.java)
            intent.putExtra(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslation.id)
            startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST)
        }
    }

    fun notifyDatasetChanged() {
        onResume()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        // disconnect from tasks
        var task = TaskManager.getTask(UpdateAllTask.TASK_ID)
        if (task != null) {
            task.removeOnProgressListener(this)
            task.removeOnFinishedListener(this)
        }
        task = TaskManager.getTask(UpdateSourceTask.TASK_ID)
        if (task != null) {
            task.removeOnProgressListener(this)
            task.removeOnFinishedListener(this)
        }
        task = TaskManager.getTask(DownloadIndexTask.TASK_ID)
        if (task != null) {
            task.removeOnProgressListener(this)
            task.removeOnFinishedListener(this)
        }
        task = TaskManager.getTask(UpdateCatalogsTask.TASK_ID)
        if (task != null) {
            task.removeOnProgressListener(this)
            task.removeOnFinishedListener(this)
        }

        // dismiss progress
        val hand = Handler(Looper.getMainLooper())
        hand.post { if (progressDialog != null) progressDialog!!.dismiss() }

        outState.putInt(STATE_DIALOG_SHOWN, mAlertShown.value)
        outState.putString(STATE_DIALOG_TRANSLATION_ID, mTargetTranslationID)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        progressDialog?.dismiss()
        val dialog = supportFragmentManager.findFragmentByTag(UpdateLibraryDialog.TAG)
        if (dialog is OnEventTalker) {
            (dialog as OnEventTalker).eventBuffer.removeOnEventListener(this)
        }
        super.onDestroy()
    }

    override fun onEventBufferEvent(talker: OnEventTalker?, tag: Int, args: Bundle?) {
        if (talker is UpdateLibraryDialog) {
            mUpdateDialog?.dismiss()

            if (!isNetworkAvailable) {
                AlertDialog.Builder(this@HomeActivity, R.style.AppTheme_Dialog)
                    .setTitle(R.string.internet_not_available)
                    .setMessage(R.string.check_network_connection)
                    .setPositiveButton(R.string.dismiss, null)
                    .show()
                return
            }

            if (tag == UpdateLibraryDialog.EVENT_SELECT_DOWNLOAD_SOURCES) {
                selectDownloadSources()
                return
            }

            val task: ManagedTask
            val taskId: String
            when (tag) {
                UpdateLibraryDialog.EVENT_UPDATE_LANGUAGES -> {
                    task = UpdateCatalogsTask()
                    task.prefix = this.resources.getString(R.string.updating_languages)
                    taskId = UpdateCatalogsTask.TASK_ID
                }

                UpdateLibraryDialog.EVENT_UPDATE_SOURCE -> {
                    task = UpdateSourceTask()
                    task.prefix = this.resources.getString(R.string.updating_sources)
                    taskId = UpdateSourceTask.TASK_ID
                }

                UpdateLibraryDialog.EVENT_DOWNLOAD_INDEX -> {
                    task = DownloadIndexTask()
                    task.setPrefix(this.resources.getString(R.string.downloading_index))
                    taskId = DownloadIndexTask.TASK_ID
                }

                UpdateLibraryDialog.EVENT_UPDATE_APP -> {
                    task = CheckForLatestReleaseTask()
                    taskId = CheckForLatestReleaseTask.TASK_ID
                }

                else -> {
                    task = CheckForLatestReleaseTask()
                    taskId = CheckForLatestReleaseTask.TASK_ID
                }
            }
            task.addOnProgressListener(this)
            task.addOnFinishedListener(this)
            TaskManager.addTask(task, taskId)
        }
    }

    /**
     * bring up UI to select and download sources
     */
    private fun selectDownloadSources() {
        val ft = supportFragmentManager.beginTransaction()
        val prev = supportFragmentManager.findFragmentByTag(DownloadSourcesDialog.TAG)
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        val dialog = DownloadSourcesDialog()
        dialog.show(ft, DownloadSourcesDialog.TAG)
        return
    }

    override fun onTaskProgress(
        task: ManagedTask,
        progress: Double,
        message: String,
        secondary: Boolean
    ) {
        val hand = Handler(Looper.getMainLooper())
        hand.post(object : Runnable {
            override fun run() {
                // init dialog
                if (progressDialog == null) {
                    progressDialog = ProgressDialog(this@HomeActivity).apply {
                        setCancelable(true)
                        setCanceledOnTouchOutside(false)
                        setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                        setOnCancelListener(this@HomeActivity)
                        setIcon(R.drawable.ic_cloud_download_secondary_24dp)
                        setTitle(R.string.updating)
                        setMessage("")

                        setButton(
                            DialogInterface.BUTTON_NEGATIVE,
                            "Cancel"
                        ) { _, _ -> TaskManager.cancelTask(task) }
                    }
                }

                progressDialog?.apply {
                    // dismiss if finished or cancelled
                    if (task.isFinished || task.isCanceled) {
                        dismiss()
                        return
                    }

                    // progress
                    max = task.maxProgress()
                    setMessage(message)
                    if (progress > 0) {
                        isIndeterminate = false
                        this.progress = (progress * max).toInt()
                        setProgressNumberFormat("%1d/%2d")
                        setProgressPercentFormat(NumberFormat.getPercentInstance())
                    } else {
                        isIndeterminate = true
                        this.progress = max
                        setProgressNumberFormat(null)
                        setProgressPercentFormat(null)
                    }

                    // show
                    if (task.isFinished) {
                        dismiss()
                    } else if (!isShowing) {
                        show()
                    }
                }
            }
        })
    }

    override fun onTaskFinished(task: ManagedTask) {
        TaskManager.clearTask(task)

        val hand = Handler(Looper.getMainLooper())
        hand.post {
            if (task is GetAvailableSourcesTask) {
                if (progressDialog != null) {
                    progressDialog!!.dismiss()
                    progressDialog = null
                }
                val availableSources = task.sources
                Logger.i(TAG, "Found " + availableSources.size + " sources")
            } else if (task is CheckForLatestReleaseTask) {
                if (progressDialog != null) {
                    progressDialog?.dismiss()
                    progressDialog = null
                }

                val release = task.latestRelease
                if (release == null) {
                    AlertDialog.Builder(this@HomeActivity, R.style.AppTheme_Dialog)
                        .setTitle(R.string.check_for_updates)
                        .setMessage(R.string.have_latest_app_update)
                        .setPositiveButton(R.string.label_ok, null)
                        .show()
                } else { // have newer
                    SettingsActivity.promptUserToDownloadLatestVersion(
                        this@HomeActivity,
                        task.latestRelease
                    )
                }
            } else if (task is LogoutTask) {
                if (progressDialog != null) {
                    progressDialog?.dismiss()
                    progressDialog = null
                }
            } else {
                if (progressDialog != null) {
                    progressDialog?.dismiss()
                    progressDialog = null
                }

                var titleID = R.string.success
                var msgID = R.string.update_success
                var message: String? = null
                var failed = true

                if (task.isCanceled) {
                    titleID = R.string.error
                    msgID = R.string.update_cancelled
                } else if (!isTaskSuccess(task)) {
                    titleID = R.string.error
                    msgID = R.string.options_update_failed
                } else { // success
                    failed = false
                    if (task is UpdateSourceTask) {
                        message = String.format(
                            resources.getString(R.string.update_sources_success),
                            task.addedCnt,
                            task.updatedCnt
                        )
                    }
                    if (task is UpdateCatalogsTask) {
                        message = String.format(
                            resources.getString(R.string.update_languages_success),
                            task.addedCnt
                        )
                    }
                    if (task is DownloadIndexTask) {
                        message =
                            String.format(resources.getString(R.string.download_index_success))
                    }
                }
                val finalFailed = failed
                val showDownloadDialog = task is UpdateSourceTask

                // notify update is done
                val dlg =
                    AlertDialog.Builder(this@HomeActivity, R.style.AppTheme_Dialog)
                        .setTitle(titleID)
                        .setPositiveButton(
                            R.string.dismiss
                        ) { _, _ ->
                            if (!finalFailed && showDownloadDialog) {
                                selectDownloadSources() // if not failed, immediately go to select downloads
                            }
                            if (task is DownloadIndexTask) {
                                restart()
                            }
                        }
                if (message == null) {
                    dlg.setMessage(msgID)
                } else {
                    dlg.setMessage(message)
                }

                dlg.show()
            }
        }
    }

    private fun isTaskSuccess(task: ManagedTask): Boolean {
        return when (task) {
            is UpdateAllTask -> task.isSuccess
            is UpdateCatalogsTask -> task.isSuccess
            is UpdateSourceTask -> task.isSuccess
            is DownloadIndexTask -> task.isSuccess
            else -> false
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        // cancel the running update tasks
        var task = TaskManager.getTask(UpdateAllTask.TASK_ID)
        if (task != null) TaskManager.cancelTask(task)
        task = TaskManager.getTask(UpdateSourceTask.TASK_ID)
        if (task != null) TaskManager.cancelTask(task)
        task = TaskManager.getTask(DownloadIndexTask.TASK_ID)
        if (task != null) TaskManager.cancelTask(task)
        task = TaskManager.getTask(UpdateCatalogsTask.TASK_ID)
        if (task != null) TaskManager.cancelTask(task)
    }

    /**
     * for keeping track if dialog is being shown for orientation changes
     */
    enum class DialogShown {
        NONE,
        IMPORT_VERIFICATION,
        OPEN_LIBRARY,
        IMPORT_RESULTS,
        MERGE_CONFLICT;

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
        private val NEW_TARGET_TRANSLATION_REQUEST = 1
        private val TARGET_TRANSLATION_VIEW_REQUEST = 101
        val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        val STATE_DIALOG_TRANSLATION_ID: String = "state_dialog_translationID"
        val TAG: String = HomeActivity::class.java.simpleName
        val INVALID: Int = -1
    }
}
