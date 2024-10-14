package com.door43.translationstudio.ui.newtranslation

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import com.door43.translationstudio.R
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.ResourceType
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.ActivityNewTargetTranslationBinding
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.Searchable
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.newlanguage.NewTempLanguageActivity
import com.door43.translationstudio.ui.viewmodels.NewTargetTranslationModel
import com.door43.usecases.MergeTargetTranslation
import com.door43.util.StringUtilities
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.door43client.models.TargetLanguage
import javax.inject.Inject

@AndroidEntryPoint
class NewTargetTranslationActivity : BaseActivity(), TargetLanguageListFragment.OnItemClickListener,
    ProjectListFragment.OnItemClickListener {

    @Inject lateinit var translator: Translator

    private var fragment: Searchable? = null
    private var dialogShown = DialogShown.NONE

    private lateinit var binding: ActivityNewTargetTranslationBinding

    private val viewModel: NewTargetTranslationModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewTargetTranslationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        if (extras != null) {
            viewModel.targetTranslationId = extras.getString(EXTRA_TARGET_TRANSLATION_ID, null)
            viewModel.changeTargetLanguageOnly = extras.getBoolean(EXTRA_CHANGE_TARGET_LANGUAGE_ONLY, false)
        }

        if (savedInstanceState != null) {
            viewModel.createdNewLanguage = savedInstanceState.getBoolean(
                STATE_NEW_LANGUAGE,
                false
            )
            dialogShown = DialogShown.fromInt(
                savedInstanceState.getInt(STATE_DIALOG_SHOWN, INVALID),
                DialogShown.NONE
            )
            if (savedInstanceState.containsKey(STATE_TARGET_TRANSLATION_ID)) {
                viewModel.newTargetTranslationId = savedInstanceState.getString(
                    STATE_TARGET_TRANSLATION_ID
                )
            }

            if (savedInstanceState.containsKey(STATE_TARGET_LANGUAGE)) {
                savedInstanceState.getString(STATE_TARGET_LANGUAGE)?.let { targetLanguageJsonStr ->
                    try {
                        viewModel.selectedTargetLanguage = TargetLanguage.fromJSON(
                            JSONObject(targetLanguageJsonStr)
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (findViewById<View?>(R.id.fragment_container) != null) {
            if (savedInstanceState != null) {
                fragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as Searchable
            } else {
                fragment = TargetLanguageListFragment()
                (fragment as TargetLanguageListFragment).arguments = intent.extras
                supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, fragment as TargetLanguageListFragment)
                    .commit()
                // TODO: animate
            }
        }

        if (viewModel.createdNewLanguage && viewModel.selectedTargetLanguage != null) {
            confirmTempLanguage()
        }

        setupObservers()
        restoreDialogs()
    }

    private fun setupObservers() {
        viewModel.mergeTranslationResult.observe(this) {
            it?.let { result ->
                val status = result.status
                var results = RESULT_ERROR

                if (MergeTargetTranslation.Status.MERGE_CONFLICTS == status) {
                    results = if (MergeConflictsHandler.isTranslationMergeConflicted(
                            result.destinationTranslation.id,
                            translator
                    )) {
                        RESULT_MERGE_CONFLICT
                    } else {
                        RESULT_OK
                    }

                    // clean up original settings
                    viewModel.clearTargetTranslationSettings(result.sourceTranslation.id)
                } else if (MergeTargetTranslation.Status.SUCCESS == status) {
                    results = RESULT_OK
                    // clean up original settings
                    viewModel.clearTargetTranslationSettings(result.sourceTranslation.id)
                }

                val data = Intent()
                data.putExtra(EXTRA_TARGET_TRANSLATION_ID, result.destinationTranslation.id)
                setResult(results, data)
                finish()
            }
        }
    }

    /**
     * restore the dialogs that were displayed before rotation
     */
    private fun restoreDialogs() {
        when (dialogShown) {
            DialogShown.RENAME_CONFLICT -> {
                val sourceTargetTranslation = viewModel.getTargetTranslation(
                    viewModel.targetTranslationId
                )
                val destTargetTranslation = viewModel.getTargetTranslation(
                    viewModel.newTargetTranslationId
                )
                if(sourceTargetTranslation != null && destTargetTranslation != null) {
                    showTargetTranslationConflict(sourceTargetTranslation, destTargetTranslation)
                }
            }
            DialogShown.NONE -> {}
        }
    }


    /**
     * Warn user that there is already an existing project with that language.
     * Give them the option of merging.
     * @param sourceTargetTranslation
     * @param existingTranslation
     */
    private fun showTargetTranslationConflict(
        sourceTargetTranslation: TargetTranslation,
        existingTranslation: TargetTranslation
    ) {
        dialogShown = DialogShown.RENAME_CONFLICT
        viewModel.newTargetTranslationId = existingTranslation.id
        val project = viewModel.getProject(existingTranslation)
        val message = String.format(
            resources.getString(R.string.warn_existing_target_translation),
            project.name,
            existingTranslation.targetLanguageName
        )

        AlertDialog.Builder(this, R.style.AppTheme_Dialog)
            .setTitle(R.string.warn_existing_target_translation_label)
            .setMessage(message)
            .setPositiveButton(R.string.yes) { _, _ ->
                // TODO: 11/1/16 the activity should return the language
                //  and let the calling activity perform the merge
                dialogShown = DialogShown.NONE
                viewModel.mergeTargetTranslation(
                    existingTranslation,
                    sourceTargetTranslation,
                    true
                )
            }
            .setNegativeButton(R.string.no) { _, _ ->
                dialogShown = DialogShown.NONE
                val snack = Snackbar.make(
                    findViewById(android.R.id.content),
                    R.string.rename_canceled,
                    Snackbar.LENGTH_LONG
                )
                ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                snack.show()
                setResult(RESULT_CANCELED)
                finish()
            }
            .show()
    }

    /**
     * Displays a confirmation for the new language
     */
    private fun confirmTempLanguage() {
        viewModel.selectedTargetLanguage?.let { language ->
            val msg = String.format(
                resources.getString(R.string.new_language_confirmation),
                language.slug,
                language.name
            )
            AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setCancelable(false)
                .setTitle(R.string.language)
                .setMessage(msg)
                .setPositiveButton(R.string.label_continue) { dialog, _ ->
                    dialog.dismiss()
                    onItemClick(language)
                }
                .setNeutralButton(R.string.copy) { _, _ ->
                    StringUtilities.copyToClipboard(
                        this,
                        language.slug
                    )
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.copied_to_clipboard,
                        Snackbar.LENGTH_SHORT
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                }
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_new_target_translation, menu)
        return true
    }

    override fun onItemClick(targetLanguage: TargetLanguage) {
        viewModel.selectedTargetLanguage = targetLanguage

        if (!viewModel.changeTargetLanguageOnly) {
            // display project list
            fragment = ProjectListFragment()
            (fragment as ProjectListFragment).arguments = intent.extras
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, (fragment as ProjectListFragment?)!!).commit()
            // TODO: animate
            invalidateOptionsMenu()
        } else { // just change the target language
            viewModel.getTargetTranslation(viewModel.targetTranslationId)?.let { sourceTargetTranslation ->
                // if nothing to do then skip
                if (targetLanguage.slug == sourceTargetTranslation.targetLanguage?.slug) {
                    setResult(RESULT_OK)
                    finish()
                    return
                }

                // check for project conflict
                val projectId = sourceTargetTranslation.projectId
                val resourceSlug = sourceTargetTranslation.resourceSlug

                val existingTranslation = viewModel.selectedTargetLanguage?.let { selected ->
                    viewModel.getTargetTranslation(
                        TargetTranslation.generateTargetTranslationId(
                            selected.slug, projectId, ResourceType.TEXT, resourceSlug
                        )
                    )
                }

                if (existingTranslation != null) {
                    showTargetTranslationConflict(sourceTargetTranslation, existingTranslation)
                } else { // no existing translation so change language and move
                    val originalTargetTranslationId = sourceTargetTranslation.id
                    sourceTargetTranslation.changeTargetLanguage(viewModel.selectedTargetLanguage)
                    viewModel.normalizeTargetTranslationPath(sourceTargetTranslation)
                    val newSourceTargetTranslationID = sourceTargetTranslation.id
                    viewModel.moveTargetTranslationAppSettings(
                        originalTargetTranslationId,
                        newSourceTargetTranslationID
                    )
                    setResult(RESULT_OK)
                    finish()
                }
            } ?: run {
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    override fun onItemClick(projectId: String) {
        // TRICKY: android only supports translating regular text projects
        val resourceSlug = if (projectId == "obs") "obs" else "reg" //Resource.REGULAR_SLUG;
        val existingTranslation = viewModel.selectedTargetLanguage?.let { selected ->
            viewModel.getTargetTranslation(
                TargetTranslation.generateTargetTranslationId(
                    selected.slug, projectId, ResourceType.TEXT, resourceSlug
                )
            )
        }
        if (existingTranslation == null) {
            // create new target translation
            // SourceLanguage sourceLanguage = App.getLibrary().getPreferredSourceLanguage(projectId, App.getDeviceLanguageCode()); // get project name
            // TODO: 3/2/2016 eventually the format will be specified in the project

            val format =
                if (projectId == "obs") TranslationFormat.MARKDOWN else TranslationFormat.USFM
            val targetTranslation = viewModel.createTargetTranslation(
                projectId,
                ResourceType.TEXT,
                resourceSlug,
                format
            )
            if (targetTranslation != null) {
                newProjectCreated(targetTranslation)
            } else {
                viewModel.deleteTargetTranslation(projectId, resourceSlug)

                val data = Intent()
                setResult(RESULT_ERROR, data)
                finish()
            }
        } else {
            // that translation already exists
            val data = Intent()
            data.putExtra(EXTRA_TARGET_TRANSLATION_ID, existingTranslation.id)
            setResult(RESULT_DUPLICATE, data)
            finish()
        }
    }

    private fun newProjectCreated(targetTranslation: TargetTranslation) {
        viewModel.newTargetTranslationId = targetTranslation.id

        val data = Intent()
        data.putExtra(EXTRA_TARGET_TRANSLATION_ID, viewModel.newTargetTranslationId)
        setResult(RESULT_OK, data)
        finish()
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
        val searchViewAction = MenuItemCompat.getActionView(searchMenuItem) as SearchView
        searchViewAction.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(s: String): Boolean {
                return true
            }

            override fun onQueryTextChange(s: String): Boolean {
                fragment!!.onSearchQuery(s)
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
            R.id.action_add_language -> {
                AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                    .setTitle(R.string.title_new_language_code)
                    .setMessage(R.string.confirm_start_new_language_code)
                    .setPositiveButton(R.string.label_continue) { _, _ ->
                        val requestNewLanguageIntent = Intent(
                            this@NewTargetTranslationActivity,
                            NewTempLanguageActivity::class.java
                        )
                        startActivityForResult(requestNewLanguageIntent, NEW_LANGUAGE_REQUEST)
                    }
                    .setNegativeButton(R.string.title_cancel, null)
                    .show()
                return true
            }

            // TODO: 10/18/16 display dialog for updating
            R.id.action_update -> return true

            else -> return super.onOptionsItemSelected(item)
        }
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_TARGET_TRANSLATION_ID, viewModel.newTargetTranslationId)
        outState.putInt(STATE_DIALOG_SHOWN, dialogShown.value)
        outState.putBoolean(STATE_NEW_LANGUAGE, viewModel.createdNewLanguage)
        if (viewModel.selectedTargetLanguage != null) {
            var targetLanguageJson: JSONObject? = null
            try {
                targetLanguageJson = viewModel.selectedTargetLanguage!!.toJSON()
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            if (targetLanguageJson != null) {
                outState.putString(STATE_TARGET_LANGUAGE, targetLanguageJson.toString())
            }
        } else {
            outState.remove(STATE_TARGET_LANGUAGE)
        }

        super.onSaveInstanceState(outState)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (NEW_LANGUAGE_REQUEST == requestCode) {
            if (RESULT_OK == resultCode) {
                val rawResponse = data?.getStringExtra(NewTempLanguageActivity.EXTRA_LANGUAGE_REQUEST)
                val registered = viewModel.registerTempLanguage(rawResponse)
                if (registered) {
                    confirmTempLanguage()
                } else {
                    AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.error)
                        .setMessage(R.string.try_again)
                        .show()
                }
            } else if (RESULT_FIRST_USER == resultCode) {
                val secondResultCode =
                    data!!.getIntExtra(NewTempLanguageActivity.EXTRA_RESULT_CODE, -1)
                if (secondResultCode == NewTempLanguageActivity.RESULT_MISSING_QUESTIONNAIRE) {
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.missing_questionnaire,
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                } else if (secondResultCode == NewTempLanguageActivity.RESULT_USE_EXISTING_LANGUAGE) {
                    val targetLanguageId =
                        data.getStringExtra(NewTempLanguageActivity.EXTRA_LANGUAGE_ID)
                    val targetLanguage = viewModel.getTargetLanguage(targetLanguageId)
                    if (targetLanguage != null) {
                        onItemClick(targetLanguage)
                    }
                } else {
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.error,
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                }
            }
        }
    }

    /**
     * for keeping track if dialog is being shown for orientation changes
     */
    enum class DialogShown {
        NONE,
        RENAME_CONFLICT;

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
        const val EXTRA_TARGET_TRANSLATION_ID: String = "extra_target_translation_id"
        const val EXTRA_CHANGE_TARGET_LANGUAGE_ONLY: String = "extra_change_target_language_only"
        const val RESULT_DUPLICATE: Int = 2
        const val RESULT_MERGE_CONFLICT: Int = 3
        private const val STATE_TARGET_TRANSLATION_ID = "state_target_translation_id"
        private const val STATE_TARGET_LANGUAGE = "state_target_language_id"
        const val STATE_DIALOG_SHOWN: String = "state_dialog_shown"
        const val RESULT_ERROR: Int = 3
        val TAG: String = NewTargetTranslationActivity::class.java.simpleName
        const val NEW_LANGUAGE_REQUEST: Int = 1001
        const val NEW_LANGUAGE_CONFIRMATION: String = "new-language-confirmation"
        private const val STATE_NEW_LANGUAGE = "new_language"
        const val INVALID: Int = -1
        const val EXTRA_DISABLED_LANGUAGES: String = "extra_disabled_language_ids"
    }
}