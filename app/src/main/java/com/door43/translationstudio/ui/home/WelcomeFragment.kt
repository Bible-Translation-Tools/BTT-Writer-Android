package com.door43.translationstudio.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentTargetTranslationWelcomeBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.viewmodels.HomeViewModel

/**
 * Displays a welcome message with instructions about creating target translations
 */
class WelcomeFragment : BaseFragment() {
    private var listener: OnCreateNewTargetTranslation? = null

    private val viewModel: HomeViewModel by activityViewModels()

    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private var _binding: FragmentTargetTranslationWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTargetTranslationWelcomeBinding.inflate(inflater, container, false)

        binding.extraAddTargetTranslationButton.setOnClickListener {
            listener?.onCreateNewTargetTranslation()
        }

        setupObservers()

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = ProgressHelper.newInstance(
            childFragmentManager,
            R.string.loading,
            false
        )
    }

    private fun setupObservers() {
        viewModel.progress.observe(viewLifecycleOwner) {
            if (it != null) {
                progressDialog?.show()
                progressDialog?.setProgress(it.progress)
                progressDialog?.setMessage(it.message)
                progressDialog?.setMax(it.max)
            } else {
                progressDialog?.dismiss()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            this.listener = context as OnCreateNewTargetTranslation
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnCreateNewTargetTranslation")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface OnCreateNewTargetTranslation {
        fun onCreateNewTargetTranslation()
    }
}
