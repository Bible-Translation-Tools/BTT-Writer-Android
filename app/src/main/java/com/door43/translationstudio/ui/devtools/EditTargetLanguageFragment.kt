package com.door43.translationstudio.ui.devtools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentEditTargetLanguageBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.viewmodels.EditLibraryViewModel
import org.unfoldingword.door43client.models.TargetLanguage

class EditTargetLanguageFragment : BaseFragment() {
    private var _binding: FragmentEditTargetLanguageBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditLibraryViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditTargetLanguageBinding.inflate(inflater, container, false)

        val slug = requireArguments().getString(ARG_SLUG)!!
        val language = viewModel.getTargetLanguage(slug)
        if (language == null) {
            parentFragmentManager.popBackStack()
            return binding.root
        }

        with(binding) {
            slugText.text = language.slug
            nameText.setText(language.name)
            anglicizedNameText.setText(language.anglicizedName)
            directionGroup.check(
                if (language.direction == "rtl") R.id.direction_rtl else R.id.direction_ltr
            )
            regionGroup.findViewWithTag<RadioButton>(language.region)?.isChecked = true
            gatewayCheck.isChecked = language.isGatewayLanguage
            saveButton.setOnClickListener { confirmSave(readForm(language)) }
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                viewModel.clearSaveResult()
                showSaveResult(it)
            }
        }

        return binding.root
    }

    private fun readForm(current: TargetLanguage): TargetLanguage = with(binding) {
        val region = regionGroup.findViewById<RadioButton>(regionGroup.checkedRadioButtonId)
        TargetLanguage(
            current.slug,
            nameText.text.toString(),
            anglicizedNameText.text.toString(),
            if (directionGroup.checkedRadioButtonId == R.id.direction_rtl) "rtl" else "ltr",
            region?.tag as? String ?: current.region,
            gatewayCheck.isChecked
        )
    }

    private fun confirmSave(language: TargetLanguage) {
        AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog)
            .setTitle(R.string.confirm)
            .setMessage(R.string.edit_library_confirm_save)
            .setPositiveButton(R.string.label_continue) { _, _ -> viewModel.save(language) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSaveResult(success: Boolean) {
        if (success) {
            AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog)
                .setTitle(R.string.edit_library)
                .setMessage(R.string.edit_library_save_success)
                .setCancelable(false)
                .setPositiveButton(R.string.dismiss) { _, _ -> App.restart() }
                .setOnDismissListener { App.restart() }
                .show()
        } else {
            AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog)
                .setTitle(R.string.error)
                .setMessage(R.string.edit_library_save_failed)
                .setPositiveButton(R.string.label_ok, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SLUG = "slug"

        fun newInstance(slug: String) = EditTargetLanguageFragment().apply {
            arguments = Bundle().apply { putString(ARG_SLUG, slug) }
        }
    }
}
