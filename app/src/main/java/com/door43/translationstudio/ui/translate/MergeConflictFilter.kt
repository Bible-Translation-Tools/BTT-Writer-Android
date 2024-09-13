package com.door43.translationstudio.ui.translate

import android.widget.Filter

/**
 * Created by blm on 10/20/16.
 */
class MergeConflictFilter(private val items: List<ListItem>) : Filter() {
    private val filteredItems = arrayListOf<ListItem>()
    private var listener: OnMatchListener? = null

    /**
     * Attaches a listener to receive match events
     * @param listener
     */
    fun setListener(listener: OnMatchListener?) {
        this.listener = listener
    }

    override fun performFiltering(constraint: CharSequence): FilterResults {
        val results = FilterResults()
        val bEmpty = constraint.trim().isEmpty()
        if (bEmpty) {
            results.values = items
            results.count = items.size
        } else {
            for (item in items) {
                // match
                val match = item.hasMergeConflicts
                if (match) {
                    filteredItems.add(item)
                    listener?.onMatch(item)
                }
            }
            results.values = filteredItems
            results.count = filteredItems.size
        }
        return results
    }

    override fun publishResults(constraint: CharSequence, results: FilterResults) {
        listener?.onFinished(constraint, results.values as ArrayList<ListItem>)
    }

    interface OnMatchListener {
        /**
         * called when a match was found
         * @param item
         */
        fun onMatch(item: ListItem)

        /**
         * called when the filtering is finished
         * @param constraint
         * @param results
         */
        fun onFinished(constraint: CharSequence, results: ArrayList<ListItem>)
    }
}


