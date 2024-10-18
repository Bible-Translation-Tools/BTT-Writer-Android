package com.door43.translationstudio.ui

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MissingNameItem
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.ActivityImportUsfmBinding
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.newtranslation.ProjectListFragment
import com.door43.translationstudio.ui.newtranslation.TargetLanguageListFragment
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.viewmodels.ImportUsfmViewModel
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.tools.logger.Logger
import java.io.File

/**
 * Handles the workflow UI for importing a USFM file.
 */
class ImportUsfmActivity : BaseActivity(), TargetLanguageListFragment.OnItemClickListener,
    ProjectListFragment.OnItemClickListener {
    private var fragment: Searchable? = null
    private lateinit var progressDialog: ProgressHelper.ProgressDialog

    private var targetLanguage: TargetLanguage? = null
    private var count: Counter? = null
    private var currentState = ImportState.NeedLanguage
    private var statusDialog: AlertDialog? = null
    private var finishedSuccess = false
    private var shuttingDown = false
    private var mergeConflict = false
    private var conflictingTargetTranslation: TargetTranslation? = null

    private lateinit var binding: ActivityImportUsfmBinding
    private val viewModel: ImportUsfmViewModel by viewModels()

    /**
     * returns string to use for language title
     */
    private val languageTitle: String
        get() {
            val format = resources.getString(R.string.selected_language)
            val language = String.format(
                format,
                "${targetLanguage?.slug} - ${targetLanguage?.name}"
            )
            return language
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportUsfmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (findViewById<View?>(R.id.fragment_container) != null) {
            if (savedInstanceState != null) {
                fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as Searchable
            } else {
                setActivityStateTo(ImportState.NeedLanguage)
            }
        }

        setupObservers()

        onBackPressedDispatcher.addCallback { onBackPressedHandler() }
    }

    override fun onStart() {
        super.onStart()
        progressDialog = ProgressHelper.newInstance(this, R.string.importing_usfm, false)
    }

    private fun setupObservers() {
        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog.show()
                progressDialog.setMessage(it.message)
                progressDialog.setMax(it.max)
                progressDialog.setProgress(it.progress)
            } else {
                progressDialog.dismiss()
            }
        }
        viewModel.usfm.observe(this) {
            it?.let { usfm ->
                if (usfm.booksMissingNames.isNotEmpty()) { // if we need valid names
                    count = Counter(usfm.booksMissingNames.size)
                    usfmPromptForNextName()
                } else {
                    usfmShowProcessingResults()
                }
            }
        }
        viewModel.importResult.observe(this) {
            it?.let { result ->
                finishedSuccess = result.success
                conflictingTargetTranslation = result.conflictingTargetTranslation

                val handler = Handler(Looper.getMainLooper())
                handler.post { usfmShowImportResults() }
            }
        }
    }

    /**
     * process an USFM file using the selected language
     */
    private fun processUSFMFile() {
        val args = intent.extras

        currentState = ImportState.ProcessingFiles

        targetLanguage?.let {
            title = languageTitle
            beginUsfmProcessing(it, args)
        }
    }

    /**
     * will prompt for resource name of next book, or if done will move on to processing finish and import
     */
    private fun usfmPromptForNextName() {
        if (count != null) {
            if (count!!.isEmpty) {
                usfmShowProcessingResults()
                return
            }
            val handler = Handler(Looper.getMainLooper())
            handler.post { setActivityStateTo(ImportState.PromptingForBookName) }
        }
    }

    /**
     * will display prompt to user asking if they want to select the resource name for the book
     */
    private fun usfmPromptForName() {
        val usfm = viewModel.usfm.value
        if (count != null && usfm != null) {
            val i = count!!.decrement()
            val item = usfm.booksMissingNames[i]

            val message: String?
            val description = usfm.getShortFilePath(item.description ?: "")
            if (item.invalidName != null) {
                val format = resources.getString(R.string.invalid_book_name_prompt)
                message = String.format(format, description, item.invalidName)
            } else {
                val format = resources.getString(R.string.missing_book_name_prompt)
                message = String.format(format, description)
            }

            AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.title_activity_import_usfm_language)
                .setMessage(message)
                .setPositiveButton(R.string.label_continue) { _, _ ->
                    fragment = ProjectListFragment()
                    (fragment as ProjectListFragment).arguments = intent.extras
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, (fragment as ProjectListFragment?)!!)
                        .commit()
                    var title = resources.getString(R.string.title_activity_import_usfm_book)
                    title += " $description"
                    setTitle(title)
                }
                .setNegativeButton(R.string.menu_cancel) { _, _ -> usfmPromptForNextName() }
                .setCancelable(true)
                .show()
        }
    }

    /**
     * process selected book with specified resource name
     *
     * @param item
     * @param resourceID
     */
    private fun usfmProcessBook(item: MissingNameItem, resourceID: String) {
        if (item.contents != null && item.description != null) {
            viewModel.processText(item.contents, item.description, false, resourceID)
        } else {
            usfmPromptForNextName()
        }
    }

    /**
     * processing of all books in file finished, show processing results and verify
     * that user wants to import.
     */
    private fun usfmShowProcessingResults() {
        if (shuttingDown) {
            return
        }
        currentState = ImportState.ShowingProcessingResults

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            viewModel.usfm.value?.let { usfm ->
                val processSuccess = usfm.isProcessSuccess
                val results = usfm.resultsString
                val language = languageTitle
                val message = "$language\n$results"

                val mStatusDialog =
                    AlertDialog.Builder(this@ImportUsfmActivity, R.style.AppTheme_Dialog)

                mStatusDialog.setTitle(if (processSuccess) R.string.title_processing_usfm_summary else R.string.title_import_usfm_error)
                    .setMessage(message)
                    .setNegativeButton(R.string.menu_cancel) { _, _ -> usfmImportDone(true) }

                if (processSuccess) { // only show continue if successful processing
                    mergeConflict = checkForMergeConflict()

                    if (mergeConflict) { // if merge conflict change the buttons and text
                        mStatusDialog.setTitle(R.string.merge_conflict_title)
                        val warning = resources.getString(
                            R.string.import_merge_conflict_project_name,
                            conflictingTargetTranslation?.id
                        )
                        mStatusDialog.setMessage("$message\n$warning")

                        mStatusDialog.setPositiveButton(R.string.merge_projects_label) { _, _ ->
                            doUsfmImport(false)
                        }
                        mStatusDialog.setNeutralButton(R.string.title_cancel) { _, _ ->
                            usfmImportDone(true)
                        }
                        mStatusDialog.setNegativeButton(R.string.overwrite_projects_label) { _, _ ->
                            doUsfmImport(true)
                        }
                    } else { // no merge conflict
                        mStatusDialog.setPositiveButton(R.string.label_continue) { _, _ ->
                            doUsfmImport(false)
                        }
                    }
                }
                mStatusDialog.show()
            }
        }
    }

    /**
     * do importing of found books
     * @param overwrite - force project overwrite
     */
    private fun doUsfmImport(overwrite: Boolean) {
        currentState = ImportState.ImportingFiles
        viewModel.usfm.value?.let { usfm ->
            viewModel.importProjects(usfm.importProjects, overwrite)
        }
    }

    /**
     * check for merge conflict presence
     * @return
     */
    private fun checkForMergeConflict(): Boolean {
        conflictingTargetTranslation = viewModel.checkMergeConflictExists()
        return conflictingTargetTranslation != null
    }

    /**
     * import has finished
     *
     * @param cancelled
     */
    private fun usfmImportDone(cancelled: Boolean) {
        currentState = ImportState.Finished
        cleanupUsfmImport()

        if (cancelled) {
            cancelled()
        } else {
            finished()
        }
    }

    /**
     * show results of import
     */
    private fun usfmShowImportResults() {
        if (shuttingDown) {
            return
        }

        if (conflictingTargetTranslation != null) {
            doManualMerge(conflictingTargetTranslation!!.id)
            usfmImportDone(false)
            return
        }

        currentState = ImportState.ShowingImportResults

        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(if (finishedSuccess) R.string.title_import_usfm_results else R.string.title_import_usfm_error)
            .setMessage(if (finishedSuccess) R.string.import_usfm_success else R.string.import_usfm_failed)
            .setPositiveButton(R.string.label_continue) { _, _ -> usfmImportDone(false) }
            .show()

        cleanupUsfmImport()
    }

    /**
     * open review mode to let user resolve conflict
     */
    private fun doManualMerge(targetTranslationID: String) {
        // navigate to target translation review mode with merge filter on
        val intent = Intent(this, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslationID)
        args.putBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, true)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)
        startActivity(intent)
    }

    /**
     * begin USFM processing using type passed (URI, File, or resource)
     *
     * @param args
     * @return
     */
    private fun beginUsfmProcessing(language: TargetLanguage, args: Bundle?) {
        if (args == null) return

        when {
            args.containsKey(EXTRA_USFM_IMPORT_URI) -> {
                val uriStr = args.getString(EXTRA_USFM_IMPORT_URI)
                uriStr?.let { uri ->
                    viewModel.processUsfm(language, Uri.parse(uri))
                } ?: run {
                    // todo show the error message
                }
            }
            args.containsKey(EXTRA_USFM_IMPORT_FILE) -> {
                (args.getSerializable(EXTRA_USFM_IMPORT_FILE) as? File)?.let { file ->
                    viewModel.processUsfm(language, file)
                }
            }
            args.containsKey(EXTRA_USFM_IMPORT_RESOURCE_FILE) -> {
                args.getString(EXTRA_USFM_IMPORT_RESOURCE_FILE)?.let { importResourceFile ->
                    viewModel.processUsfm(language, importResourceFile)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_new_target_translation, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        if (fragment is ProjectListFragment) {
            menu.findItem(R.id.action_update).setVisible(true)
        } else {
            menu.findItem(R.id.action_update).setVisible(false)
        }
        val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
        val searchMenuItem = menu.findItem(R.id.action_search)
        val searchViewAction = searchMenuItem.actionView as SearchView
        searchViewAction.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return true
            }
            override fun onQueryTextChange(s: String): Boolean {
                fragment?.onSearchQuery(s)
                return true
            }
        })
        searchViewAction.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        when (id) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                return true
            }
            R.id.action_search -> return true
            R.id.home -> {
                onBackPressedHandler()
                return true
            }
            // TODO: 10/18/16 display dialog for updating
            R.id.action_update -> return true
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        shuttingDown = false

        val targetLanguageId = savedInstanceState.getString(STATE_TARGET_LANGUAGE_ID, null)
        if (targetLanguageId != null) {
            targetLanguage = viewModel.getTargetLanguage(targetLanguageId)
        }

        currentState = ImportState.fromInt(
            savedInstanceState.getInt(
                STATE_CURRENT_STATE,
                ImportState.NeedLanguage.value
            )
        )

        val usfmStr = savedInstanceState.getString(STATE_USFM, null)
        if (usfmStr != null) {
            viewModel.processUsfm(usfmStr)
        }

        val usfm = viewModel.usfm.value
        if (savedInstanceState.containsKey(STATE_PROMPT_NAME_COUNTER) && usfm != null) {
            val count = savedInstanceState.getInt(STATE_PROMPT_NAME_COUNTER)
            this.count = Counter(count + 1) // backup one
        }

        finishedSuccess = savedInstanceState.getBoolean(STATE_FINISH_SUCCESS, false)

        val handler = Handler(Looper.getMainLooper())
        handler.post { setActivityStateTo(currentState) }
    }

    /**
     * update UI for specified state (e.g. prompt for language, book name selection, display processing results...)
     * and begin that state
     *
     * @param currentState
     */
    private fun setActivityStateTo(currentState: ImportState) {
        if (shuttingDown) {
            return
        }

        Logger.i(TAG, "setActivityStateTo($currentState)")
        this.currentState = currentState

        if (targetLanguage != null) {
            title = languageTitle
        }

        when (currentState) {
            ImportState.NeedLanguage -> if (fragment == null) {
                fragment = TargetLanguageListFragment()
                (fragment as TargetLanguageListFragment).arguments = intent.extras
                supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, fragment as TargetLanguageListFragment)
                    .commit()
                // TODO: animate
            }

            ImportState.ProcessingFiles -> {
                when {
                    targetLanguage != null -> processUSFMFile()
                    count != null -> usfmPromptForName()
                    else -> usfmShowProcessingResults()
                }
            }

            ImportState.PromptingForBookName -> {
                if (count != null) {
                    usfmPromptForName()
                } else {
                    usfmShowProcessingResults()
                }
            }

            ImportState.ShowingProcessingResults -> {
                usfmShowProcessingResults()
            }

            ImportState.ShowingImportResults -> usfmShowImportResults()
            ImportState.ImportingFiles -> {
                // not resumable - presume completed
                this.currentState = ImportState.Finished
                viewModel.cleanup()
            }

            ImportState.Finished -> {
                viewModel.cleanup()
            }
        }
    }

    private fun onBackPressedHandler() {
        when (currentState) {
            ImportState.NeedLanguage -> cancelled()
            ImportState.PromptingForBookName -> setBook(null)
            ImportState.ShowingProcessingResults, ImportState.ProcessingFiles -> {
                viewModel.cleanup()
                setActivityStateTo(ImportState.NeedLanguage)
            }
            ImportState.ShowingImportResults -> usfmImportDone(false)
            else -> {}
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        shuttingDown = true

        if (statusDialog != null) {
            statusDialog!!.dismiss()
        }
        statusDialog = null

        val currentState = this.currentState //capture state before it is changed
        outState.putInt(STATE_CURRENT_STATE, currentState.value)

        if (targetLanguage != null) {
            outState.putString(STATE_TARGET_LANGUAGE_ID, targetLanguage?.slug)
        } else {
            outState.remove(STATE_TARGET_LANGUAGE_ID)
        }

        viewModel.usfm.value?.let { usfm ->
            outState.putString(STATE_USFM, usfm.toJson().toString())
        } ?: run {
            outState.remove(STATE_USFM)
        }

        count?.counter?.let {
            outState.putInt(STATE_PROMPT_NAME_COUNTER, it)
        } ?: run {
            outState.remove(STATE_PROMPT_NAME_COUNTER)
        }

        outState.putBoolean(STATE_FINISH_SUCCESS, finishedSuccess)

        super.onSaveInstanceState(outState)
    }

    override fun onItemClick(targetLanguage: TargetLanguage) {
        this.targetLanguage = targetLanguage
        supportFragmentManager
            .beginTransaction()
            .remove(fragment as TargetLanguageListFragment)
            .commit()
        fragment = null
        processUSFMFile()
    }

    override fun onItemClick(projectId: String) {
        setBook(projectId)
    }

    /**
     * use the project ID
     *
     * @param projectId
     */
    private fun setBook(projectId: String?) {
        if (projectId != null) {
            supportFragmentManager
                .beginTransaction()
                .remove(fragment as ProjectListFragment)
                .commit()
            fragment = null

            val usfm = viewModel.usfm.value
            if (usfm != null) {
                count?.counter?.let {
                    val item = usfm.booksMissingNames[it]
                    usfmProcessBook(item, projectId)
                }
            }
        } else { //book cancelled
            usfmPromptForNextName()
        }
    }

    /**
     * user cancelled import
     */
    private fun cancelled() {
        cleanupUsfmImport()

        val data = Intent()
        setResult(RESULT_CANCELED, data)
        finish()
    }

    /**
     * user completed import
     */
    private fun finished() {
        cleanupUsfmImport()

        val data = Intent()
        setResult(if (mergeConflict) RESULT_MERGED else RESULT_OK, data)
        finish()
    }

    private fun cleanupUsfmImport() {
        viewModel.cleanup()
    }

    /**
     * enum that keeps track of current state of USFM import
     */
    enum class ImportState(val value: Int) {
        NeedLanguage(0),
        ProcessingFiles(1),
        PromptingForBookName(2),
        ShowingProcessingResults(3),
        ImportingFiles(4),
        ShowingImportResults(5),
        Finished(6);

        companion object {
            fun fromInt(i: Int): ImportState {
                for (b in entries) {
                    if (b.value == i) {
                        return b
                    }
                }
                return NeedLanguage
            }
        }
    }

    /**
     * class to keep track of number of books left to prompt for resource ID
     */
    private inner class Counter(var counter: Int) {
        val isEmpty: Boolean
            get() = counter == 0

        fun setCount(count: Int) {
            counter = count
        }

        fun increment(): Int {
            return ++counter
        }

        fun decrement(): Int {
            if (counter > 0) {
                counter--
            }
            return counter
        }
    }

    companion object {
        const val RESULT_DUPLICATE: Int = 2
        private const val STATE_TARGET_LANGUAGE_ID = "state_target_language_id"
        const val RESULT_ERROR: Int = 3
        const val EXTRA_USFM_IMPORT_URI: String = "extra_usfm_import_uri"
        const val EXTRA_USFM_IMPORT_FILE: String = "extra_usfm_import_file"
        const val EXTRA_USFM_IMPORT_RESOURCE_FILE: String = "extra_usfm_import_resource_file"
        const val STATE_USFM: String = "state_usfm"
        const val STATE_CURRENT_STATE: String = "state_current_state"
        const val STATE_PROMPT_NAME_COUNTER: String = "state_prompt_name_counter"
        const val STATE_FINISH_SUCCESS: String = "state_finish_success"
        const val RESULT_MERGED: Int = 2001
        val TAG: String = ImportUsfmActivity::class.java.simpleName
    }
}
