package com.door43.translationstudio.ui.dialogs

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.viewbinding.ViewBinding
import com.door43.data.AssetsProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.Util
import com.door43.translationstudio.databinding.FragmentSelectDownloadSourceItemBinding
import com.door43.translationstudio.databinding.FragmentSelectFilterItemBinding
import com.door43.translationstudio.getBestFontForLanguage
import com.door43.usecases.GetAvailableSources
import com.door43.widget.ViewUtil
import org.json.JSONObject
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.tools.logger.Logger
import java.util.Collections

/**
 * Created by blm on 12/1/16.
 */
class DownloadSourcesAdapter(
    private val typography: Typography,
    private val assetsProvider: AssetsProvider
) : BaseAdapter() {

    companion object {
        val TAG: String = DownloadSourcesAdapter::class.java.simpleName
        const val TYPE_ITEM_FILTER_SELECTION = 0
        const val TYPE_ITEM_SOURCE_SELECTION = 1

        // 02/20/2017 - for now we are disabling updating of TA since a major change coming up could break the app
        private val bookTypeNameList = intArrayOf(
            R.string.old_testament_label,
            R.string.new_testament_label,
            R.string.other_label
        ) // removed R.string.ta_label to disable updating TA

        private val bookTypeIconList = intArrayOf(
            R.drawable.ic_library_books_black_24dp,
            R.drawable.ic_library_books_black_24dp,
            R.drawable.ic_local_library_black_24dp
        )
    }

    private var context: Context? = null
    var selected = mutableListOf<String>()
    var downloaded = mutableListOf<String>()
    var items = mutableListOf<ViewItem>()
        private set

    private var availableSources: List<Translation>? = null
    private var byLanguage: Map<String, List<Int>>? = null
    private var otBooks: Map<String, List<Int>>? = null
    private var ntBooks: Map<String, List<Int>>? = null
    private var otherBooks: Map<String, List<Int>>? = null

    private var selectionType = SelectionType.language
    private var steps: List<FilterStep>? = null
    private var languageFilter: String? = null
    private var bookFilter: String? = null
    private var search: String? = null
    private val downloadErrors = mutableMapOf<String, String?>()

    override fun getCount(): Int {
        return items.size
    }

    /**
     * Loads source lists from task results
     */
    fun setData(result: GetAvailableSources.Result) {
        availableSources = result.sources
        Logger.i(TAG, "Found ${availableSources?.size} sources")

        byLanguage = result.byLanguage
        otBooks = result.otBooks
        ntBooks = result.ntBooks
        otherBooks = result.otherBooks
        selected = mutableListOf() // clear selections
        initializeSelections()
    }

    /**
     * loads the filter stages (e.g. filter by language, and then by category)
     * @param restore - if true then don't reset selection list
     */
    fun setFilterSteps(steps: List<FilterStep>?, search: String?, restore: Boolean) {
        this.steps = steps
        this.search = search
        if (!restore) {
            selected = mutableListOf() // clear selections
        }
        initializeSelections()
    }

    /**
     * loads the filter stages (e.g. filter by language, and then by category)
     */
    fun setSearch(search: String?) {
        this.search = search
        initializeSelections()
    }

    override fun getItem(position: Int): ViewItem {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return when (selectionType) {
            SelectionType.source_filtered_by_language, SelectionType.source_filtered_by_book -> TYPE_ITEM_SOURCE_SELECTION
            else -> TYPE_ITEM_FILTER_SELECTION
        }
    }

    override fun getViewTypeCount(): Int {
        return 2
    }

    fun getDownloadErrorMessages(): JSONObject {
        return JSONObject(downloadErrors)
    }

    fun setDownloadErrorMessages(jsonDownloadErrorMessagesStr: String) {
        downloadErrors.clear()
        try {
            val jsonMessages = JSONObject(jsonDownloadErrorMessagesStr)
            val keySet: MutableIterator<*> = jsonMessages.keys()
            while (keySet.hasNext()) {
                val key = keySet.next() as String
                val value = jsonMessages.get(key)
                downloadErrors[key] = value.toString()
            }
        } catch (e: java.lang.Exception) {
            Logger.w("DownloadSourcesAdapter", "Error parsing download error messages", e)
        }
    }

    val selectedState: SelectedState
        get() {
            var allSelected = true
            var noneSelected = true
            for (item in items) {
                if (!item.downloaded) { // ignore items already downloaded
                    if (item.selected) {
                        noneSelected = false
                    } else {
                        allSelected = false
                    }
                }
            }

            return when {
                noneSelected -> SelectedState.none
                allSelected -> SelectedState.all
                else -> SelectedState.not_empty
            }
        }

    /**
     * Resorts the data
     */
    fun initializeSelections() {
        bookFilter = null // clear filters
        languageFilter = null

        if (steps.isNullOrEmpty() || availableSources.isNullOrEmpty()) {
            return
        }

        val safeSteps = steps!!
        selectionType = safeSteps[safeSteps.size - 1].selection

        for (i in 0 until safeSteps.size - 1) { // iterate through previous steps to extract filters
            val step = safeSteps[i]
            when (step.selection) {
                SelectionType.language -> languageFilter = step.filter
                SelectionType.oldTestament, SelectionType.newTestament, SelectionType.other_book, SelectionType.book_type -> bookFilter = step.filter
                else -> {}
            }
        }

        when (selectionType) {
            SelectionType.source_filtered_by_language -> getSourcesForLanguageAndCategory()
            SelectionType.source_filtered_by_book -> getSourcesForBook()
            SelectionType.oldTestament -> getBooksInCategory(otBooks, false)
            SelectionType.newTestament -> getBooksInCategory(ntBooks, false)
            SelectionType.other_book -> getBooksInCategory(otherBooks, true)
            SelectionType.book_type -> getCategories()
            SelectionType.language -> getLanguages()
        }
        notifyDataSetChanged()
    }

    /**
     * create list of languages available in sources
     */
    private fun getLanguages() {
        items = mutableListOf()
        byLanguage?.let { langMap ->
            for (key in langMap.keys) {
                val indices = langMap[key]
                if (!indices.isNullOrEmpty()) {
                    val index = indices[0]
                    if (index >= 0 && index < availableSources!!.size) {
                        val sourceTranslation = availableSources!![index]
                        val title = "${sourceTranslation.language.name}  (${sourceTranslation.language.slug})"
                        val newItem = ViewItem(title, sourceTranslation.language.slug, sourceTranslation, false, false)
                        items.add(newItem)
                    }
                }
            }
        }

        if (!search.isNullOrEmpty()) {
            val filteredItems = mutableListOf<ViewItem>()
            val searchStr = search!!

            // filter by language code
            for (item in items) {
                val code = item.sourceTranslation?.language?.slug ?: ""
                if (code.length >= searchStr.length) {
                    if (code.substring(0, searchStr.length).equals(searchStr, ignoreCase = true)) {
                        filteredItems.add(item)
                    }
                }
            }

            // filter by language name
            for (item in items) {
                val name = item.sourceTranslation?.language?.name ?: ""
                if (name.length >= searchStr.length) {
                    if (name.substring(0, searchStr.length).equals(searchStr, ignoreCase = true)) {
                        if (!filteredItems.contains(item)) { // prevent duplicates
                            filteredItems.add(item)
                        }
                    }
                }
            }

            items = filteredItems
        }
    }

    /**
     * get list of categories (OT, NT, other).  If language has been selected, only
     * return categories that contain the language.
     */
    private fun getCategories() {
        items = mutableListOf()
        for (i in bookTypeNameList.indices) {
            val id = bookTypeNameList[i]
            if (languageFilter != null) {
                val found = when (id) {
                    R.string.old_testament_label -> isLanguageInCategory(byLanguage, otBooks)
                    R.string.new_testament_label -> isLanguageInCategory(byLanguage, ntBooks)
                    else -> isLanguageInCategory(byLanguage, otherBooks)
                }
                if (!found) { // if category is not found, skip
                    continue
                }
            }
            val title = context?.resources?.getString(id) ?: ""
            val newItem = ViewItem(title, id.toString(), null, false, false)
            newItem.icon = bookTypeIconList[i]
            items.add(newItem)
        }
    }

    /**
     * check if category (OT, NT, other) contains the selected language
     */
    private fun isLanguageInCategory(sortSet: Map<String, List<Int>>?, category: Map<String, List<Int>>?): Boolean {
        var found = false
        if (sortSet != null && category != null && sortSet.containsKey(languageFilter)) {
            val indices = sortSet[languageFilter] ?: emptyList()
            for (index in indices) {
                if (index >= 0 && index < availableSources!!.size) {
                    val sourceTranslation = availableSources!![index]
                    if (category.containsKey(sourceTranslation.project.slug)) {
                        found = true
                        break
                    }
                }
            }
        }
        return found
    }

    /**
     * create list of source selections that match book
     */
    private fun getSourcesForBook() {
        var sourceList: List<Int>? = null
        items = mutableListOf()

        // first get book list for selected book type
        if (ntBooks?.containsKey(bookFilter) == true) {
            sourceList = ntBooks!![bookFilter]
        }
        if (sourceList == null && otBooks?.containsKey(bookFilter) == true) {
            sourceList = otBooks!![bookFilter]
        }
        if (sourceList == null && otherBooks?.containsKey(bookFilter) == true) {
            sourceList = otherBooks!![bookFilter]
        }

        if (sourceList == null) {
            return
        }

        for (index in sourceList) {
            if (index >= 0 && index < availableSources!!.size) {
                val source = availableSources!![index]
                val filter = source.resourceContainerSlug
                val language = "${source.language.name}  (${source.language.slug})"
                val project = "${source.resource.name}  (${source.resource.slug})"
                addNewViewItem(language, project, filter, source)
            }
        }

        // sort by language code (do numeric sort)
        Collections.sort(items, Comparator { lhs, rhs -> lhs.filter.compareTo(rhs.filter) })
    }

    /**
     * create new view item, apply previous state info, and add to list
     */
    private fun addNewViewItem(title1: String, title2: String, filter: String, source: Translation) {
        val newItem = ViewItem(title1, title2, filter, source, false, false)

        if (selected.contains(newItem.containerSlug)) {
            newItem.selected = true
        }
        if (downloaded.contains(newItem.containerSlug)) {
            newItem.downloaded = true
        }
        if (downloadErrors.containsKey(newItem.containerSlug)) {
            newItem.error = true
            newItem.errorMessage = downloadErrors[newItem.containerSlug]
        }
        items.add(newItem)
    }

    /**
     * gets the selection type based on filter
     */
    fun getCategoryForFilter(categoryFilter: String?): SelectionType {
        val bookTypeSelected = Util.strToInt(categoryFilter, R.string.other_label)
        return when (bookTypeSelected) {
            R.string.old_testament_label -> SelectionType.oldTestament
            R.string.new_testament_label -> SelectionType.newTestament
            else -> SelectionType.other_book
        }
    }

    /**
     * create list of source selections that match language and category
     */
    private fun getSourcesForLanguageAndCategory() {
        items = mutableListOf()

        //get book list for category
        val category = getCategoryForFilter(bookFilter)
        val sortSet = when (category) {
            SelectionType.oldTestament -> otBooks
            SelectionType.newTestament -> ntBooks
            else -> otherBooks
        }

        byLanguage?.let { langMap ->
            for (key in langMap.keys) {
                if (languageFilter != null && key != languageFilter) {
                    continue // skip over language if not matching filter
                }

                val indices = langMap[key]
                if (!indices.isNullOrEmpty()) {
                    for (index in indices) {
                        if (index >= 0 && index < availableSources!!.size) {
                            val source = availableSources!![index]

                            if (sortSet != null && !sortSet.containsKey(source.project.slug)) {
                                continue // if not in right category then skip
                            }

                            val filter = source.resourceContainerSlug
                            val project = "${source.project.name}  (${source.project.slug})"
                            val resource = "${source.resource.name}  (${source.resource.slug})"
                            addNewViewItem(project, resource, filter, source)
                        }
                    }
                }
                if (languageFilter != null) { // if filtering by specific language, then done
                    break
                }
            }
        }

        if (sortSet != null) {
            val unOrdered = items
            items = mutableListOf()

            for (book in sortSet.keys) {
                var i = 0
                while (i < unOrdered.size) {
                    val viewItem = unOrdered[i]
                    val sourceTranslation = viewItem.sourceTranslation
                    if (book == sourceTranslation?.project?.slug) {
                        items.add(viewItem)
                        unOrdered.removeAt(i)
                        i--
                    }
                    i++
                }
            }
        }
    }

    /**
     * create ordered list based on category, optionally sort
     */
    private fun getBooksInCategory(bookType: Map<String, List<Int>>?, sort: Boolean) {
        items = mutableListOf()
        if (bookType != null) {
            for (key in bookType.keys) {
                val indices = bookType[key]
                if (!indices.isNullOrEmpty()) {
                    var index: Int
                    var sourceTranslation: Translation? = null
                    var title: String? = null
                    var filter: String? = null
                    for (i in indices.indices) {
                        index = indices[i]
                        sourceTranslation = availableSources!![index]
                        filter = sourceTranslation.project.slug
                        title = "${sourceTranslation.project.name}  ($filter)"
                        if (sourceTranslation.language.slug == "en") {
                            break
                        }
                    }

                    val newItem = ViewItem(title ?: "", filter ?: "", sourceTranslation,
                        selected = false,
                        downloaded = false
                    )
                    items.add(newItem)
                }
            }
        }

        if (sort) {
            items.sortWith(Comparator { lhs, rhs ->
                lhs.title.toString().compareTo(rhs.title.toString())
            })
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        context = parent.context
        val inflater = LayoutInflater.from(parent.context)

        val rowType = getItemViewType(position)
        val item = getItem(position)

        val viewHolder: BaseViewHolder
        var view = convertView

        if (view == null) {
            if (rowType == TYPE_ITEM_FILTER_SELECTION) {
                val binding = FragmentSelectFilterItemBinding.inflate(inflater, parent, false)
                viewHolder = FilterViewHolder(binding)
            } else {
                val binding = FragmentSelectDownloadSourceItemBinding.inflate(inflater, parent, false)
                viewHolder = DownloadSourceViewHolder(binding)
            }
            view = viewHolder.binding.root
            view.tag = viewHolder
        } else {
            viewHolder = view.tag as BaseViewHolder
        }

        viewHolder.bind(item)

        return view
    }

    /**
     * toggle selection state for item
     */
    fun toggleSelection(position: Int) {
        if (getItem(position).selected) {
            deselect(position)
        } else {
            select(position)
        }
        notifyDataSetChanged()
    }

    fun select(position: Int) {
        val item = getItem(position)
        if (!item.downloaded) {
            item.selected = true
            item.containerSlug?.let {
                if (!selected.contains(it)) { // make sure we don't add entry twice
                    selected.add(it)
                }
            }
        }
    }

    fun deselect(position: Int) {
        val item = getItem(position)
        item.selected = false
        item.containerSlug?.let {
            selected.remove(it)
        }
    }

    /**
     * search items for position that matches slug
     */
    fun findPosition(slug: String): Int {
        for (i in items.indices) {
            if (items[i].containerSlug == slug) {
                return i
            }
        }
        return -1
    }

    /**
     * marks an item as downloaded
     */
    fun markItemDownloaded(position: Int) {
        val item = getItem(position)
        item.downloaded = true
        item.error = false
        deselect(position)

        item.containerSlug?.let {
            if (!downloaded.contains(it)) {
                downloaded.add(it)
            }
            downloadErrors.remove(it)
        }
    }

    /**
     * marks an item as error
     */
    fun markItemError(position: Int, message: String?) {
        val item = getItem(position)
        item.error = true
        item.errorMessage = message

        item.containerSlug?.let {
            downloadErrors[it] = message
            downloaded.remove(it)
        }
    }

    /**
     * used to force selection of all or none of the items
     */
    fun forceSelection(selectAll: Boolean, selectNone: Boolean) {
        if (selectAll) {
            for (i in items.indices) {
                select(i)
            }
        }
        if (selectNone) {
            for (i in items.indices) {
                deselect(i)
            }
        }
        notifyDataSetChanged()
    }

    class ViewItem(
        val title: CharSequence,
        val title2: CharSequence?,
        val filter: String,
        val sourceTranslation: Translation?,
        var selected: Boolean,
        var downloaded: Boolean
    ) {
        val containerSlug: String? = sourceTranslation?.resourceContainerSlug
        var error: Boolean = false
        var icon: Int = 0
        var errorMessage: String? = null

        constructor(title: CharSequence, filter: String, sourceTranslation: Translation?, selected: Boolean, downloaded: Boolean) :
                this(title, null, filter, sourceTranslation, selected, downloaded)
    }

    class FilterStep(val selection: SelectionType, var label: String) {
        lateinit var oldLabel: String
        var filter: String? = null
        var language: Language? = null

        private constructor(selection: SelectionType, label: String, filter: String?, oldLabel: String) : this(selection, label) {
            this.filter = filter
            this.oldLabel = oldLabel
        }

        fun toJson(): JSONObject? {
            return try {
                val jsonObject = JSONObject()
                jsonObject.putOpt("selection", selection.value)
                jsonObject.putOpt("label", label)
                jsonObject.putOpt("old_label", oldLabel)
                jsonObject.putOpt("filter", filter)
                jsonObject
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        companion object {
            fun generate(jsonObject: JSONObject): FilterStep? {
                return try {
                    val selectionInt = getOpt(jsonObject, "selection") as Int
                    val selection = SelectionType.fromInt(selectionInt) ?: return null
                    val label = getOpt(jsonObject, "label") as String
                    val oldLabel = getOpt(jsonObject, "old_label") as String
                    val filter = getOpt(jsonObject, "filter") as? String
                    FilterStep(selection, label, filter, oldLabel)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            private fun getOpt(json: JSONObject, key: String): Any? {
                return try {
                    if (json.has(key)) {
                        json.get(key)
                    } else null
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    /**
     * enum that keeps track of current state of USFM import
     */
    enum class SelectionType(val value: Int) {
        language(0),
        oldTestament(1),
        newTestament(2),
        other_book(3),
        book_type(4),
        source_filtered_by_language(5),
        source_filtered_by_book(6);

        companion object {
            fun fromInt(i: Int): SelectionType? {
                return entries.find { it.value == i }
            }
        }
    }

    enum class SelectedState {
        all,
        none,
        not_empty
    }

    private abstract inner class BaseViewHolder(val binding: ViewBinding) {
        abstract fun bind(item: ViewItem)
    }

    private inner class FilterViewHolder(private val filterBinding: FragmentSelectFilterItemBinding) : BaseViewHolder(filterBinding) {
        override fun bind(item: ViewItem) {
            // make sure this is reset to default
            filterBinding.title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            filterBinding.title.text = item.title

            if (selectionType == SelectionType.book_type) {
                context?.let { ctx ->
                    filterBinding.itemIcon.setImageDrawable(AppCompatResources.getDrawable(ctx, item.icon))
                }
                filterBinding.itemIcon.visibility = View.VISIBLE
            } else {
                filterBinding.itemIcon.visibility = View.GONE
                // if language selection, look up font
                val typeface = getBestFontForLanguage(
                    typography,
                    assetsProvider,
                    item.sourceTranslation?.language?.slug
                )
                filterBinding.title.setTypeface(typeface, Typeface.NORMAL)
            }
        }
    }

    private inner class DownloadSourceViewHolder(private val downloadBinding: FragmentSelectDownloadSourceItemBinding) : BaseViewHolder(downloadBinding) {
        override fun bind(item: ViewItem) {
            val ctx = downloadBinding.root.context

            // make sure this is reset to default
            downloadBinding.title.setTypeface(Typeface.DEFAULT, Typeface.NORMAL)
            downloadBinding.title.text = item.title
            downloadBinding.title2.text = item.title2 ?: ""
            downloadBinding.errorIcon.visibility = if (item.error) View.VISIBLE else View.GONE

            if (item.error) {
                downloadBinding.errorIcon.setOnClickListener {
                    val builder = AlertDialog.Builder(ctx, R.style.AppTheme_Dialog)
                        .setTitle(R.string.download_failed)
                        .setMessage(R.string.check_network_connection)
                        .setPositiveButton(R.string.label_close, null)
                    if (item.errorMessage != null) {
                        builder.setMessage(item.errorMessage)
                    }
                    builder.show()
                }
            }
            if (item.downloaded) { // display with a green check
                downloadBinding.itemIcon.setBackgroundResource(R.drawable.ic_done_black_24dp)
                ViewUtil.tintViewDrawable(downloadBinding.itemIcon, ContextCompat.getColor(ctx, R.color.completed))
            } else if (item.selected) { // display checked box
                downloadBinding.itemIcon.setBackgroundResource(R.drawable.ic_check_box_black_24dp)
                ViewUtil.tintViewDrawable(downloadBinding.itemIcon, ContextCompat.getColor(ctx, R.color.accent))
            } else { // display unchecked box
                downloadBinding.itemIcon.setBackgroundResource(R.drawable.ic_check_box_outline_blank_black_24dp)
                ViewUtil.tintViewDrawable(downloadBinding.itemIcon, ContextCompat.getColor(ctx, R.color.dark_primary_text))
            }
        }
    }
}