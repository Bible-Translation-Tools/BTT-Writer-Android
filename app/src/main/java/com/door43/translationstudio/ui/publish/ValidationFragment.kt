package com.door43.translationstudio.ui.publish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.door43.translationstudio.core.RenderingProvider
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentPublishValidationListBinding
import com.door43.translationstudio.ui.viewmodels.ValidationViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.security.InvalidParameterException

/**
 * Created by joel on 9/20/2015.
 */
class ValidationFragment : PublishStepFragment(), ValidationAdapter.OnClickListener {

    val typography: Typography by inject()
    val renderingProvider: RenderingProvider by inject()

    private val adapter by lazy { ValidationAdapter(typography, renderingProvider) }

    private var _binding: FragmentPublishValidationListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ValidationViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPublishValidationListBinding.inflate(layoutInflater, container, false)

        val args = requireArguments()
        val targetTranslationId = args.getString(PublishActivity.EXTRA_TARGET_TRANSLATION_ID)
        val sourceTranslationId = args.getString(ARG_SOURCE_TRANSLATION_ID)
        if (targetTranslationId == null) {
            throw InvalidParameterException("a valid target translation id is required")
        }
        if (sourceTranslationId == null) {
            throw InvalidParameterException("a valid source translation id is required")
        }

        with (binding) {
            adapter.setOnClickListener(this@ValidationFragment)

            validationItems.layoutManager = LinearLayoutManager(activity)
            validationItems.itemAnimator = DefaultItemAnimator()
            validationItems.adapter = adapter

            // display loading view
            validationItems.visibility = View.GONE
            loadingLayout.visibility = View.VISIBLE
        }

        setupObservers()

        viewModel.validateProject(targetTranslationId, sourceTranslationId)

        return binding.root
    }

    private fun setupObservers() {
        viewModel.validations.observe(viewLifecycleOwner) {
            it?.let { validations ->
                adapter.setValidations(validations)
                binding.validationItems.visibility = View.VISIBLE
                binding.loadingLayout.visibility = View.GONE
                // TODO: animate
            }
        }
    }

    override fun onClickReview(targetTranslationId: String, chapterId: String, frameId: String) {
        openReview(targetTranslationId, chapterId, frameId)
    }

    override fun onClickNext() {
        listener?.nextStep()
    }

    override fun onDestroy() {
        adapter.setOnClickListener(null)
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
