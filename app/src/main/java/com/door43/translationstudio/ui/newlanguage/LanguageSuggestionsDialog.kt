package com.door43.translationstudio.ui.newlanguage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.AdapterView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.databinding.DialogTargetLanguageSuggestionsBinding
import com.door43.translationstudio.ui.newtranslation.TargetLanguageAdapter
import com.door43.translationstudio.ui.viewmodels.NewTempLanguageViewModel
import org.unfoldingword.door43client.models.TargetLanguage

/**
 * Created by joel on 6/9/16.
 */
class LanguageSuggestionsDialog : DialogFragment() {
    private var listener: OnClickListener? = null
    private val adapter by lazy { TargetLanguageAdapter() }
    private var targetLanguages: List<TargetLanguage> = arrayListOf()

    private var _binding: DialogTargetLanguageSuggestionsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewTempLanguageViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogTargetLanguageSuggestionsBinding.inflate(inflater, container, false)

        val args = arguments
        if (args != null) {
            targetLanguages = viewModel.findTargetLanguages(args.getString(ARG_LANGUAGE_QUERY))
        } else {
            dismiss()
            return binding.root
        }

        with (binding) {
            cancelButton.setOnClickListener {
                listener?.onDismissLanguageSuggestion()
                dismiss()
            }
            listView.adapter = adapter
            adapter.setLanguages(targetLanguages)
            listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                listener?.onAcceptLanguageSuggestion(adapter.getItem(position))
                dismiss()
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Sets the listener to receive click events
     * @param listener
     */
    fun setOnClickListener(listener: OnClickListener?) {
        this.listener = listener
    }

    interface OnClickListener {
        fun onAcceptLanguageSuggestion(language: TargetLanguage?)
        fun onDismissLanguageSuggestion()
    }

    companion object {
        const val TAG: String = "language_suggestions_dialog"
        const val ARG_LANGUAGE_QUERY: String = "language_query"
    }
}
