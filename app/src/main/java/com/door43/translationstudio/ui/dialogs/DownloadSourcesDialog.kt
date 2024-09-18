package com.door43.translationstudio.ui.dialogs

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.door43.translationstudio.App.Companion.isNetworkAvailable
import com.door43.translationstudio.App.Companion.showKeyboard
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.DialogDownloadSourcesBinding
import com.door43.translationstudio.ui.dialogs.DownloadSourcesAdapter.FilterStep
import com.door43.translationstudio.ui.dialogs.DownloadSourcesAdapter.SelectedState
import com.door43.translationstudio.ui.dialogs.DownloadSourcesAdapter.SelectionType
import com.door43.translationstudio.ui.viewmodels.DownloadSourcesViewModel
import com.door43.usecases.DownloadResourceContainers
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.unfoldingword.tools.logger.Logger
import java.util.Objects
import kotlin.math.min

/**
 * Created by blm on 12/1/16.
 */
@AndroidEntryPoint
class DownloadSourcesDialog : DialogFragment() {
    private lateinit var progressDialog: ProgressHelper.ProgressDialog
    private lateinit var adapter: DownloadSourcesAdapter

    private var steps = arrayListOf<FilterStep>()
    private var searchString: String? = null
    private var selected = arrayListOf<String>()
    private var searchTextWatcher: TextWatcher? = null

    private var _binding: DialogDownloadSourcesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadSourcesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogDownloadSourcesBinding.inflate(inflater, container, false)

        setupObservers()

        viewModel.getAvailableSources(resources.getString(R.string.loading_sources))

        progressDialog = ProgressHelper.newInstance(
            requireContext(),
            R.string.loading_sources,
            false
        )

        adapter = DownloadSourcesAdapter()

        with(binding) {
            searchBackButton.setOnClickListener {
                if (steps.size > 1) {
                    removeLastStep()
                    setFilter(!RESTORE)
                } else {
                    dismiss()
                }
            }
            selectAll.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapter.forceSelection(true, false)
                    onSelectionChanged()
                }
            }
            unselectAll.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapter.forceSelection(false, true)
                    onSelectionChanged()
                }
            }
            downloadButton.setOnClickListener {
                val selected = adapter.selected
                if (selected != null && selected.isNotEmpty()) {
                    if (isNetworkAvailable) {
                        viewModel.downloadSources(selected)
                    } else {
                        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.internet_not_available)
                            .setMessage(R.string.check_network_connection)
                            .setPositiveButton(R.string.dismiss, null)
                            .show()
                    }
                }
            }
            list.adapter = adapter
            list.onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    if (steps.isNotEmpty()) {
                        searchString = null
                        val currentStep = steps[steps.size - 1]
                        val item = adapter.getItem(position)
                        currentStep.old_label = currentStep.label
                        currentStep.label = item.title.toString()
                        currentStep.filter = item.filter
                        currentStep.language = item.sourceTranslation?.language

                        if (steps.size < 2) { // if we haven't set up last step
                            when (currentStep.selection) {
                                SelectionType.language -> addStep(
                                    SelectionType.book_type,
                                    R.string.choose_category
                                )

                                SelectionType.oldTestament, SelectionType.newTestament, SelectionType.translationAcademy, SelectionType.other_book -> addStep(
                                    SelectionType.language, R.string.choose_language
                                )

                                SelectionType.book_type -> {
                                    val category = adapter.getCategoryForFilter(currentStep.filter)
                                    when (category) {
                                        SelectionType.oldTestament -> addStep(
                                            SelectionType.oldTestament,
                                            R.string.choose_book
                                        )

                                        SelectionType.newTestament -> addStep(
                                            SelectionType.newTestament,
                                            R.string.choose_book
                                        )

                                        SelectionType.translationAcademy -> addStep(
                                            SelectionType.translationAcademy,
                                            R.string.choose_book
                                        )

                                        SelectionType.other_book -> addStep(
                                            SelectionType.other_book,
                                            R.string.choose_book
                                        )

                                        else -> addStep(SelectionType.other_book, R.string.choose_book)
                                    }
                                }

                                else -> addStep(SelectionType.book_type, R.string.choose_category)
                            }
                        } else if (steps.size < 3) { // set up last step
                            val firstStep = steps[0]
                            if (Objects.requireNonNull(firstStep.selection) == SelectionType.language) {
                                addStep(
                                    SelectionType.source_filtered_by_language,
                                    R.string.choose_sources
                                )
                            } else {
                                addStep(SelectionType.source_filtered_by_book, R.string.choose_sources)
                            }
                        } else { // at last step, do toggling
                            adapter.toggleSelection(position)
                            val selectedState = adapter.selectedState
                            when (selectedState) {
                                SelectedState.all -> selectAll.isChecked = true
                                SelectedState.none -> unselectAll.isChecked = true
                                else -> onSelectionChanged()
                            }
                            return@OnItemClickListener
                        }
                        setFilter(!RESTORE)
                    }
                }
        }

        /*selectionBar = view.findViewById<View>(R.id.selection_bar) as LinearLayout
        searchIcon = view.findViewById<View>(R.id.search_mag_icon) as ImageView
        searchEditText = view.findViewById<View>(R.id.search_text) as EditText
        searchTextBorder = view.findViewById<View>(R.id.search_text_border) as LinearLayout*/

        searchString = null

        /*byLanguageButton = view.findViewById<View>(R.id.byLanguage) as RadioButton
        byBookButton = view.findViewById<View>(R.id.byBook) as RadioButton*/

        if (savedInstanceState != null) {
            val stepsArrayJson = savedInstanceState.getString(STATE_FILTER_STEPS, null)
            try {
                val stepsArray = JSONArray(stepsArrayJson)
                for (i in 0 until stepsArray.length()) {
                    val jsonObject = stepsArray[i] as JSONObject
                    val step = FilterStep.generate(jsonObject)
                    steps.add(step)
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            searchString = savedInstanceState.getString(STATE_SEARCH_STRING, null)
            val byLanguage = savedInstanceState.getBoolean(STATE_BY_LANGUAGE_FLAG, true)
            if (byLanguage) {
                binding.byLanguage.isChecked = true
            } else {
                binding.byBook.isChecked = true
            }

            savedInstanceState.getStringArrayList(STATE_SELECTED_LIST)?.let {
                selected.clear()
                selected.addAll(it)
            }

            adapter.selected = selected

            savedInstanceState.getStringArrayList(STATE_DOWNLOADED_LIST)?.let {
                adapter.downloaded = it
            }

            val errorMessagesJson = savedInstanceState.getString(STATE_DOWNLOADED_ERROR_MESSAGES, null)
            adapter.setDownloadErrorMessages(errorMessagesJson)
        }

        binding.byLanguage.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                searchString = null
                steps = ArrayList() // clear existing filter and start over
                addStep(SelectionType.language, R.string.choose_language)
                setFilter(!RESTORE)
            }
        }
        binding.byBook.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                steps = ArrayList() // clear existing filter and start over
                addStep(SelectionType.book_type, R.string.choose_category)
                setFilter(!RESTORE)
            }
        }

        if (savedInstanceState == null) {
            binding.byLanguage.isChecked = true // setup initial state
        }
        return binding.root
    }

    private fun setupObservers() {
        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog.show()
                progressDialog.setProgress(it.progress)
                progressDialog.setMessage(it.message)
                progressDialog.setMax(it.max)
            } else {
                progressDialog.dismiss()
            }
        }
        viewModel.availableSources.observe(this) {
            it?.let { result ->
                adapter.setData(result)
                adapter.selected = selected
                setFilter(RESTORE)

                searchString?.let { search ->
                    enableSearchText()
                    binding.searchText.setText(search)
                    val endPos = search.length
                    binding.searchText.setSelection(endPos, endPos)
                    adapter.setSearch(search)
                }
            }
        }
        viewModel.downloadedSources.observe(this) {
            it?.let { result ->
                getDownloadedSources(result)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // widen dialog to accommodate more text
        val desiredWidth = 750
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val density = displayMetrics.density
        val correctedWidth = width / density
        var screenWidthFactor = desiredWidth / correctedWidth
        screenWidthFactor = min(screenWidthFactor.toDouble(), 1.0).toFloat() // sanity check

        dialog?.window?.setLayout(
            (width * screenWidthFactor).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    override fun onSaveInstanceState(out: Bundle) {
        val stepsArray = JSONArray()
        for (step in steps) {
            val jsonObject = step.toJson()
            stepsArray.put(jsonObject)
        }
        out.putString(STATE_FILTER_STEPS, stepsArray.toString())
        out.putString(STATE_SEARCH_STRING, searchString)
        out.putBoolean(STATE_BY_LANGUAGE_FLAG, binding.byLanguage.isChecked)
        out.putStringArrayList(STATE_SELECTED_LIST, adapter.selected as ArrayList<String>)
        out.putStringArrayList(STATE_DOWNLOADED_LIST, adapter.downloaded as ArrayList<String>)
        out.putString(
            STATE_DOWNLOADED_ERROR_MESSAGES,
            adapter.downloadErrorMessages.toString()
        )
        super.onSaveInstanceState(out)
    }

    /**
     * update controls for selection state
     */
    private fun onSelectionChanged() {
        val selectedState = adapter.selectedState
        val allSelected = (selectedState == SelectedState.all)
        binding.selectAll.isEnabled = !allSelected
        if (!allSelected) {
            binding.selectAll.isChecked = false
        }
        val noneSelected = (selectedState == SelectedState.none)
        binding.unselectAll.isEnabled = !noneSelected
        if (!noneSelected) {
            binding.unselectAll.isChecked = false
        }

        val downloadSelect = !noneSelected
        binding.downloadButton.isEnabled = downloadSelect
        val backgroundColor = if (downloadSelect) R.color.accent else R.color.light_gray
        binding.downloadButton.setBackgroundColor(resources.getColor(backgroundColor))
    }

    /**
     * remove the last step from stack
     */
    private fun removeLastStep() {
        var lastStep = steps[steps.size - 1]
        steps.remove(lastStep)
        lastStep = steps[steps.size - 1]
        lastStep.filter = null
        lastStep.label = lastStep.old_label
    }

    /**
     * display the nav label and show choices to user
     *
     */
    private fun setFilter(restore: Boolean) {
        for (step in 0..2) {
            setNavBarStep(step)
        }

        // setup selection bar
        val selectDownloads = (steps.size == 3)
        binding.selectionBar.visibility = if (selectDownloads) View.VISIBLE else View.GONE
        adapter.setFilterSteps(steps, searchString, restore)
        if (selectDownloads) {
            onSelectionChanged()
        }

        //set up nav/search bar
        val enableLanguageSearch = ((steps.size == 1)
                && (steps[0].selection == SelectionType.language))
        if (enableLanguageSearch) {
            setupLanguageSearch()
        } else {
            binding.searchMagIcon.visibility = View.GONE
            showNavbar(true)
        }

        binding.searchTextBorder.visibility = View.GONE
        binding.searchText.visibility = View.GONE
        binding.searchText.isEnabled = false
        if (searchTextWatcher != null) {
            binding.searchText.removeTextChangedListener(searchTextWatcher)
            searchTextWatcher = null
        }

        getTextView(1)?.setOnClickListener(null)
    }

    /**
     * setup UI for doing language search
     */
    private fun setupLanguageSearch() {
        binding.searchMagIcon.visibility = View.VISIBLE
        binding.searchMagIcon.setOnClickListener { enableSearchText() }
    }

    /**
     * enable search text box
     */
    private fun enableSearchText() {
        showNavbar(false)
        binding.searchTextBorder.visibility = View.VISIBLE
        binding.searchText.visibility = View.VISIBLE
        binding.searchText.isEnabled = true
        binding.searchText.requestFocus()
        showKeyboard(activity, binding.searchText, false)
        binding.searchText.setText("")

        if (searchTextWatcher != null) {
            binding.searchText.removeTextChangedListener(searchTextWatcher)
        }

        searchTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }
            override fun afterTextChanged(s: Editable) {
                searchString = s.toString()
                adapter.setSearch(searchString)
            }
        }
        binding.searchText.addTextChangedListener(searchTextWatcher)
    }

    /**
     * set text at nav bar position
     * @param stepIndex
     */
    private fun setNavBarStep(stepIndex: Int) {
        val navText = getTextForStep(stepIndex)
        val typeface = getFontForStep(stepIndex)

        val viewPosition = stepIndex * 2 + 1

        setNavPosition(navText, viewPosition, typeface)

        var sep: CharSequence? = null
        if (navText != null) { // if we have something at this position, then add separator
            sep = ">"
        }
        setNavPosition(sep, viewPosition - 1, Typeface.DEFAULT)
    }

    /**
     * lookup the font to use for string
     * @param stepIndex
     * @return
     */
    private fun getFontForStep(stepIndex: Int): Typeface {
        var typeface = Typeface.DEFAULT
        val enable = (stepIndex < steps.size) && (stepIndex >= 0)
        if (enable) {
            val step = steps[stepIndex]
            if (step.language != null) {
                typeface = Typography.getBestFontForLanguage(
                    activity, TranslationType.SOURCE, step.language.slug, step.language.direction
                )
            }
        }
        return typeface
    }

    /**
     * get text for position
     * @param stepIndex
     * @return
     */
    private fun getTextForStep(stepIndex: Int): CharSequence? {
        var navText: CharSequence? = null
        val enable = stepIndex < steps.size
        if (enable) {
            val step = steps[stepIndex]

            val span = SpannableStringBuilder(step.label)
            val lastItem = (stepIndex >= (steps.size - 1))
            if (!lastItem) {
                // insert a clickable span

                span.setSpan(
                    SpannableStringBuilder(step.label),
                    0,
                    span.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val clickSpan: ClickableSpan = object : ClickableSpan() {
                    override fun onClick(widget: View) {
                        Logger.i(TAG, "clicked on item: $stepIndex")
                        while (steps.size > stepIndex + 1) {
                            removeLastStep()
                        }
                        setFilter(!RESTORE)
                    }
                }
                span.setSpan(clickSpan, 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            navText = span
        }
        return navText
    }

    /**
     * set text at position, or hide view if text is null
     * @param text
     * @param position
     * @param typeface font to use
     */
    private fun setNavPosition(text: CharSequence?, position: Int, typeface: Typeface) {
        val view = getTextView(position)
        if (view != null) {
            if (text != null) {
                view.text = text
                view.setTypeface(typeface, Typeface.NORMAL)
                view.visibility = View.VISIBLE
                view.movementMethod =
                    LinkMovementMethod.getInstance() // enable clicking on TextView
            } else {
                view.text = ""
                view.visibility = View.GONE
            }
        }
    }

    /**
     * show/hide navbar items
     * @param enable
     */
    private fun showNavbar(enable: Boolean) {
        val visibility = if (enable) View.VISIBLE else View.GONE
        for (i in 1..5) {
            getTextView(i)?.visibility = visibility
        }
    }

    /**
     * find text view for position
     * @param position
     * @return
     */
    private fun getTextView(position: Int): TextView? {
        val resID = when (position) {
            1 -> R.id.nav_text1
            2 -> R.id.nav_text2
            3 -> R.id.nav_text3
            4 -> R.id.nav_text4
            5 -> R.id.nav_text5
            else -> return null
        }
        val searchView = binding.root.findViewById<View>(resID) as TextView
        return searchView
    }

    /**
     * add step to sequence
     * @param selection
     * @param prompt
     */
    private fun addStep(selection: SelectionType, prompt: Int) {
        val promptStr = resources.getString(prompt)
        val step = FilterStep(selection, promptStr)
        steps.add(step)
    }

    private fun getDownloadedSources(result: DownloadResourceContainers.Result) {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            val downloadedTranslations = result.downloadedTranslations
            for (slug in downloadedTranslations) {
                Logger.i(TAG, "Received: $slug")

                val pos = adapter.findPosition(slug)
                if (pos >= 0) {
                    adapter.markItemDownloaded(pos)
                }
            }

            val failedSourceDownloads = result.failedSourceDownloads
            for (translationID in failedSourceDownloads) {
                Logger.e(TAG, "Download failed: $translationID")
                val pos = adapter.findPosition(translationID)
                if (pos >= 0) {
                    adapter.markItemError(
                        pos,
                        result.failureMessages[translationID]
                    )
                }
            }

            val downloads = resources.getString(
                R.string.downloads_success,
                downloadedTranslations.size
            )
            var errors = ""
            if ((failedSourceDownloads.isNotEmpty())) {
                val error = requireActivity().resources.getString(
                    R.string.downloads_fail,
                    failedSourceDownloads.size
                )
                errors = "\n$error"
            }

            val failedHelpsDownloads = result.failedHelpsDownloads
            if (failedHelpsDownloads.isNotEmpty()) {
                val error = requireActivity().resources.getString(
                    R.string.helps_download_errors,
                    failedHelpsDownloads.toString()
                )
                errors += "\n$error"
            }

            adapter.notifyDataSetChanged()
            onSelectionChanged()

            val title = if (errors.isNotEmpty()) R.string.download_errors else R.string.download_complete
            AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                .setTitle(title)
                .setMessage(downloads + errors)
                .setPositiveButton(R.string.label_close, null)
                .show()
        }
    }

    override fun onDestroy() {
        progressDialog.dismiss()
        super.onDestroy()
    }

    companion object {
        val TAG: String = DownloadSourcesDialog::class.java.simpleName

        const val STATE_SEARCH_STRING: String = "state_search_string"
        const val STATE_FILTER_STEPS: String = "state_filter_steps"
        const val STATE_BY_LANGUAGE_FLAG: String = "state_by_language_flag"
        const val STATE_SELECTED_LIST: String = "state_selected_list"
        const val STATE_DOWNLOADED_LIST: String = "state_downloaded_list"
        const val STATE_DOWNLOADED_ERROR_MESSAGES: String = "state_downloaded_error_messages"
        const val RESTORE: Boolean = true
    }
}
