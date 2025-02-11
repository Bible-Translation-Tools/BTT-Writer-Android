package com.door43.translationstudio.ui.newtranslation

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentProjectListBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.Searchable
import com.door43.translationstudio.ui.viewmodels.NewTargetTranslationModel
import org.unfoldingword.door43client.models.CategoryEntry

/**
 * Created by joel on 9/4/2015.
 */
class ProjectListFragment : BaseFragment(), Searchable {
    private var listener: OnItemClickListener? = null
    private val adapter by lazy { ProjectCategoryAdapter() }

    private var _binding: FragmentProjectListBinding? = null
    val binding get() = _binding!!

    private val viewModel: NewTargetTranslationModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProjectListBinding.inflate(inflater, container, false)

        with(binding) {
            search.searchText.setHint(R.string.choose_a_project)
            search.searchText.isEnabled = false

            search.searchBackButton.visibility = View.GONE
            search.searchMagIcon.setBackgroundResource(R.drawable.ic_refresh_secondary_24dp)

            // TODO: set up update button
            adapter.setCategories(viewModel.getCategories())
            list.adapter = adapter
            list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                val category = adapter.getItem(position)
                if (category.entryType == CategoryEntry.Type.PROJECT) {
                    listener?.onItemClick(category.slug)
                } else {
                    // TODO: we need to display another back arrow to back up a level in the categories
                    adapter.setCategories(viewModel.getCategories(category.id))
                    search.searchMagIcon.visibility = View.GONE
                }
            }
        }

        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            this.listener = context as OnItemClickListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnItemClickListener")
        }
    }

    override fun onSearchQuery(query: String) {
        adapter.filter.filter(query)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    interface OnItemClickListener {
        fun onItemClick(projectId: String)
    }
}
