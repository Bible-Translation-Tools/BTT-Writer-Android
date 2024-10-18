package com.door43.translationstudio.ui.home

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentRestoreFromCloudListItemBinding

/**
 * Displays a list of translation repositories in the cloud
 */
class TranslationRepositoryAdapter(private val typography: Typography) : BaseAdapter() {
    private val items = arrayListOf<RepositoryItem>()

    override fun getCount(): Int {
        return items.size
    }

    /**
     * TRICKY: we don't actually use this
     * @param position the list position
     * @return the repository at this position
     */
    override fun getItem(position: Int): RepositoryItem {
        return items[position]
    }

    /**
     * determine if item at position is supported project type
     * @param position
     * @return
     */
    fun isSupported(position: Int): Boolean {
        val item = getItem(position)
        return item.isSupported
    }

    /**
     * get project name for item at position
     * @param position
     * @return
     */
    fun getProjectName(position: Int): String {
        val item = getItem(position)
        var projectName = item.projectName
        if (!projectName.equals(item.targetTranslationSlug, ignoreCase = true)) {
            projectName += " (" + item.targetTranslationSlug + ")" // if not same as project name, add project id
        }
        return projectName
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder
        val binding: FragmentRestoreFromCloudListItemBinding

        if (convertView == null) {
            binding = FragmentRestoreFromCloudListItemBinding.inflate(
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
     * @param repositories the list of repositories to be displayed
     */
    fun setRepositories(repositories: List<RepositoryItem>) {
        items.clear()
        items.addAll(repositories)
        notifyDataSetChanged()
    }

    private inner class ViewHolder(val binding: FragmentRestoreFromCloudListItemBinding) {
        init {
            binding.root.tag = this
        }

        /**
         * Loads the item into the view
         * @param item the item to be displayed in this view
         */
        fun bind(item: RepositoryItem) {
            val context = binding.root.context
            binding.targetLanguageName.text = item.languageName

            // set default typeface for language
            val typeface = typography.getBestFontForLanguage(
                TranslationType.SOURCE,
                item.languageCode,
                item.languageDirection
            )
            binding.targetLanguageName.setTypeface(typeface, Typeface.NORMAL)

            if (item.isPrivate) {
                binding.privacy.setImageResource(R.drawable.ic_lock_secondary_18dp)
            } else {
                binding.privacy.setImageResource(R.drawable.ic_lock_open_secondary_18dp)
            }

            var projectNameStr = item.projectName
            if (item.isSupported) {
                binding.projectName.setTypeface(null, Typeface.BOLD)
                binding.projectName.setTextColor(context.resources.getColor(R.color.dark_primary_text))
            } else {
                binding.projectName.setTypeface(null, Typeface.NORMAL)
                binding.projectName.setTextColor(context.resources.getColor(R.color.dark_secondary_text))
                projectNameStr += " - " + context.getString(item.notSupportedId)
            }
            binding.projectName.text = projectNameStr
            binding.repositoryUrl.text = item.url
        }
    }
}
