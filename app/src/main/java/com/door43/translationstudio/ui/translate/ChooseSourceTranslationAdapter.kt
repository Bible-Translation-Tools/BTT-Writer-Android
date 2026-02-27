package com.door43.translationstudio.ui.translate

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ImageSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.door43.data.AssetsProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListDownloadItemBinding
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListHeaderBinding
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListItemBinding
import com.door43.translationstudio.databinding.FragmentSelectSourceTranslationListUpdatableItemBinding
import com.door43.translationstudio.format
import com.door43.translationstudio.getBestFontForLanguage
import com.door43.widget.ViewUtil
import org.unfoldingword.door43client.models.Translation
import java.util.TreeSet

/**
 * Handles the list of source translations that can be chosen for viewing along side
 * a target translation.
 */
class ChooseSourceTranslationAdapter(
    private val context: Context,
    private val typography: Typography,
    private val assetsProvider: AssetsProvider
) : BaseAdapter() {

    companion object {
        val TAG: String = ChooseSourceTranslationAdapter::class.java.simpleName
        const val TYPE_ITEM_SELECTABLE = 0
        const val TYPE_SEPARATOR = 1
        const val TYPE_ITEM_NEED_DOWNLOAD = 2
        const val TYPE_ITEM_SELECTABLE_UPDATABLE = 3
        const val MAX_SOURCE_ITEMS = 3
    }

    private val data = mutableMapOf<String, RCItem>()
    private val selected = mutableListOf<String>()
    private val available = mutableListOf<String>()
    private val downloadable = mutableListOf<String>()
    private var sortedData = mutableListOf<RCItem>()
    private var sectionHeader = TreeSet<Int>()
    private var searchText: String? = null

    private var itemClickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun onCheckForItemUpdates(containerSlug: String, position: Int)
        fun onTriggerDownload(item: RCItem, position: Int, callback: Callbacks.OnDownloadCancel)
        fun onTriggerDeleteContainer(
            containerSlug: String,
            position: Int,
            callback: Callbacks.OnDeleteContainer
        )
    }

    interface Callbacks {
        fun interface OnDownloadCancel {
            fun onCancel(position: Int)
        }

        fun interface OnDeleteContainer {
            fun onDelete(position: Int)
        }
    }

    fun setItems(items: List<RCItem>) {
        data.clear()
        available.clear()
        selected.clear()
        downloadable.clear()
        sortedData.clear()

        for (item in items) {
            addItem(item)
        }
        sort()
    }

    override fun getCount(): Int {
        return sortedData.size
    }

    /**
     * Adds an item to the list
     * If the item id matches an existing item it will be skipped
     */
    private fun addItem(item: RCItem) {
        val slug = item.containerSlug ?: return
        if (!data.containsKey(slug)) {
            data[slug] = item
            if (item.selected && item.downloaded) {
                selected.add(slug)
            } else if (!item.downloaded) {
                downloadable.add(slug)
            } else {
                available.add(slug)
            }
        }
    }

    override fun getItem(position: Int): RCItem? {
        return if (position in 0 until sortedData.size) {
            sortedData[position]
        } else {
            null
        }
    }

    /**
     * toggle selection state for item
     */
    fun toggleSelection(position: Int) {
        val item = getItem(position) ?: return
        if (item.selected) {
            deselect(position)
        } else {
            select(position)
        }
        sort()
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun isSelectableItem(position: Int): Boolean {
        return getItemViewType(position) != TYPE_SEPARATOR
    }

    override fun getItemViewType(position: Int): Int {
        var type = if (sectionHeader.contains(position)) TYPE_SEPARATOR else TYPE_ITEM_SELECTABLE
        if (type == TYPE_ITEM_SELECTABLE) {
            val v = getItem(position)
            if (v != null) {
                if (!v.downloaded) { // check if we need to download
                    type = TYPE_ITEM_NEED_DOWNLOAD
                } else if (v.hasUpdates) {
                    type = TYPE_ITEM_SELECTABLE_UPDATABLE
                }
            }
        }
        return type
    }

    override fun getViewTypeCount(): Int {
        return 4
    }

    /**
     * applies search string and resorts list
     */
    fun applySearch(search: String?) {
        searchText = search
        sort()
    }

    /**
     * Resorts the data
     */
    private fun sort() {
        sortedData = ArrayList()
        sectionHeader = TreeSet()

        // build list
        val selectedHeader = RCItem(getSelectedText(), null, selected = false, downloaded = false)
        sortedData.add(selectedHeader)
        sectionHeader.add(sortedData.size - 1)

        var section = getViewItems(selected, null) // do not restrict selections by search string
        sortedData.addAll(section)

        val availableHeader = RCItem(context.resources.getString(R.string.available), null, selected = false, downloaded = false)
        sortedData.add(availableHeader)
        sectionHeader.add(sortedData.size - 1)

        section = getViewItems(available, searchText)
        sortedData.addAll(section)

        val downloadableHeader = RCItem(getDownloadableText(), null, selected = false, downloaded = false)
        sortedData.add(downloadableHeader)
        sectionHeader.add(sortedData.size - 1)

        section = getViewItems(downloadable, searchText)
        sortedData.addAll(section)

        notifyDataSetChanged()
    }

    /**
     * get ViewItems from data list and apply any search filters
     */
    private fun getViewItems(keys: List<String>, searchText: String?): List<RCItem> {
        var section = mutableListOf<RCItem>()
        for (id in keys) {
            data[id]?.let { section.add(it) }
        }

        // sort by language code
        section.sortWith(Comparator({ lhs, rhs ->
                    try {
                        lhs.sourceTranslation?.language?.slug?.compareTo(
                            rhs.sourceTranslation?.language?.slug ?: ""
                        ) ?: 0
                    } catch (e: Exception) {
                        e.printStackTrace()
                        0
                    }
                }))

        if (!searchText.isNullOrEmpty()) {
            val filtered = mutableListOf<RCItem>()

            // filter by language code
            for (item in section) {
                val code = item.sourceTranslation?.language?.slug ?: ""
                if (code.length >= searchText.length) {
                    if (code.substring(0, searchText.length).equals(searchText, ignoreCase = true)) {
                        filtered.add(item)
                    }
                }
            }

            // filter by language name
            for (item in section) {
                val name = item.sourceTranslation?.language?.name ?: ""
                if (name.length >= searchText.length) {
                    if (name.substring(0, searchText.length).equals(searchText, ignoreCase = true)) {
                        if (!filtered.contains(item)) { // prevent duplicates
                            filtered.add(item)
                        }
                    }
                }
            }

            // filter by resource name
            for (item in section) {
                val parts = item.sourceTranslation?.resource?.name?.split("-") ?: emptyList()
                for (part in parts) { // handle sections separately
                    val name = part.trim()
                    if (name.length >= searchText.length) {
                        if (name.substring(0, searchText.length).equals(searchText, ignoreCase = true)) {
                            if (!filtered.contains(item)) { // prevent duplicates
                                filtered.add(item)
                            }
                        }
                    }
                }
            }
            section = filtered
        }
        return section
    }

    /**
     * create text for selected separator
     */
    private fun getSelectedText(): CharSequence {
        val text = context.resources.getString(R.string.selected)
        val limit = context.resources.getString(R.string.maximum_limit, MAX_SOURCE_ITEMS)
        val refresh = createImageSpannable(R.drawable.ic_refresh_secondary_24dp)
        val warning = context.resources.getString(R.string.requires_internet)
        val wifi = createImageSpannable(R.drawable.ic_wifi_secondary_18dp)
        return TextUtils.concat(text, " ", limit, "    ", refresh, " ", warning, " ", wifi) // combine all on one line
    }

    /**
     * create text for selected separator
     */
    private fun getDownloadableText(): CharSequence {
        val text = context.resources.getString(R.string.available_online)
        val warning = context.resources.getString(R.string.requires_internet)
        val wifi = createImageSpannable(R.drawable.ic_wifi_secondary_18dp)
        return TextUtils.concat(text, "    ", warning, " ", wifi) // combine all on one line
    }

    /**
     * create an image spannable
     */
    private fun createImageSpannable(resource: Int): SpannableStringBuilder {
        val refresh = SpannableStringBuilder(" ")
        val refreshDrawable = ResourcesCompat.getDrawable(context.resources, resource, null)
        if (refreshDrawable != null) {
            refreshDrawable.setBounds(0, 0, refreshDrawable.minimumWidth, refreshDrawable.minimumHeight)
            refresh.setSpan(ImageSpan(refreshDrawable), 0, refresh.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return refresh
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val holder: ViewHolder
        val rowType = getItemViewType(position)
        val item = getItem(position) ?: return view ?: View(parent.context) // Fallback for safety
        val inflater = LayoutInflater.from(parent.context)

        if (view == null) {
            holder = ViewHolder()
            when (rowType) {
                TYPE_SEPARATOR -> {
                    val binding = FragmentSelectSourceTranslationListHeaderBinding.inflate(inflater, parent, false)
                    holder.titleView = binding.title
                    binding.title.transformationMethod = null
                    view = binding.root
                }
                TYPE_ITEM_SELECTABLE -> {
                    val binding = FragmentSelectSourceTranslationListItemBinding.inflate(inflater, parent, false)
                    holder.titleView = binding.title
                    holder.checkboxView = binding.checkBoxView
                    view = binding.root
                }
                TYPE_ITEM_SELECTABLE_UPDATABLE -> {
                    val binding = FragmentSelectSourceTranslationListUpdatableItemBinding.inflate(inflater, parent, false)
                    holder.titleView = binding.title
                    holder.checkboxView = binding.checkBoxView
                    holder.downloadView = binding.downloadResource
                    view = binding.root
                }
                TYPE_ITEM_NEED_DOWNLOAD -> {
                    val binding = FragmentSelectSourceTranslationListDownloadItemBinding.inflate(inflater, parent, false)
                    holder.titleView = binding.title
                    holder.downloadView = binding.downloadResource
                    view = binding.root
                }
                else -> throw IllegalArgumentException("Incorrect view type")
            }
            view.tag = holder
        } else {
            holder = view.tag as ViewHolder
        }

        // load update status
        holder.currentPosition = position

        holder.titleView?.text = item.title
        if (item.sourceTranslation != null) {
            setFontForLanguage(holder, item)
        }

        if (rowType == TYPE_ITEM_NEED_DOWNLOAD || rowType == TYPE_ITEM_SELECTABLE_UPDATABLE) {
            holder.downloadView?.let {
                if (rowType == TYPE_ITEM_NEED_DOWNLOAD) {
                    it.setBackgroundResource(R.drawable.ic_file_download_black_24dp)
                } else {
                    it.setBackgroundResource(R.drawable.ic_refresh_black_24dp)
                }
                ViewUtil.tintViewDrawable(it, ContextCompat.getColor(parent.context, R.color.accent))
            }
        }

        if (rowType == TYPE_ITEM_SELECTABLE || rowType == TYPE_ITEM_SELECTABLE_UPDATABLE) {
            holder.checkboxView?.let {
                if (item.selected) {
                    it.setBackgroundResource(R.drawable.ic_check_box_black_24dp)
                    ViewUtil.tintViewDrawable(it, ContextCompat.getColor(parent.context, R.color.accent))
                    // display checked
                } else {
                    it.setBackgroundResource(R.drawable.ic_check_box_outline_blank_black_24dp)
                    ViewUtil.tintViewDrawable(it, ContextCompat.getColor(parent.context, R.color.dark_primary_text))
                    // display unchecked
                }
            }
        }

        view.setOnClickListener {
            if (itemClickListener != null && isSelectableItem(position)) {
                if (item.hasUpdates || !item.downloaded) {
                    itemClickListener?.onTriggerDownload(item, position) { toggleSelection(it) }
                } else {
                    toggleSelection(position)
                    if (!item.checkedUpdates && item.downloaded) {
                        itemClickListener?.onCheckForItemUpdates(item.containerSlug ?: "", position)
                    }
                }
            }
        }

        view.setOnLongClickListener {
            if (itemClickListener != null && item.downloaded && isSelectableItem(position)) {
                itemClickListener?.onTriggerDeleteContainer(
                    item.containerSlug ?: "",
                    position
                ) { markItemDeleted(it) }
                true
            } else {
                false
            }
        }

        return view
    }

    /**
     * will substitute some fonts for specific languages that may not be supported on all devices.
     * Uses lookup by language code.
     */
    private fun setFontForLanguage(holder: ViewHolder, item: RCItem) {
        val code = item.sourceTranslation?.language?.slug ?: return
        val titleView = holder.titleView ?: return

        titleView.format(
            typography,
            assetsProvider,
            TranslationType.SOURCE,
            code,
            item.sourceTranslation.language.direction
        )

        val typeface = getBestFontForLanguage(
            typography,
            assetsProvider,
            code
        )
        if (typeface != Typeface.DEFAULT) {
            titleView.setTypeface(typeface, Typeface.NORMAL)
        }
    }

    private fun select(position: Int) {
        if (selected.size >= MAX_SOURCE_ITEMS) {
            return
        }

        val item = getItem(position) ?: return
        val slug = item.containerSlug ?: return

        item.selected = true
        selected.remove(slug)
        available.remove(slug)
        downloadable.remove(slug)
        selected.add(slug)
    }

    private fun deselect(position: Int) {
        val item = getItem(position) ?: return
        val slug = item.containerSlug ?: return

        item.selected = false
        selected.remove(slug)
        available.remove(slug)
        downloadable.remove(slug)
        available.add(slug)
    }

    /**
     * Marks an item as deleted
     */
    private fun markItemDeleted(position: Int) {
        val item = getItem(position)
        if (item != null) {
            item.hasUpdates = false
            item.downloaded = false
            item.selected = false
            item.containerSlug?.let { slug ->
                selected.remove(slug)
                available.remove(slug)
                if (!downloadable.contains(slug)) downloadable.add(slug)
            }
        }
        sort()
    }

    /**
     * marks an item as downloaded
     */
    fun markItemDownloaded(position: Int) {
        val item = getItem(position)
        if (item != null) {
            item.hasUpdates = false
            item.downloaded = true
            select(position) // auto select download item
        }
        sort()
    }

    fun setItemClickListener(listener: OnItemClickListener?) {
        itemClickListener = listener
    }

    fun setItemHasUpdates(position: Int, hasUpdates: Boolean) {
        val item = getItem(position) ?: return
        item.hasUpdates = hasUpdates
        item.checkedUpdates = true
        notifyDataSetChanged()
        Log.i(TAG, "Checking for updates on ${item.containerSlug} finished, needs updates: $hasUpdates")
    }

    class ViewHolder {
        var titleView: TextView? = null
        var checkboxView: ImageView? = null
        var downloadView: ImageView? = null
        var currentPosition: Int = 0
    }

    class RCItem(
        val title: CharSequence,
        val sourceTranslation: Translation?,
        var selected: Boolean,
        var downloaded: Boolean
    ) {
        val containerSlug: String? = sourceTranslation?.resourceContainerSlug
        var hasUpdates: Boolean = false
        var checkedUpdates: Boolean = false
    }
}