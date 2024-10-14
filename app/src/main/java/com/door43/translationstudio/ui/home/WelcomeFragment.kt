package com.door43.translationstudio.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.door43.translationstudio.databinding.FragmentTargetTranslationWelcomeBinding
import com.door43.translationstudio.ui.BaseFragment

/**
 * Displays a welcome message with instructions about creating target translations
 */
class WelcomeFragment : BaseFragment() {
    private var listener: OnCreateNewTargetTranslation? = null

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

        return binding.root
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
