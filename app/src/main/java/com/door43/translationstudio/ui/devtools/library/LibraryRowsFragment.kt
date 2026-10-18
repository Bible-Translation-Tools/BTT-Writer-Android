package com.door43.translationstudio.ui.devtools.library

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.databinding.FragmentLanguageListBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.Searchable
import com.door43.translationstudio.ui.viewmodels.EditLibraryViewModel

class LibraryRowsFragment : BaseFragment(), Searchable {
    private var _binding: FragmentLanguageListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditLibraryViewModel by activityViewModels()
    private val adapter by lazy { LibraryRowAdapter() }
    private var listener: Listener? = null
    private lateinit var table: LibraryTable
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        table = LibraryTable.valueOf(requireArguments().getString(ARG_TABLE)!!)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageListBinding.inflate(inflater, container, false)

        with(binding) {
            languages.adapter = adapter
            languages.setOnItemClickListener { _, _, position, _ ->
                listener?.onRowSelected(table, adapter.getItem(position).id)
            }
            search.searchText.setHint(table.titleRes)
            search.searchText.isEnabled = false
            search.searchBackButton.visibility = View.GONE
            search.searchMagIcon.visibility = View.GONE
        }

        viewModel.rows.observe(viewLifecycleOwner) {
            adapter.setRows(it)
            if (query.isNotEmpty()) adapter.getFilter().filter(query)
        }
        if (viewModel.rows.value == null) viewModel.loadRows(table)

        return binding.root
    }

    override fun onSearchQuery(query: String) {
        this.query = query
        adapter.getFilter().filter(query)
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(table.titleRes)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
            ?: throw ClassCastException("$context must implement LibraryRowsFragment.Listener")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface Listener {
        fun onRowSelected(table: LibraryTable, rowId: String)
    }

    companion object {
        private const val ARG_TABLE = "table"

        fun newInstance(table: LibraryTable): LibraryRowsFragment {
            return LibraryRowsFragment().apply {
                arguments = Bundle().apply { putString(ARG_TABLE, table.name) }
            }
        }
    }
}
