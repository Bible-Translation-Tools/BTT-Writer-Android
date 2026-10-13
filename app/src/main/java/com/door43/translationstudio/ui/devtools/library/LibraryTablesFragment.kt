package com.door43.translationstudio.ui.devtools.library

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentLanguageListBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.viewmodels.EditLibraryViewModel

class LibraryTablesFragment : BaseFragment() {
    private var _binding: FragmentLanguageListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditLibraryViewModel by activityViewModels()
    private val adapter by lazy { LibraryRowAdapter() }
    private var listener: Listener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageListBinding.inflate(inflater, container, false)

        with(binding) {
            adapter.setRows(viewModel.tables.map { LibraryRow(it.name, getString(it.titleRes)) })
            languages.adapter = adapter
            languages.setOnItemClickListener { _, _, position, _ ->
                listener?.onTableSelected(viewModel.tables[position])
            }
            search.searchText.setHint(R.string.edit_library_choose_table)
            search.searchText.isEnabled = false
            search.searchBackButton.visibility = View.GONE
            search.searchMagIcon.visibility = View.GONE
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.title_activity_edit_library)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
            ?: throw ClassCastException("$context must implement LibraryTablesFragment.Listener")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface Listener {
        fun onTableSelected(table: LibraryTable)
    }
}
