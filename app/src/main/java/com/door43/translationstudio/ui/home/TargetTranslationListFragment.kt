package com.door43.translationstudio.ui.home

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.activityViewModels
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentTargetTranslationListBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.home.TargetTranslationAdapter.SortByColumnType
import com.door43.translationstudio.ui.home.TargetTranslationAdapter.SortProjectColumnType
import com.door43.translationstudio.ui.viewmodels.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Displays a list of target translations
 */
@AndroidEntryPoint
class TargetTranslationListFragment : BaseFragment() {
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var typography: Typography

    private var listener: OnItemClickListener? = null
    private var sortProjectColumn = SortProjectColumnType.BibleOrder
    private var sortByColumn = SortByColumnType.ProjectThenLanguage

    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private lateinit var adapter: TargetTranslationAdapter
    private val viewModel: HomeViewModel by activityViewModels()

    private var _binding: FragmentTargetTranslationListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTargetTranslationListBinding.inflate(
            inflater,
            container,
            false
        )

        setupObservers()

        adapter = TargetTranslationAdapter(typography)
        adapter.setOnInfoClickListener(object : TargetTranslationAdapter.OnInfoClickListener {
            override fun onClick(item: TranslationItem) {
                val ft = parentFragmentManager.beginTransaction()
                val prev = parentFragmentManager.findFragmentByTag("infoDialog")
                if (prev != null) {
                    ft.remove(prev)
                }
                ft.addToBackStack(null)

                val dialog = TargetTranslationInfoDialog()
                val args = Bundle()
                args.putString(
                    TargetTranslationInfoDialog.ARG_TARGET_TRANSLATION_ID,
                    item.translation.id
                )
                dialog.arguments = args
                dialog.show(ft, "infoDialog")
            }
        })
        binding.translationsList.adapter = adapter

        // open target translation
        binding.translationsList.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            listener?.onItemClick(adapter.getItem(position))
        }

        if (savedInstanceState != null) {
            sortByColumn = SortByColumnType.fromInt(
                savedInstanceState.getInt(
                    STATE_SORT_BY_COLUMN,
                    sortByColumn.value
                )
            )
            sortProjectColumn = SortProjectColumnType.fromInt(
                savedInstanceState.getInt(
                    STATE_SORT_PROJECT_COLUMN, sortProjectColumn.value
                )
            )
        } else { // if not restoring states, get last values
            sortByColumn = SortByColumnType.fromString(
                prefRepository.getDefaultPref(SORT_BY_COLUMN_ITEM, "0"),
                SortByColumnType.ProjectThenLanguage
            )
            sortProjectColumn = SortProjectColumnType.fromString(
                prefRepository.getDefaultPref(SORT_PROJECT_ITEM, "0"),
                SortProjectColumnType.BibleOrder
            )
        }
        adapter.sort(sortByColumn, sortProjectColumn)

        val projectTypes: MutableList<String> = ArrayList()
        projectTypes.add(this.resources.getString(R.string.sort_project_then_language))
        projectTypes.add(this.resources.getString(R.string.sort_language_then_project))
        projectTypes.add(this.resources.getString(R.string.sort_progress_then_project))
        val projectTypesAdapter =
            ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, projectTypes)
        projectTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.sortColumn.adapter = projectTypesAdapter
        binding.sortColumn.setSelection(sortByColumn.value)
        binding.sortColumn.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
//                    Logger.i(TAG, "Sort column item selected: " + position);
                sortByColumn = SortByColumnType.fromInt(position)
                prefRepository.getDefaultPref(SORT_BY_COLUMN_ITEM, sortByColumn.value.toString())
                adapter.sort(sortByColumn, sortProjectColumn)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        val bibleTypes: MutableList<String> = ArrayList()
        bibleTypes.add(this.resources.getString(R.string.sort_bible_order))
        bibleTypes.add(this.resources.getString(R.string.sort_alphabetical_order))
        val bibleTypesAdapter =
            ArrayAdapter(requireActivity(), android.R.layout.simple_spinner_item, bibleTypes)
        bibleTypesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.sortProjects.adapter = bibleTypesAdapter
        binding.sortProjects.setSelection(sortProjectColumn.value)
        binding.sortProjects.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
//                    Logger.i(TAG, "Sort project column item selected: " + position);
                sortProjectColumn = SortProjectColumnType.fromInt(position)
                prefRepository.setDefaultPref(
                    SORT_PROJECT_ITEM,
                    sortProjectColumn.value.toString()
                )
                adapter.sort(sortByColumn, sortProjectColumn)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressDialog = ProgressHelper.newInstance(
            parentFragmentManager,
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
        viewModel.translations.observe(viewLifecycleOwner) {
            it?.let { adapter.setTranslations(it) }
        }
    }

    fun reloadList() {
        val handler = Handler(Looper.getMainLooper())
        handler.post { adapter.notifyDataSetChanged() }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            this.listener = context as OnItemClickListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnItemClickListener")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        progressDialog = null
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putInt(STATE_SORT_BY_COLUMN, sortByColumn.value)
        out.putInt(STATE_SORT_PROJECT_COLUMN, sortProjectColumn.value)
        super.onSaveInstanceState(out)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface OnItemClickListener {
        fun onItemClick(item: TranslationItem)
    }

    companion object {
        val TAG: String = TargetTranslationListFragment::class.java.simpleName
        const val STATE_SORT_BY_COLUMN: String = "state_sort_by_column"
        const val STATE_SORT_PROJECT_COLUMN: String = "state_sort_project_column"
        const val SORT_PROJECT_ITEM: String = "sort_project_item"
        const val SORT_BY_COLUMN_ITEM: String = "sort_by_column_item"
    }
}
