package com.door43.translationstudio.ui.home

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
import androidx.fragment.app.viewModels
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TargetTranslationMigrator
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.DialogImportFromDoor43Binding
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.home.ImportDialog.MergeOptions
import com.door43.translationstudio.ui.home.ImportDialog.MergeOptions.Companion.fromInt
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.viewmodels.ImportViewModel
import com.door43.usecases.CloneRepository
import com.door43.util.FileUtilities.deleteQuietly
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.tools.logger.Logger
import java.io.IOException
import javax.inject.Inject
import kotlin.math.min

/**
 * Created by joel on 5/10/16.
 */
@AndroidEntryPoint
open class ImportFromDoor43Dialog : DialogFragment() {
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var targetTranslationMigrator: TargetTranslationMigrator
    @Inject lateinit var library: Door43Client
    @Inject lateinit var typography: Typography

    private val viewModel: ImportViewModel by viewModels()

    private var targetTranslation: TargetTranslation? = null
    private var repositories = arrayListOf<RepositoryItem>()
    private var dialogShown: DialogShown = DialogShown.NONE

    private val adapter by lazy { TranslationRepositoryAdapter(typography) }
    private var mCloneHtmlUrl: String? = null
    private var mergeSelection = MergeOptions.NONE
    private var mergeConflicted = false

    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private var _binding: DialogImportFromDoor43Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogImportFromDoor43Binding.inflate(inflater, container, false)

        progressDialog = ProgressHelper.newInstance(
            requireContext(),
            R.string.label_import,
            false
        )

        with(binding) {
            dismissButton.setOnClickListener {
                // TODO stop search repo task
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

                viewModel.searchRepositories(userQuery, repoQuery, 50)
            }

            (root.findViewById<View>(R.id.list) as? ListView)?.let { list ->
                list.adapter = adapter
                list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                    if (adapter.isSupported(position)) {
                        doImportProject(position)
                    } else {
                        val projectName = adapter.getProjectName(position)
                        val message =
                            requireActivity().getString(R.string.import_warning, projectName)
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
            dialogShown = DialogShown.fromInt(
                savedInstanceState.getInt(
                    STATE_DIALOG_SHOWN,
                    DialogShown.NONE.value
                )
            )
            mCloneHtmlUrl = savedInstanceState.getString(STATE_CLONE_URL, null)
            mergeConflicted = savedInstanceState.getBoolean(STATE_MERGE_CONFLICT, false)
            mergeSelection =
                fromInt(savedInstanceState.getInt(STATE_MERGE_SELECTION, MergeOptions.NONE.value))
            val targetTranslationId = savedInstanceState.getString(STATE_TARGET_TRANSLATION, null)
            targetTranslationId?.let { viewModel.loadTargetTranslation(it) }

            val repoJsonArray = savedInstanceState.getStringArray(STATE_REPOSITORIES)
            if (repoJsonArray != null) {
                for (json in repoJsonArray) {
                    try {
                        val repo = Repository.fromJSON(JSONObject(json))
                        if (json != null) {
                            repositories.add(viewModel.mapRepository(repo))
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
                adapter.setRepositories(repositories)
            }
        }

        setupObservers()

        restoreDialogs()
        return binding.root
    }

    private fun setupObservers() {
        viewModel.translation.observe(this) {
            it?.let { targetTranslation = it }
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
        viewModel.repositories.observe(this) {
            it?.let {
                repositories.addAll(it)
                adapter.setRepositories(it)
            }
        }
        viewModel.cloneRepoResult.observe(this) {
            it?.let { result ->
                var alreadyExisted = false

                if (result.status == CloneRepository.Status.SUCCESS) {
                    Logger.i(this.javaClass.name, "Repository cloned from ${result.cloneUrl}")
                    val clonedDir = targetTranslationMigrator.migrate(result.cloneDir)
                    var importFailed = false
                    mergeConflicted = false

                    if (clonedDir != null) {
                        TargetTranslation.open(result.cloneDir) {
                            // Try to backup and delete corrupt project
                            viewModel.backupAndDeleteTranslation(result.cloneDir)
                        }?.let { tempTargetTranslation ->
                            val existingTargetTranslation = translator.getTargetTranslation(
                                tempTargetTranslation.id
                            )

                            alreadyExisted = existingTargetTranslation != null

                            if (alreadyExisted && mergeSelection != MergeOptions.OVERWRITE) {
                                // merge target translation
                                try {
                                    val success = existingTargetTranslation!!.merge(result.cloneDir) {
                                        // Try to backup and delete corrupt project
                                        viewModel.backupAndDeleteTranslation(result.cloneDir)
                                    }
                                    if (!success) {
                                        if (MergeConflictsHandler.isTranslationMergeConflicted(
                                                existingTargetTranslation.id,
                                                translator
                                            )
                                        ) {
                                            mergeConflicted = true
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
                                alreadyExisted = false
                                // restore the new target translation
                                try {
                                    translator.restoreTargetTranslation(tempTargetTranslation)
                                } catch (e: IOException) {
                                    Logger.e(
                                        this.javaClass.name,
                                        "Failed to import the target translation " + tempTargetTranslation.id,
                                        e
                                    )
                                    notifyImportFailed()
                                    importFailed = true
                                }
                            }
                        } ?: run {
                            Logger.e(this.javaClass.name, "Failed to open the online backup")
                            notifyImportFailed()
                            importFailed = true
                        }
                    }

                    deleteQuietly(result.cloneDir)

                    if (!importFailed && !alreadyExisted) {
                        (activity as? HomeActivity)?.loadTranslations()
                        showImportSuccess()
                    }
                } else if (result.status == CloneRepository.Status.AUTH_FAILURE) {
                    Logger.i(this.javaClass.name, "Authentication failed")
                    // if we have already tried ask the user if they would like to try again
                    if (directoryProvider.hasSSHKeys()) {
                        showAuthFailure()
                    } else {
                        viewModel.registerSSHKeys(false)
                    }
                } else {
                    notifyImportFailed()
                }
            }
        }
        viewModel.registeredSSHKeys.observe(this) {
            it?.let { registered ->
                if (registered) {
                    Logger.i(this.javaClass.name, "SSH keys were registered with the server")
                    cloneRepository(mergeSelection)
                } else {
                    notifyImportFailed()
                }
            }
        }
    }

    /**
     * do import of project at position
     * @param position
     */
    private fun doImportProject(position: Int) {
        val repo = adapter?.getItem(position)
        val repoName = repo?.repoName?.replace("/", "-")
        if (repo != null && repoName != null) {
            mCloneHtmlUrl = repo.url
            cloneRepository(MergeOptions.NONE)
        }
    }

    /**
     * start a clone task
     */
    private fun cloneRepository(mergeSelection: MergeOptions) {
        this.mergeSelection = mergeSelection
        mCloneHtmlUrl?.let { viewModel.cloneRepository(it) }
    }

    /**
     * restore the dialogs that were displayed before rotation
     */
    private fun restoreDialogs() {
        //recreate dialog last shown
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            when (dialogShown) {
                DialogShown.IMPORT_SUCCESS -> showImportSuccess()
                DialogShown.IMPORT_FAILED -> notifyImportFailed()
                DialogShown.AUTH_FAILURE -> showAuthFailure()
                DialogShown.MERGE_CONFLICT -> showMergeOverwritePrompt(targetTranslation)
                DialogShown.NONE -> {}
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
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    /**
     * tell user that import was successful
     */
    private fun showImportSuccess() {
        dialogShown = DialogShown.IMPORT_SUCCESS
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.import_from_door43)
            .setMessage(R.string.title_import_success)
            .setPositiveButton(R.string.dismiss) { _, _ ->
                dialogShown = DialogShown.NONE
                dismiss()
            }
            .show()
    }

    /**
     * let user know there was a merge conflict
     * @param targetTranslation
     */
    private fun showMergeOverwritePrompt(targetTranslation: TargetTranslation?) {
        dialogShown = DialogShown.MERGE_CONFLICT
        this.targetTranslation = targetTranslation
        val messageID = if (mergeConflicted) {
            R.string.import_merge_conflict_project_name
        } else {
            R.string.import_project_already_exists
        }
        val message = requireActivity().getString(messageID, targetTranslation?.id)
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.merge_conflict_title)
            .setMessage(message)
            .setPositiveButton(R.string.merge_projects_label) { _, _ ->
                dialogShown = DialogShown.NONE
                if (mergeConflicted) {
                    doManualMerge()
                } else {
                    showImportSuccess()
                }
            }
            .setNeutralButton(R.string.title_cancel) { dialog, _ ->
                dialogShown = DialogShown.NONE
                resetToMasterBackup()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.overwrite_projects_label) { _, _ ->
                dialogShown = DialogShown.NONE
                resetToMasterBackup() // restore and now overwrite
                cloneRepository(MergeOptions.OVERWRITE)
            }.show()
    }

    /**
     * restore original version
     */
    private fun resetToMasterBackup() {
        targetTranslation?.resetToMasterBackup()
    }

    /**
     * open review mode to let user resolve conflict
     */
    private fun doManualMerge() {
        // ask parent activity to navigate to target translation review mode with merge filter on
        val intent = Intent(activity, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslation?.id)
        args.putBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, true)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)
        startActivity(intent)
        dismiss()
    }

    private fun showAuthFailure() {
        dialogShown = DialogShown.AUTH_FAILURE
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.error).setMessage(R.string.auth_failure_retry)
            .setPositiveButton(R.string.yes) { _, _ ->
                dialogShown = DialogShown.NONE
                viewModel.registerSSHKeys(true)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                dialogShown = DialogShown.NONE
                notifyImportFailed()
            }.show()
    }

    private fun notifyImportFailed() {
        dialogShown = DialogShown.IMPORT_FAILED
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.error)
            .setMessage(R.string.restore_failed)
            .setPositiveButton(R.string.dismiss) { _, _ ->
                dialogShown = DialogShown.NONE
            }
            .show()
    }

    override fun onSaveInstanceState(out: Bundle) {
        val repoJsonList = arrayListOf<String>()
        for (r in repositories) {
            repoJsonList.add(r.toJson().toString())
        }
        out.putStringArray(STATE_REPOSITORIES, repoJsonList.toTypedArray<String>())
        out.putInt(STATE_DIALOG_SHOWN, dialogShown.value)
        out.putInt(STATE_MERGE_SELECTION, mergeSelection.value)
        out.putBoolean(STATE_MERGE_CONFLICT, mergeConflicted)
        out.putString(STATE_CLONE_URL, mCloneHtmlUrl)
        out.putString(STATE_TARGET_TRANSLATION, targetTranslation?.id)

        super.onSaveInstanceState(out)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        viewModel.clearResults()
    }

    /**
     * for keeping track which dialog is being shown for orientation changes (not for DialogFragments)
     */
    enum class DialogShown(val value: Int) {
        NONE(0),
        IMPORT_FAILED(1),
        AUTH_FAILURE(2),
        MERGE_CONFLICT(3),
        IMPORT_SUCCESS(4);

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
        const val TAG = "ImportFromDoor43Dialog"

        private const val STATE_REPOSITORIES = "state_repositories"
        private const val STATE_DIALOG_SHOWN = "state_dialog_shown"
        private const val STATE_CLONE_URL: String = "state_clone_url"
        private const val STATE_TARGET_TRANSLATION: String = "state_target_translation"
        private const val STATE_MERGE_SELECTION: String = "state_merge_selection"
        private const val STATE_MERGE_CONFLICT: String = "state_merge_conflict"
    }
}
