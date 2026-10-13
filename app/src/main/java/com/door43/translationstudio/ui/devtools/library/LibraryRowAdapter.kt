package com.door43.translationstudio.ui.devtools.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Filter
import com.door43.translationstudio.databinding.FragmentLanguageListItemBinding
import java.util.Locale

class LibraryRowAdapter : BaseAdapter() {
    private val rows = arrayListOf<LibraryRow>()
    private val filteredRows = arrayListOf<LibraryRow>()
    private val rowFilter = RowFilter()

    override fun getCount(): Int = filteredRows.size

    override fun getItem(position: Int): LibraryRow = filteredRows[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding = if (convertView == null) {
            FragmentLanguageListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                .also { it.root.tag = it }
        } else {
            convertView.tag as FragmentLanguageListItemBinding
        }

        val row = getItem(position)
        binding.languageName.text = row.title
        binding.languageCode.text = row.subtitle

        return binding.root
    }

    fun setRows(rows: List<LibraryRow>) {
        this.rows.clear()
        this.rows.addAll(rows)
        filteredRows.clear()
        filteredRows.addAll(rows)
        notifyDataSetChanged()
    }

    fun getFilter(): Filter = rowFilter

    private inner class RowFilter : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val query = constraint?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
            val matched = if (query.isEmpty()) {
                ArrayList(rows)
            } else {
                rows.filter {
                    it.id.lowercase(Locale.getDefault()).startsWith(query)
                            || it.title.lowercase(Locale.getDefault()).contains(query)
                }
            }
            results.values = matched
            results.count = matched.size
            return results
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            filteredRows.clear()
            filteredRows.addAll(results.values as List<LibraryRow>)
            notifyDataSetChanged()
        }
    }
}
