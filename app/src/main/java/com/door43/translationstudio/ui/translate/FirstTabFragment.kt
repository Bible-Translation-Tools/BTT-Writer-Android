package com.door43.translationstudio.ui.translate

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.door43.translationstudio.databinding.FragmentFirstTabBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONException
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.unfoldingword.tools.logger.Logger

/**
 * Gives some instructions when no source text has been selected
 */
class FirstTabFragment : BaseFragment(), ChooseSourceTranslationDialog.OnClickListener {

    private var listener: OnEventListener? = null

    private val viewModel: TargetTranslationViewModel by activityViewModel()

    private var _binding: FragmentFirstTabBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstTabBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        requireNotNull(args)

        setupObservers()

        try {
            val p = viewModel.getProject()
            binding.sourceTranslationTitle.text = "${p?.name} - ${viewModel.targetTranslation.targetLanguageName}"
        } catch (e: Exception) {
            Logger.e(
                FirstTabFragment::class.java.simpleName,
                "Error getting resource container for '${viewModel.targetTranslation.id}'",
                e
            )
        }

        val clickListener = View.OnClickListener {
            val ft = parentFragmentManager.beginTransaction()
            val prev = parentFragmentManager.findFragmentByTag("tabsDialog")
            if (prev != null) {
                ft.remove(prev)
            }
            ft.addToBackStack(null)

            val dialog = ChooseSourceTranslationDialog()
            val args1 = Bundle()
            args1.putString(
                ChooseSourceTranslationDialog.ARG_TARGET_TRANSLATION_ID,
                viewModel.targetTranslation.id
            )
            dialog.setOnClickListener(this@FirstTabFragment)
            dialog.arguments = args1
            dialog.show(ft, "tabsDialog")
        }

        binding.newTabButton.setOnClickListener(clickListener)
        binding.secondaryNewTabButton.setOnClickListener(clickListener)

        // attach to tabs dialog
        if (savedInstanceState != null) {
            val dialog = parentFragmentManager.findFragmentByTag("tabsDialog") as? ChooseSourceTranslationDialog
            dialog?.setOnClickListener(this)
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.model
                        .map { it.items }
                        .distinctUntilChanged()
                        .collect { items ->
                            if (items.isNotEmpty()) {
                                listener?.onHasSourceTranslations()
                            }
                        }
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            this.listener = context as OnEventListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement FirstTabFragment.OnEventListener")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * user has selected to update sources
     */
    override fun onUpdateSources() {
        listener?.onUpdateSources()
    }

    override fun onCancelTabsDialog(targetTranslationId: String) {}

    override fun onConfirmTabsDialog(sourceTranslationIds: List<String>) {
        val oldSourceTranslationIds = viewModel.getOpenSourceTranslations()
        for (id in oldSourceTranslationIds) {
            viewModel.removeOpenSourceTranslation(id)
        }

        if (sourceTranslationIds.isNotEmpty()) {
            // save open source language tabs
            for (slug in sourceTranslationIds) {
                val t = viewModel.getTranslation(slug)
                if (t != null) {
                    val modifiedAt = viewModel.getResourceContainerLastModified(t)
                    try {
                        viewModel.addOpenSourceTranslation(slug)
                        val targetTranslation = viewModel.targetTranslation
                        try {
                            targetTranslation.addSourceTranslation(t, modifiedAt)
                        } catch (e: JSONException) {
                            Logger.e(
                                this.javaClass.name,
                                "Failed to record source translation ($slug) usage in the target translation ${targetTranslation.id}",
                                e
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // redirect back to previous mode
            listener?.onHasSourceTranslations()
        }
    }

    interface OnEventListener {
        fun onHasSourceTranslations()

        /**
         * user has selected to update sources
         */
        fun onUpdateSources()
    }
}