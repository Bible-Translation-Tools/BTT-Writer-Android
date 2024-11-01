package com.door43.translationstudio.ui.newtranslation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import com.door43.translationstudio.databinding.FragmentProjectListItemBinding
import org.unfoldingword.door43client.models.CategoryEntry
import java.util.Collections
import java.util.Locale

/**
 * Created by joel on 9/4/2015.
 */
class ProjectCategoryAdapter : BaseAdapter() {
    private val categories = arrayListOf<CategoryEntry>()
    private val filteredCategories = arrayListOf<CategoryEntry>()
    private var projectFilter: ProjectCategoryFilter = ProjectCategoryFilter()

    fun setCategories(categories: List<CategoryEntry>) {
        this.categories.clear()
        this.categories.addAll(categories)

        filteredCategories.clear()
        filteredCategories.addAll(categories)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return filteredCategories.size
    }

    override fun getItem(position: Int): CategoryEntry {
        return filteredCategories[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder

        if (convertView == null) {
            val binding = FragmentProjectListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            holder = ViewHolder(binding)
        } else {
            holder = convertView.tag as ViewHolder
        }

        val item = getItem(position)

        // render view
        holder.binding.projectName.text = item.name
        if (item.entryType == CategoryEntry.Type.PROJECT) {
            holder.binding.moreIcon.visibility = View.GONE
        } else {
            holder.binding.moreIcon.visibility = View.VISIBLE
        }

        // TODO: render icon
        return holder.binding.root
    }

    /**
     * Returns the project filter
     * @return
     */
    val filter: Filter
        get() = projectFilter

    class ViewHolder(val binding: FragmentProjectListItemBinding) {
        init {
            binding.root.tag = this
        }
    }

    /**
     * A filter for projects
     */
    private inner class ProjectCategoryFilter : Filter() {
        override fun performFiltering(charSequence: CharSequence?): FilterResults {
            val results = FilterResults()
            if (charSequence.isNullOrEmpty()) {
                // no filter
                results.values = categories
                results.count = categories.size
            } else {
                // perform filter
                val filteredCategories: MutableList<CategoryEntry> = ArrayList()
                for (category in categories) {
                    var match = false
                    if (category.entryType == CategoryEntry.Type.PROJECT) {
                        // match the project id
                        match = category.slug.lowercase(Locale.getDefault()).startsWith(
                            charSequence.toString().lowercase(
                                Locale.getDefault()
                            )
                        )
                    }
                    if (!match) {
                        val categoryComponents = category.slug.split("-".toRegex())
                        val titleComponents = category.name.split(" ".toRegex())
                        if (category.name.lowercase(Locale.getDefault()).startsWith(
                                charSequence.toString().lowercase(
                                    Locale.getDefault()
                                )
                            )
                        ) {
                            // match the project title in any language
                            match = true
                        } else if (category.sourceLanguageSlug.lowercase(Locale.getDefault())
                                .startsWith(
                                    charSequence.toString().lowercase(
                                        Locale.getDefault()
                                    )
                                )
                        ) { // || l.getName().toLowerCase().startsWith(charSequence.toString().toLowerCase())) {
                            // match the language id or name
                            match = true
                        } else {
                            // match category id components
                            for (component in categoryComponents) {
                                if (component.lowercase(Locale.getDefault()).startsWith(
                                        charSequence.toString().lowercase(
                                            Locale.getDefault()
                                        )
                                    )
                                ) {
                                    match = true
                                    break
                                }
                            }
                            if (!match) {
                                // match title components
                                for (component in titleComponents) {
                                    if (component.lowercase(Locale.getDefault()).startsWith(
                                            charSequence.toString().lowercase(
                                                Locale.getDefault()
                                            )
                                        )
                                    ) {
                                        match = true
                                        break
                                    }
                                }
                            }
                        }
                    }
                    if (match) {
                        filteredCategories.add(category)
                    }
                }
                results.values = filteredCategories
                results.count = filteredCategories.size
            }
            return results
        }

        override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
            val filteredProjects = (filterResults.values as? List<CategoryEntry>)
            filteredCategories.clear()
            if (filteredProjects != null) {
                filteredCategories.addAll(filteredProjects)
            }
            notifyDataSetChanged()
        }
    }

    companion object {
        /**
         * Sorts project categories by id
         * @param categories
         * @param referenceId categories are sorted according to the reference id
         */
        private fun sortProjectCategories(
            categories: List<CategoryEntry>,
            referenceId: CharSequence
        ) {
            Collections.sort(categories) { lhs, rhs ->
                var lhId = lhs.slug
                var rhId = rhs.slug
                // give priority to matches with the reference
                if (lhId.startsWith(referenceId.toString().lowercase(Locale.getDefault()))) {
                    lhId = "!$lhId"
                }
                if (rhId.startsWith(referenceId.toString().lowercase(Locale.getDefault()))) {
                    rhId = "!$rhId"
                }
                lhId.compareTo(rhId, ignoreCase = true)
            }
        }
    }
}
