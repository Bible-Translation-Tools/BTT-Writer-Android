package com.door43.translationstudio.ui.draft

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.door43.translationstudio.R
import com.door43.translationstudio.core.RenderingProvider
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.ActivityDraftPreviewBinding
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.viewmodels.DraftViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import java.security.InvalidParameterException
import javax.inject.Inject

@AndroidEntryPoint
class DraftActivity : BaseActivity() {
    @Inject lateinit var typography: Typography
    @Inject lateinit var renderingProvider: RenderingProvider

    private val adapter by lazy { DraftAdapter(typography, renderingProvider) }
    private var sourceContainer: ResourceContainer? = null

    private lateinit var binding: ActivityDraftPreviewBinding
    private val viewModel: DraftViewModel by viewModels()

    private var progressDialog: ProgressHelper.ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDraftPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // validate parameters
        val extras = intent.extras
        if (extras != null) {
            val targetTranslationId = extras.getString(EXTRA_TARGET_TRANSLATION_ID, null)
            viewModel.loadDraftTranslations(targetTranslationId)
        } else {
            throw InvalidParameterException("This activity expects some arguments")
        }

        with(binding) {
            drafts.recyclerView.layoutManager = LinearLayoutManager(this@DraftActivity)
            drafts.recyclerView.itemAnimator = DefaultItemAnimator()
            drafts.recyclerView.adapter = adapter

            fab.setOnClickListener {
                AlertDialog.Builder(this@DraftActivity, R.style.AppTheme_Dialog)
                    .setTitle(R.string.import_draft)
                    .setMessage(R.string.import_draft_confirmation)
                    .setNegativeButton(R.string.menu_cancel, null)
                    .setPositiveButton(R.string.label_import) { _, _ -> // // TODO: 1/20/2016 use the draft from the selected tab
                        viewModel.importDraft(sourceContainer!!)
                    }.show()
            }
        }

        setupObservers()
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
        viewModel.draftTranslations.observe(this) {
            it?.let { drafts ->
                if (drafts.isEmpty()) {
                    finish()
                } else {
                    onDraftsLoaded(drafts)
                }
            }
        }
        viewModel.importResult.observe(this) {
            it?.let { result ->
                if (result.targetTranslation != null) {
                    finish()
                } else {
                    AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.error)
                        .setMessage(R.string.translation_import_failed)
                        .setNeutralButton(R.string.dismiss, null)
                        .show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        progressDialog = ProgressHelper.newInstance(this, R.string.loading, false)
    }

    private fun onDraftsLoaded(drafts: List<Translation>) {
        // TODO: 10/7/16 display translations in tabs like in the translate modes
        //  so they can choose which one to import
        val draftTranslation = drafts[0]

        sourceContainer = viewModel.getResourceContainer(draftTranslation.resourceContainerSlug)
        if (sourceContainer == null) {
            finish()
        }

        val sourceLanguage = viewModel.getSourceLanguage(sourceContainer!!)
        adapter.setData(sourceContainer!!, sourceLanguage)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val TAG: String = "DraftActivity"
        const val EXTRA_TARGET_TRANSLATION_ID: String = "target_translation_id"
    }
}