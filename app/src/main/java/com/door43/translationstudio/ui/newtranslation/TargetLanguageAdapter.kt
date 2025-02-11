package com.door43.translationstudio.ui.newtranslation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentLanguageListItemBinding
import com.door43.util.ColorUtil
import org.unfoldingword.door43client.models.TargetLanguage
import java.util.Locale

/**
 * Created by joel on 9/4/2015.
 */
class TargetLanguageAdapter : BaseAdapter() {
    private val targetLanguages = arrayListOf<TargetLanguage>()
    private val filteredTargetLanguages = arrayListOf<TargetLanguage>()
    private val disabledLanguages = arrayListOf<String>()
    private val targetLanguageFilter = TargetLanguageFilter()

    override fun getCount(): Int {
        return filteredTargetLanguages.size
    }

    override fun getItem(position: Int): TargetLanguage {
        return filteredTargetLanguages[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    /**
     * checks if the item is disabled
     * @param position
     * @return
     */
    fun isItemDisabled(position: Int): Boolean {
        val l = getItem(position)
        return isLanguageDisabled(l.slug)
    }

    /**
     * Checks if the language is disabled
     * @return true if disabled
     */
    private fun isLanguageDisabled(id: String): Boolean {
        for (l in disabledLanguages) {
            if (id == l) return true
        }
        return false
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder

        if (convertView == null) {
            val binding = FragmentLanguageListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            holder = ViewHolder(binding)
        } else {
            holder = convertView.tag as ViewHolder
        }

        val language = getItem(position)

        // render view
        holder.bind(language)
        holder.setDisabled(isLanguageDisabled(language.slug))

        return holder.binding.root
    }

    fun setLanguages(targetLanguages: List<TargetLanguage>) {
        this.targetLanguages.clear()
        this.targetLanguages.addAll(targetLanguages.sorted())
        filteredTargetLanguages.clear()
        filteredTargetLanguages.addAll(targetLanguages)
        notifyDataSetChanged()
    }

    /**
     * Returns the target language filter
     * @return
     */
    fun getFilter(): Filter {
        return targetLanguageFilter
    }

    /**
     * Sets the language id's that will be disabled (not selectable)
     * @param disabledLanguages
     */
    fun setDisabledLanguages(disabledLanguages: List<String>) {
        this.disabledLanguages.clear()
        this.disabledLanguages.addAll(disabledLanguages)
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: FragmentLanguageListItemBinding) {
        private val context = binding.root.context

        init {
            binding.root.tag = this
        }

        fun bind(language: TargetLanguage) {
            binding.languageName.text = language.name
            binding.languageCode.text = language.slug
        }

        fun setDisabled(disabled: Boolean) {
            if (disabled) {
                binding.root.setBackgroundColor(ColorUtil.getColor(context, R.color.graph_background))
                binding.languageCode.setTextColor(ColorUtil.getColor(context, R.color.dark_disabled_text))
                binding.languageName.setTextColor(ColorUtil.getColor(context, R.color.dark_disabled_text))
            } else {
                binding.root.setBackgroundColor(
                    ColorUtil.getColor(
                        context,
                        android.R.color.transparent
                    )
                )
                binding.languageCode.setTextColor(ColorUtil.getColor(context, R.color.dark_secondary_text))
                binding.languageName.setTextColor(ColorUtil.getColor(context, R.color.dark_primary_text))
            }
        }
    }

    private inner class TargetLanguageFilter : Filter() {
        override fun performFiltering(charSequence: CharSequence): FilterResults {
            val results = FilterResults()
            if (charSequence.isEmpty()) {
                // no filter
                results.values = targetLanguages
                results.count = targetLanguages.size
            } else {
                // perform filter
                val filteredCategories = arrayListOf<TargetLanguage>()
                for (language in targetLanguages) {
                    // match the target language id
                    var match = language.slug.lowercase(Locale.getDefault()).startsWith(
                        charSequence.toString().lowercase(
                            Locale.getDefault()
                        )
                    )
                    if (!match) {
                        if (language.name.lowercase(Locale.getDefault()).contains(
                                charSequence.toString().lowercase(
                                    Locale.getDefault()
                                )
                            )
                        ) {
                            // match the target language name
                            match = true
                        }
                    }
                    if (match) {
                        filteredCategories.add(language)
                    }
                }
                results.values = filteredCategories
                results.count = filteredCategories.size
            }
            return results
        }

        override fun publishResults(charSequence: CharSequence, filterResults: FilterResults) {
            val filteredLanguages = filterResults.values as ArrayList<TargetLanguage>
            if (charSequence.isNotEmpty()) {
                sortTargetLanguages(filteredLanguages, charSequence)
            }
            filteredTargetLanguages.clear()
            filteredTargetLanguages.addAll(filteredLanguages)
            notifyDataSetChanged()
        }
    }

    companion object {
        /**
         * Sorts target languages by id
         * @param languages
         * @param referenceId languages are sorted according to the reference id
         */
        private fun sortTargetLanguages(
            languages: ArrayList<TargetLanguage>,
            referenceId: CharSequence
        ) {
            languages.sortWith { lhs, rhs ->
                var lhId = lhs.slug
                var rhId = rhs.slug
                // give priority to matches with the reference
                if (lhId.lowercase(Locale.getDefault()).startsWith(
                        referenceId.toString().lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    lhId = "!!$lhId"
                }
                if (rhId.lowercase(Locale.getDefault()).startsWith(
                        referenceId.toString().lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    rhId = "!!$rhId"
                }
                if (lhs.name.lowercase(Locale.getDefault()).startsWith(
                        referenceId.toString().lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    lhId = "!$lhId"
                }
                if (rhs.name.lowercase(Locale.getDefault()).startsWith(
                        referenceId.toString().lowercase(
                            Locale.getDefault()
                        )
                    )
                ) {
                    rhId = "!$rhId"
                }
                lhId.compareTo(rhId, ignoreCase = true)
            }
        }
    }
}
