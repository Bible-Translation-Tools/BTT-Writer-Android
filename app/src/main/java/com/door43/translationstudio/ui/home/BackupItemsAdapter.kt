package com.door43.translationstudio.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.door43.translationstudio.databinding.FragmentRestoreFromBackupListItemBinding
import java.io.File
import java.text.DateFormat
import java.text.DateFormat.MEDIUM
import java.util.Date

/**
 * Displays a list of translation repositories in the cloud
 */
class BackupItemsAdapter : BaseAdapter() {
    private val items = arrayListOf<File>()

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): File {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        val binding: FragmentRestoreFromBackupListItemBinding

        if (convertView == null) {
            binding = FragmentRestoreFromBackupListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            holder = ViewHolder(binding)
        } else {
            holder = convertView.tag as ViewHolder
        }

        val item = getItem(position)
        holder.bind(item)

        return holder.binding.root
    }

    /**
     * Sets the data to display
     * @param files the list of backup files to be displayed
     */
    fun setBackupItems(files: List<File>) {
        items.clear()
        items.addAll(files)
        notifyDataSetChanged()
    }

    private inner class ViewHolder(val binding: FragmentRestoreFromBackupListItemBinding) {
        init {
            binding.root.tag = this
        }

        /**
         * Loads the item into the view
         * @param item the item to be displayed in this view
         */
        fun bind(item: File) {
            val date = Date(item.lastModified())
            val dateFormat = DateFormat.getDateTimeInstance(MEDIUM, MEDIUM).format(date)

            binding.fileName.text = item.name
            binding.fileDate.text = dateFormat
        }
    }
}
