package com.door43.translationstudio.ui.newtranslation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentLanguageListBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.Searchable
import com.door43.translationstudio.ui.viewmodels.NewTargetTranslationModel
import org.unfoldingword.door43client.models.TargetLanguage

/**
 * Created by joel on 9/4/2015.
 */
class TargetLanguageListFragment : BaseFragment(), Searchable {
    private var listener: OnItemClickListener? = null
    private val adapter by lazy { TargetLanguageAdapter() }

    private var _binding: FragmentLanguageListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewTargetTranslationModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageListBinding.inflate(inflater, container, false)

        with(binding) {
            adapter.setLanguages(viewModel.getTargetLanguages())
            val args = arguments
            if (args != null) {
                val disabledLanguages = args.getStringArray(
                    NewTargetTranslationActivity.EXTRA_DISABLED_LANGUAGES
                ) ?: arrayOf()
                adapter.setDisabledLanguages(disabledLanguages.toList())
            }
            languages.adapter = adapter
            languages.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                if (!adapter.isItemDisabled(position)) {
                    listener?.onItemClick(adapter.getItem(position))
                }
            }
            search.searchText.setHint(R.string.choose_target_language)
            search.searchText.isEnabled = false
            search.searchBackButton.visibility = View.GONE
            search.searchMagIcon.visibility = View.GONE
        }

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = activity as OnItemClickListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$activity must implement OnItemClickListener")
        }
    }

    override fun onSearchQuery(query: String) {
        adapter.getFilter().filter(query)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface OnItemClickListener {
        fun onItemClick(targetLanguage: TargetLanguage)
    }
}
