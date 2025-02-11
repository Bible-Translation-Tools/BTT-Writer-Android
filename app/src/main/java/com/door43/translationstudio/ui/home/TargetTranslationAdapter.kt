package com.door43.translationstudio.ui.home

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.door43.translationstudio.core.BibleCodes
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentTargetTranslationListItemBinding


/**
 * Created by joel on 9/3/2015.
 */
class TargetTranslationAdapter(private val typography: Typography) : BaseAdapter() {

    private val translations = arrayListOf<TranslationItem>()
    private var infoClickListener: OnInfoClickListener? = null
    private var sortProjectColumn = SortProjectColumnType.BibleOrder
    private var sortByColumn = SortByColumnType.ProjectThenLanguage

    /**
     * Adds a listener to be called when the info button is called
     * @param listener the listener to be added
     */
    fun setOnInfoClickListener(listener: OnInfoClickListener?) {
        infoClickListener = listener
    }

    override fun getCount(): Int {
        return translations.size
    }

    fun sort(
        sortByColumn: SortByColumnType = this.sortByColumn,
        sortProjectColumn: SortProjectColumnType = this.sortProjectColumn
    ) {
        this.sortByColumn = sortByColumn
        this.sortProjectColumn = sortProjectColumn

        translations.sortWith { lhs: TranslationItem, rhs: TranslationItem ->
            var compare: Int
            when (sortByColumn) {
                SortByColumnType.ProjectThenLanguage -> {
                    compare = compareProject(lhs, rhs, sortProjectColumn)
                    if (compare == 0) {
                        compare = lhs.translation.targetLanguageName
                            .compareTo(rhs.translation.targetLanguageName, ignoreCase = true)
                    }
                    return@sortWith compare
                }

                SortByColumnType.LanguageThenProject -> {
                    compare = lhs.translation.targetLanguageName
                        .compareTo(rhs.translation.targetLanguageName, ignoreCase = true)
                    if (compare == 0) {
                        compare = compareProject(lhs, rhs, sortProjectColumn)
                    }
                    return@sortWith compare
                }

                SortByColumnType.ProgressThenProject -> {
                    compare = ((rhs.progress - lhs.progress) * 100).toInt()

                    if (compare == 0) {
                        compare = compareProject(lhs, rhs, sortProjectColumn)
                    }
                    return@sortWith compare
                }

                else -> {
                    compare = ((rhs.progress - lhs.progress) * 100).toInt()
                    if (compare == 0) {
                        compare = compareProject(lhs, rhs, sortProjectColumn)
                    }
                    return@sortWith compare
                }
            }
        }

        val hand = Handler(Looper.getMainLooper())
        hand.post { this.notifyDataSetChanged() }
    }

    /**
     * compare projects (for use in sorting)
     * @param lhs
     * @param rhs
     * @return
     */
    private fun compareProject(
        lhs: TranslationItem,
        rhs: TranslationItem,
        sortProjectColumn: SortProjectColumnType
    ): Int {
        if (sortProjectColumn == SortProjectColumnType.BibleOrder) {
            val lhsIndex = bookList.indexOf(lhs.translation.projectId)
            val rhsIndex = bookList.indexOf(rhs.translation.projectId)
            if ((lhsIndex == rhsIndex) && (lhsIndex < 0)) { // if not bible books, then compare by name
                return lhs.formattedProjectName.compareTo(
                    rhs.formattedProjectName,
                    ignoreCase = true
                )
            }
            return lhsIndex - rhsIndex
        }

        // compare project names
        return lhs.formattedProjectName.compareTo(rhs.formattedProjectName, ignoreCase = true)
    }

    override fun getItem(position: Int): TranslationItem {
        return translations[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val holder: ViewHolder

        if (convertView == null) {
            val binding = FragmentTargetTranslationListItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            holder = ViewHolder(binding)
        } else {
            holder = convertView.tag as ViewHolder
        }

        val targetTranslation = getItem(position)
        holder.currentTargetTranslation = targetTranslation
        holder.binding.translationProgress.visibility = View.INVISIBLE

        holder.setProgress(targetTranslation.progress)

        // render view
        holder.binding.projectTitle.text = targetTranslation.formattedProjectName
        holder.binding.targetLanguage.text = targetTranslation.translation.targetLanguageName

        // set typeface for language
        val targetLanguage = targetTranslation.translation.targetLanguage
        val typeface = typography.getBestFontForLanguage(
            TranslationType.SOURCE,
            targetLanguage.slug,
            targetLanguage.direction
        )
        holder.binding.targetLanguage.setTypeface(typeface, Typeface.NORMAL)

        // TODO: finish rendering project icon
        holder.binding.infoButton.setOnClickListener {
            infoClickListener?.onClick(getItem(position))
        }
        return holder.binding.root
    }

    fun setTranslations(targetTranslations: List<TranslationItem>) {
        translations.clear()
        translations.addAll(targetTranslations)
        sort()
    }

    interface OnInfoClickListener {
        fun onClick(item: TranslationItem)
    }

    class ViewHolder(var binding: FragmentTargetTranslationListItemBinding) {
        var currentTargetTranslation: TranslationItem? = null

        init {
            binding.translationProgress.max = 100
            binding.root.tag = this
        }

        fun setProgress(progress: Double) {
            binding.translationProgress.progress = (progress * 100).coerceIn(0.0, 100.0).toInt()
            binding.translationProgress.visibility = View.VISIBLE
        }
    }

    /**
     * enum that keeps track of current state of USFM import
     */
    enum class SortByColumnType(val value: Int) {
        ProjectThenLanguage(0),
        LanguageThenProject(1),
        ProgressThenProject(2);

        companion object {
            fun fromString(value: String, defaultValue: SortByColumnType): SortByColumnType {
                return try {
                    fromInt(value.toInt())
                } catch (e: Exception) {
                    e.printStackTrace()
                    defaultValue
                }
            }

            fun fromInt(i: Int): SortByColumnType {
                for (b in entries) {
                    if (b.value == i) {
                        return b
                    }
                }
                return ProjectThenLanguage
            }
        }
    }

    /**
     * enum that keeps track of current state of USFM import
     */
    enum class SortProjectColumnType(val value: Int) {
        BibleOrder(0),
        Alphabetical(1);

        companion object {
            fun fromString(
                value: String,
                defaultValue: SortProjectColumnType
            ): SortProjectColumnType {
                return try {
                    fromInt(value.toInt())
                } catch (e: Exception) {
                    e.printStackTrace()
                    defaultValue
                }
            }

            fun fromInt(i: Int): SortProjectColumnType {
                for (b in entries) {
                    if (b.value == i) {
                        return b
                    }
                }
                return BibleOrder
            }
        }
    }

    companion object {
        private val bookList = BibleCodes.getBibleBooks()
    }
}