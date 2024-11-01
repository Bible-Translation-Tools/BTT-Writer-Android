package com.door43.translationstudio.ui.translate

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.door43.translationstudio.App.Companion.showKeyboard
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.DialogChooseSourceTranslationBinding
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.Callbacks
import com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.OnItemClickListener
import com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.RCItem
import com.door43.translationstudio.ui.viewmodels.ChooseSourcesViewModel
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.min

/**
 * Created by joel on 9/15/2015.
 */
@AndroidEntryPoint
class ChooseSourceTranslationDialog : DialogFragment(), OnItemClickListener {

    @Inject lateinit var typography: Typography

    private val viewModel: ChooseSourcesViewModel by viewModels()

    private var clickListener: OnClickListener? = null
    private var progressDialog: ProgressHelper.ProgressDialog? = null
    private var initializing = true

    private lateinit var adapter: ChooseSourceTranslationAdapter
    private var _binding: DialogChooseSourceTranslationBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogChooseSourceTranslationBinding.inflate(inflater, container, false)

        val args = arguments
        if (args == null) {
            dismiss()
        } else {
            val targetTranslationId = args.getString(ARG_TARGET_TRANSLATION_ID, null)
            viewModel.setTargetTranslation(targetTranslationId)

            if (viewModel.noTargetTranslation()) {
                // missing target translation
                dismiss()
            }
        }

        progressDialog = ProgressHelper.newInstance(
            parentFragmentManager,
            R.string.loading_sources,
            false
        )

        adapter = ChooseSourceTranslationAdapter(requireContext(), typography)
        adapter.setItemClickListener(this)

        with(binding) {
            searchBar.searchText.setHint(R.string.choose_source_translations)
            searchBar.searchText.isEnabled = true
            searchBar.searchText.isFocusable = true
            searchBar.searchText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                }
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                }
                override fun afterTextChanged(s: Editable) {
                    adapter.applySearch(s.toString())
                }
            })

            searchBar.searchBackButton.visibility = View.GONE
            searchBar.searchMagIcon.setOnClickListener {
                searchBar.searchText.requestFocus()
                showKeyboard(activity, searchBar.searchText, false)
            }

            list.adapter = adapter

            updateButton.setOnClickListener {
                AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                    .setTitle(R.string.warning_title)
                    .setMessage(R.string.update_warning)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        viewModel.cancelJobs()
                        clickListener?.onUpdateSources()
                        dismiss()
                    }
                    .setNegativeButton(R.string.menu_cancel, null)
                    .show()
            }

            cancelButton.setOnClickListener {
                viewModel.cancelJobs()
                clickListener?.onCancelTabsDialog(viewModel.targetTranslation.id)
                dismiss()
            }

            confirmButton.setOnClickListener {
                viewModel.cancelJobs()
                // collect selected source translations
                val count = adapter.count
                val resourceContainerSlugs = arrayListOf<String>()
                for (i in 0 until count) {
                    if (adapter.isSelectableItem(i)) {
                        val item = adapter.getItem(i)
                        if (item.selected) {
                            resourceContainerSlugs.add(item.containerSlug)
                        }
                    }
                }
                clickListener?.onConfirmTabsDialog(resourceContainerSlugs)
                dismiss()
            }
        }

        setupObservers()

        viewModel.loadData()
        return binding.root
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
        viewModel.items.observe(this) {
            adapter.setItems(it)
            initializing = false
        }
        viewModel.downloadResult.observe(this) {
            it?.let { result ->
                for (rc in result.containers) {
                    // reset cached containers that were downloaded
                    ContainerCache.remove(rc.slug)
                }
                if (result.success) {
                    val snack = Snackbar.make(
                        this@ChooseSourceTranslationDialog.requireView(),
                        resources.getString(R.string.download_complete),
                        Snackbar.LENGTH_SHORT
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                    adapter.markItemDownloaded(viewModel.downloadItemPosition)
                } else {
                    val snack = Snackbar.make(
                        this@ChooseSourceTranslationDialog.requireView(),
                        resources.getString(R.string.download_failed),
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
        viewModel.itemResult.observe(this) {
            it?.let { result ->
                adapter.setItemHasUpdates(result.position, result.hasUpdates)
            }
        }
    }

    /**
     * Assigns a listener for this dialog
     * @param listener
     */
    fun setOnClickListener(listener: OnClickListener?) {
        clickListener = listener
    }

    override fun onCheckForItemUpdates(containerSlug: String, position: Int) {
        viewModel.checkForContainerUpdates(containerSlug, position)
    }

    override fun onTriggerDownload(
        item: RCItem,
        position: Int,
        callback: Callbacks.OnDownloadCancel
    ) {
        val format = resources.getString(R.string.download_source_language)
        val message = String.format(format, item.title)
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.title_download_source_language)
            .setMessage(message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.downloadResourceContainer(item.sourceTranslation, position)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                if (item.downloaded) {
                    // allow selecting if downloaded already
                    callback.onCancel(position)
                }
            }
            .show()
    }

    override fun onTriggerDeleteContainer(
        containerSlug: String,
        position: Int,
        callback: Callbacks.OnDeleteContainer
    ) {
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.label_delete)
            .setMessage(R.string.confirm_delete_project)
            .setPositiveButton(R.string.confirm) { _, _ ->
                viewModel.deleteResourceContainer(containerSlug)
                callback.onDelete(position)
            }
            .setNegativeButton(R.string.menu_cancel, null)
            .show()
    }

    interface OnClickListener {
        fun onCancelTabsDialog(targetTranslationId: String)
        fun onConfirmTabsDialog(sourceTranslationIds: List<String>)
        fun onUpdateSources()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        progressDialog?.dismiss()
        progressDialog = null
        viewModel.clearResults()
    }

    companion object {
        const val ARG_TARGET_TRANSLATION_ID: String = "target_translation_id"
        val TAG: String = ChooseSourceTranslationDialog::class.java.simpleName
    }
}
