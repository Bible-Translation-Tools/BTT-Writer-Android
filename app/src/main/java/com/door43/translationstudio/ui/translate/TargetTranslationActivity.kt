package com.door43.translationstudio.ui.translate

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.compose.setContent
import androidx.fragment.app.Fragment
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.ActivityTargetTranslationDetailBinding
import com.door43.translationstudio.ui.AppTheme
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.dialogs.BackupDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.PrintDialog
import com.door43.translationstudio.ui.draft.DraftActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.translate.review.SearchSubject
import com.door43.translationstudio.ui.viewmodels.TargetAction
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.unfoldingword.tools.logger.Logger
import java.util.Timer
import java.util.TimerTask

class TargetTranslationActivity : BaseActivity(),
    FirstTabFragment.OnEventListener,
    AdapterView.OnItemSelectedListener {

    private val prefRepository: IPreferenceRepository by inject()
    private val viewModel: TargetTranslationViewModel by viewModel()

    private lateinit var binding: ActivityTargetTranslationDetailBinding

    private var fragment: Fragment? = null
    private var commitTimer = Timer()
    private var searchEnabled = false
    private var searchTextWatcher: TextWatcher? = null
    private var searchTimerTask: SearchTimerTask? = null
    private var searchTimer: Timer? = null
    private var searchString: String? = null

    private var haveMergeConflict = false
    private var mergeConflictFilterEnabled = false
    private var foundTextFormat: Int = 0
    private var searchAtEnd = false
    private var searchAtStart = false
    private var numberOfChunkMatches = 0
    private var searchResumed = false
    private var showConflictSummary = false

    companion object {
        private const val TAG = "TranslationActivity"
        private const val COMMIT_INTERVAL = 2 * 60 * 1000L // commit changes every 2 minutes
        const val SEARCH_START_DELAY = 1000L
        const val STATE_SEARCH_ENABLED = "state_search_enabled"
        const val STATE_SEARCH_TEXT = "state_search_text"
        const val STATE_HAVE_MERGE_CONFLICT = "state_have_merge_conflict"
        const val STATE_MERGE_CONFLICT_FILTER_ENABLED = "state_merge_conflict_filter_enabled"
        const val STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED = "state_merge_conflict_summary_displayed"
        const val STATE_FILTER_MERGE_CONFLICTS = "state_filter_merge_conflicts"
        const val SEARCH_SOURCE = "search_source"
        const val STATE_SEARCH_AT_END = "state_search_at_end"
        const val STATE_SEARCH_AT_START = "state_search_at_start"
        const val STATE_SEARCH_FOUND_CHUNKS = "state_search_found_chunks"
        const val RESULT_DO_UPDATE = 42
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTargetTranslationDetailBinding.inflate(layoutInflater)
//        setContentView(binding.root)

        // validate parameters
        val args = intent.extras
        requireNotNull(args)

        val targetTranslationId = args.getString(Translator.EXTRA_TARGET_TRANSLATION_ID, null)
        mergeConflictFilterEnabled = args.getBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, false)

        if (!viewModel.initialized) {
            viewModel.initialize(targetTranslationId)
        }

        if (!viewModel.initialized) {
            Logger.e(
                TAG,
                "A valid target translation id is required. Received $targetTranslationId but the translation could not be found"
            )
            finish()
            return
        }

        // open used source translations by default
        viewModel.onAction(TargetAction.OpenSourceTranslations)

        // manual location settings
        val modeIndex = args.getInt(Translator.EXTRA_VIEW_MODE, -1)
        if (modeIndex > 0 && modeIndex < TranslationViewMode.entries.size) {
            viewModel.onAction(TargetAction.SaveLastViewMode(TranslationViewMode.entries[modeIndex]))
        }

//        binding.searchPane.downSearch.setOnClickListener { moveSearch(true) }
//        binding.searchPane.upSearch.setOnClickListener { moveSearch(false) }

        foundTextFormat = R.string.found_in_chunks

        // inject fragments
//        if (findViewById<View>(R.id.fragment_container) != null) {
//            if (savedInstanceState != null) {
//                fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
//            } else {
//                fragment = when (viewModel.model.value.viewMode) {
//                    TranslationViewMode.READ -> ReadModeFragment()
//                    TranslationViewMode.CHUNK -> ChunkModeFragment()
//                    TranslationViewMode.REVIEW -> ReviewModeFragment()
//                }
//                fragment?.arguments = intent.extras
//                supportFragmentManager
//                    .beginTransaction()
//                    .add(R.id.fragment_container, fragment!!)
//                    .commit()
//                // TODO: animate
//                // TODO: update menu
//            }
//        }

//        setUpSeekBar()

//        binding.translatorSidebar.warnMergeConflict.setOnClickListener {
//            mergeConflictFilterEnabled = !mergeConflictFilterEnabled // toggle filter state
//            setMergeConflictFilter() // update displayed state
//            openTranslationMode(TranslationViewMode.REVIEW, null) // make sure we are in review mode
//        }

//        binding.translatorSidebar.actionReview.setOnClickListener {
//            removeSearchBar()
//            mergeConflictFilterEnabled = false
//            setMergeConflictFilter()
//            openTranslationMode(TranslationViewMode.REVIEW, null)
//        }

        if (savedInstanceState != null) {
            searchEnabled = savedInstanceState.getBoolean(STATE_SEARCH_ENABLED, false)
            searchResumed = searchEnabled
            searchAtEnd = savedInstanceState.getBoolean(STATE_SEARCH_AT_END, false)
            searchAtStart = savedInstanceState.getBoolean(STATE_SEARCH_AT_START, false)
            numberOfChunkMatches = savedInstanceState.getInt(STATE_SEARCH_FOUND_CHUNKS, 0)
            searchString = savedInstanceState.getString(STATE_SEARCH_TEXT, null)
            haveMergeConflict = savedInstanceState.getBoolean(STATE_HAVE_MERGE_CONFLICT, false)
            mergeConflictFilterEnabled = savedInstanceState.getBoolean(STATE_MERGE_CONFLICT_FILTER_ENABLED, false)
            showConflictSummary = savedInstanceState.getBoolean(STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED, false)
        } else {
            showConflictSummary = mergeConflictFilterEnabled
        }

//        setSearchBarVisibility(searchEnabled)
//        if (searchEnabled) {
//            setSearchSpinner(true, numberOfChunkMatches, searchAtEnd, searchAtStart) // restore initial state
//        }

        restartAutoCommitTimer()

        setContent {
            AppTheme(darkTheme = isDarkTheme) {
                TargetTranslationScreen(
                    onHomeClick = { finish() },
                    onNavigateToDraft = {
                        val intent = Intent(this, DraftActivity::class.java)
                        intent.putExtra(
                            DraftActivity.EXTRA_TARGET_TRANSLATION_ID,
                            viewModel.targetTranslation.id
                        )
                        startActivity(intent)
                    },
                    onProjectPreview = {
                        val publishIntent = Intent(this@TargetTranslationActivity, PublishActivity::class.java)
                        publishIntent.putExtra(PublishActivity.EXTRA_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
                        publishIntent.putExtra(PublishActivity.EXTRA_CALLING_ACTIVITY, PublishActivity.ACTIVITY_TRANSLATION)
                        startActivity(publishIntent)
                        // TRICKY: we may move back and forth between the publisher and translation activities
                        // so we finish to avoid filling the stack.
                        finish()
                    },
                    onUploadExport = {
                        val backupFt = supportFragmentManager.beginTransaction()
                        val backupPrev = supportFragmentManager.findFragmentByTag(BackupDialog.TAG)
                        if (backupPrev != null) {
                            backupFt.remove(backupPrev)
                        }
                        backupFt.addToBackStack(null)

                        val backupDialog = BackupDialog()
                        val args = Bundle()
                        args.putString(BackupDialog.ARG_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
                        backupDialog.arguments = args
                        backupDialog.show(backupFt, BackupDialog.TAG)
                    },
                    onPrint = {
                        val printFt = supportFragmentManager.beginTransaction()
                        val printPrev = supportFragmentManager.findFragmentByTag("printDialog")
                        if (printPrev != null) {
                            printFt.remove(printPrev)
                        }
                        printFt.addToBackStack(null)

                        val printDialog = PrintDialog()
                        val printArgs = Bundle()
                        printArgs.putString(PrintDialog.ARG_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
                        printDialog.arguments = printArgs
                        printDialog.show(printFt, "printDialog")
                    },
                    onFeedback = {
                        val ft = supportFragmentManager.beginTransaction()
                        val prev = supportFragmentManager.findFragmentByTag("bugDialog")
                        if (prev != null) {
                            ft.remove(prev)
                        }
                        ft.addToBackStack(null)

                        val dialog = FeedbackDialog()
                        dialog.show(ft, "bugDialog")
                    },
                    onSearch = {},
                    onChunksDone = {},
                    onSettings = {
                        startActivity(Intent(
                            this@TargetTranslationActivity,
                            SettingsActivity::class.java
                        ))
                    },
                    onRestartAutoCommitTimer = ::restartAutoCommitTimer
                )
            }
        }
    }

    /**
     * enable/disable merge conflict filter in adapter
     */
//    private fun setMergeConflictFilter() {
//        val hand = Handler(Looper.getMainLooper())
//        hand.post {
//            val currentFragment = fragment
//            if (currentFragment is ViewModeFragment) {
//                currentFragment.setShowMergeSummary(showConflictSummary)
//                currentFragment.setMergeConflictFilter(mergeConflictFilterEnabled, false)
//            }
//            onEnableMergeConflict(haveMergeConflict, mergeConflictFilterEnabled)
//        }
//    }

//    private fun setUpSeekBar() {
//        if (enableGrids) {
//            graduations = binding.translatorSidebar.actionSeekGraduations
//        }
//        val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//        seekBar.max = 100
//        seekBar.progress = computePositionFromProgress(0)
//        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
//            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
//                var correctedProgress = correctProgress(progress)
//                correctedProgress = limitRange(correctedProgress, 0, seekBar.max - 1)
//                val position = correctedProgress / seekbarMultiplier
//                var percentage = 0
//
//                if (seekbarMultiplier > 1) { // if we need some granularity, calculate fractional amount
//                    val fractional = correctedProgress - position * seekbarMultiplier
//                    if (fractional != 0) {
//                        percentage = 100 * fractional / seekbarMultiplier
//                    }
//                }
//
//                // TODO: 2/16/17 record position
//
//                // If this change was initiated by a click on a UI element (rather than as a result
//                // of updates within the program), then update the view accordingly.
//                val currentFragment = fragment
//                if (currentFragment is ViewModeFragment && fromUser) {
//                    currentFragment.onScrollProgressUpdate(position, percentage)
//                }
//
//                closeKeyboard()
//            }
//
//            override fun onStartTrackingTouch(seekBar: SeekBar) {
//                graduations?.animate()?.alpha(1f)
//            }
//
//            override fun onStopTrackingTouch(seekBar: SeekBar) {
//                graduations?.animate()?.alpha(0f)
//            }
//        })
//
//        if (seekBar is SeekBarHint) {
//            seekBar.setOnProgressChangeListener { _, progress -> getFormattedChapter(progress) }
//        }
//
//        if (seekBar is VerticalSeekBarHint) {
//            seekBar.setOnProgressChangeListener { _, progress -> getFormattedChapter(progress) }
//        }
//    }

    /**
     * get chapter string to display
     */
    private fun getFormattedChapter(progress: Int): String {
//        val position = computePositionFromProgress(progress)
        val chapter = getChapterSlug(0)
        return " $chapter "
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean(STATE_SEARCH_ENABLED, searchEnabled)
        val searchText = getFilterText()
        if (searchEnabled) {
            out.putString(STATE_SEARCH_TEXT, searchText)
            out.putBoolean(STATE_SEARCH_AT_END, searchAtEnd)
            out.putBoolean(STATE_SEARCH_AT_START, searchAtStart)
            out.putInt(STATE_SEARCH_FOUND_CHUNKS, numberOfChunkMatches)
        }
        out.putBoolean(STATE_HAVE_MERGE_CONFLICT, haveMergeConflict)
        out.putBoolean(STATE_MERGE_CONFLICT_FILTER_ENABLED, mergeConflictFilterEnabled)
        val currentFragment = fragment
        if (currentFragment is ViewModeFragment) {
            out.putBoolean(
                STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED,
                currentFragment.ismMergeConflictSummaryDisplayed()
            )
        }
        super.onSaveInstanceState(out)
    }

    /**
     * hide search bar and clear search text
     */
    private fun removeSearchBar() {
        setSearchBarVisibility(false)
        setFilterText(null)
        filter(null) // clear search filter
    }

    /**
     * change state of search bar
     * @param show - if true set visible
     */
    private fun setSearchBarVisibility(show: Boolean) {
        // toggle search bar
        val visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            App.closeKeyboard(this@TargetTranslationActivity)
            setSearchSpinner(doingSearch = false, numberOfChunkMatches = 0, atEnd = true, atStart = true)
        }

        binding.searchPane.root.visibility = visibility
        searchEnabled = show

        if (searchTextWatcher != null) {
            binding.searchPane.searchText.removeTextChangedListener(searchTextWatcher) // remove old listener
            searchTextWatcher = null
        }

        if (show) {
            searchTextWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable) {
                    searchTimer?.cancel()

                    searchTimer = Timer()
                    searchTimerTask = SearchTimerTask(this@TargetTranslationActivity, s)
                    searchTimer?.schedule(searchTimerTask, SEARCH_START_DELAY)
                }
            }

            binding.searchPane.searchText.addTextChangedListener(searchTextWatcher)
            if (searchResumed) {
                // we don't have a way to reliably determine the state of the soft keyboard
                //   so we don't initially show the keyboard on resume.  This should be less
                //   annoying than always popping up the keyboard on resume
                searchResumed = false
            } else {
                setFocusOnTextSearchEdit()
            }
        } else {
            filter(null) // clear search filter
        }

        if (searchString != null) { // restore after rotate
            binding.searchPane.searchText.setText(searchString)
            if (show) {
                filter(searchString)
            }
            searchString = null
        }

        binding.searchPane.closeSearch.setOnClickListener { removeSearchBar() }

        val types = ArrayList<String>()
        types.add(resources.getString(R.string.search_source))
        types.add(resources.getString(R.string.search_translation))
        val typesAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, types)
        typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.searchPane.searchType.adapter = typesAdapter

        // restore last search type
        val lastSearchSourceStr = prefRepository.getDefaultPref(SEARCH_SOURCE, SearchSubject.SOURCE.name.uppercase(), String::class.javaObjectType)
        var lastSearchSource = SearchSubject.SOURCE
        try {
            lastSearchSource = SearchSubject.valueOf(lastSearchSourceStr.uppercase())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        binding.searchPane.searchType.setSelection(lastSearchSource.ordinal)
        binding.searchPane.searchType.onItemSelectedListener = this
    }

    /**
     * this seems crazy that we have to do a delay within a delay, but it is the only thing that works
     * to bring up keyboard.  Guessing that it is because there is so much redrawing that is
     * happening on bringing up the search bar.
     */
    @Deprecated("Find a better way to request focus and show keyboard")
    private fun setFocusOnTextSearchEdit() {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            binding.searchPane.searchText.isFocusableInTouchMode = true
            binding.searchPane.searchText.requestFocus()

            val hand1 = Handler(Looper.getMainLooper())
            hand1.post {
                App.showKeyboard(
                    this@TargetTranslationActivity,
                    binding.searchPane.searchText
                )
            }
        }
    }

    /**
     * notify listener of search state changes
     */
    private fun setSearchSpinner(doingSearch: Boolean, numberOfChunkMatches: Int, atEnd: Boolean, atStart: Boolean) {
        searchAtEnd = atEnd
        searchAtStart = atStart
        this.numberOfChunkMatches = numberOfChunkMatches
        binding.searchPane.searchProgress.visibility = if (doingSearch) View.VISIBLE else View.GONE

        val showSearchNavigation = !doingSearch && numberOfChunkMatches > 0
        val searchVisibility = if (showSearchNavigation) View.VISIBLE else View.INVISIBLE
        binding.searchPane.downSearch.visibility = if (atEnd) View.INVISIBLE else searchVisibility
        binding.searchPane.upSearch.visibility = if (atStart) View.INVISIBLE else searchVisibility

        val msg = resources.getString(foundTextFormat, numberOfChunkMatches)
        binding.searchPane.found.visibility = if (!doingSearch) View.VISIBLE else View.INVISIBLE
        binding.searchPane.found.text = msg
    }

    /**
     * called if search type is changed
     */
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
        filter(getFilterText())  // do search with search string in edit control
    }

    /**
     * called if no search type is selected
     */
    override fun onNothingSelected(parent: AdapterView<*>?) {
        // do nothing
    }

    /**
     * get the type of search
     */
    private fun getFilterSubject(): SearchSubject {
        val pos = binding.searchPane.searchType.selectedItemPosition
        if (pos == 0) {
            return SearchSubject.SOURCE
        }
        return SearchSubject.TARGET
    }

    /**
     * get search text in search bar
     */
    private fun getFilterText(): String {
        return binding.searchPane.searchText.text.toString()
    }

    /**
     * set search text in search bar
     */
    private fun setFilterText(text: String?) {
        binding.searchPane.searchText.setText(text)
    }

    /**
     * Filters the list, currently it just marks chunks with text
     */
    fun filter(constraint: String?) {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            val currentFragment = fragment
            if (currentFragment is ViewModeFragment) {
                // preserve current search type
                val subject = getFilterSubject()
                viewModel.saveSearchSource(subject.name.uppercase())
                currentFragment.filter(constraint ?: "", subject)
            }
        }
    }

    /**
     * move to next/previous search item
     */
    private fun moveSearch(next: Boolean) {
        App.closeKeyboard(this)
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            val currentFragment = fragment
            if (currentFragment is ViewModeFragment) {
                currentFragment.onMoveSearch(next)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notifyDatasetChanged()
        //setMergeConflictFilter(mMergeConflictFilterEnabled, mMergeConflictFilterEnabled); // restore last state
    }

    override fun onPause() {
        super.onPause()

        val currentFragment = fragment
        if (currentFragment is ViewModeFragment) {
            showConflictSummary = currentFragment.ismMergeConflictSummaryDisplayed() // update current state
        }
    }

    fun closeKeyboard() {
        val currentFragment = fragment
        if (currentFragment is ViewModeFragment) {
            val enteringSearchText = searchEnabled && binding.searchPane.searchText.hasFocus()
            if (!enteringSearchText) { // we don't want to close keyboard if we are entering search text
                currentFragment.closeKeyboard()
            }
        }
    }

//    private fun checkIfCursorStillOnScreen() {
//        val cursorPos = getCursorPositionOnScreen()
//        if (cursorPos != null) {
//            val scrollView = findViewById<View>(R.id.fragment_container)
//            if (scrollView != null) {
//                var visible = true
//
//                val scrollBounds = Rect()
//                scrollView.getHitRect(scrollBounds)
//
//                if (cursorPos.top < scrollBounds.top) {
//                    visible = false
//                } else if (cursorPos.bottom > scrollBounds.bottom) {
//                    visible = false
//                }
//
//                if (!visible) {
//                    closeKeyboard()
//                }
//            }
//        }
//    }

//    private fun getCursorPositionOnScreen(): Rect? {
//        val focusedView = currentFocus
//        if (focusedView != null) {
//            // get view position on screen
//            val l = IntArray(2)
//            focusedView.getLocationOnScreen(l)
//            val focusedViewX = l[0]
//            val focusedViewY = l[1]
//
//            if (focusedView is EditText) {
//                // getting relative cursor position
//                val pos = focusedView.selectionStart
//                val layout: Layout? = focusedView.layout
//                if (layout != null) {
//                    val line = layout.getLineForOffset(pos)
//                    val baseline = layout.getLineBaseline(line)
//                    val ascent = layout.getLineAscent(line)
//
//                    // convert relative positions to absolute position
//                    val x = focusedViewX + layout.getPrimaryHorizontal(pos).toInt()
//                    val bottomY = focusedViewY + baseline
//                    val y = bottomY + ascent
//
//                    return Rect(x, y, x, bottomY) // ignore width of cursor for now
//                }
//            }
//        }
//
//        return null
//    }

//    override fun onScrollProgress(progress: Int) {
        // TODO: 2/16/17 record scroll position
//        val computedProgress = computeProgressFromPosition(progress)
//        val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//        seekBar.progress = computedProgress
//        checkIfCursorStillOnScreen()
//    }

//    override fun onDataSetChanged(count: Int) {
//        val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//        val initialMax = seekBar.max
//        val initialProgress = seekBar.progress
//
//        val newCount = setSeekbarMax(count)
//        val newMax = seekBar.max
//        if (initialMax != newMax && initialMax > 0) { // if seekbar maximum has changed
//            // adjust proportionally
//            val newProgress = newMax * initialProgress / initialMax
//            seekBar.progress = newProgress
//        }
//        setupGraduations()
//        closeKeyboard()
//    }

    /**
     * get number of items in adapter
     */
    private fun getItemCount(): Int {
        val currentFragment = fragment
        if (currentFragment is ViewModeFragment) {
            return currentFragment.getItemCount()
        }
        return 0
    }

    /**
     * sets seekbar maximum based on item count, and add granularity if item count is small
     */
//    private fun setSeekbarMax(itemCount: Int): Int {
//        val minimumSteps = 300
//        var count = itemCount
//
//        Log.i(TAG, "setSeekbarMax: itemCount=$count")
//
//        if (count < 1) { // sanity check
//            count = 1
//        }
//
//        seekbarMultiplier = if (count < minimumSteps) {  // increase step size if number of cards is small, this gives more granularity in positioning
//            (minimumSteps / count) + 1
//        } else {
//            1
//        }
//
//        val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//        val newMax = count * seekbarMultiplier
//        val oldMax = seekBar.max
//        if (newMax != oldMax) {
//            Log.i(TAG, "setSeekbarMax: oldMax=$oldMax, newMax=$newMax, mSeekbarMultiplier=$seekbarMultiplier")
//            seekBar.max = newMax
//        } else {
//            Log.i(TAG, "setSeekbarMax: max unchanged=$oldMax")
//        }
//        return count
//    }

    /**
     * initialize text on graduations if enabled
     */
//    private fun setupGraduations() {
//        if (enableGrids && graduations != null) {
//            val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//            val numCards = seekBar.max / seekbarMultiplier
//
//            val maxChapterStr = getChapterSlug(numCards - 1)
//            val maxChapter = maxChapterStr.toIntOrNull() ?: 0
//
//            // Set up visibility of the graduation bar.
//            // Display graduations evenly spaced by number of chapters (but not more than the number
//            // of chapters that exist). As a special case, display nothing if there's only one chapter.
//            // Also, show nothing unless we're in read mode, since the other modes are indexed by
//            // frame, not by chapter, so displaying either frame numbers or chapter numbers would be
//            // nonsensical.
//            var numVisibleGraduations = Math.min(numCards, graduations!!.childCount)
//
//            if (maxChapter in 1 until numVisibleGraduations) {
//                numVisibleGraduations = maxChapter
//            }
//
//            if (numVisibleGraduations < 2) {
//                numVisibleGraduations = 0
//            }
//
//            // Set up the visible chapters.
//            for (i in 0 until numVisibleGraduations) {
//                val container = graduations!!.getChildAt(i) as ViewGroup
//                container.visibility = View.VISIBLE
//                val text = container.getChildAt(1) as TextView
//
//                val position = i * (numCards - 1) / (numVisibleGraduations - 1)
//                val chapter = getChapterSlug(position)
//                text.text = chapter
//            }
//
//            // Undisplay the invisible chapters.
//            for (i in numVisibleGraduations until graduations!!.childCount) {
//                graduations!!.getChildAt(i).visibility = View.GONE
//            }
//        }
//    }

    /**
     * get the chapter slug for the position
     */
    private fun getChapterSlug(position: Int): String {
        val currentFragment = fragment
        if (currentFragment is ViewModeFragment) {
            return currentFragment.getChapterSlug(position)
        }
        return (position + 1).toString()
    }

    /**
     * user has selected to update sources
     */
    override fun onUpdateSources() {
        setResult(RESULT_DO_UPDATE)
        finish()
    }

//    private fun displaySeekBarAsInverted(): Boolean {
//        return binding.translatorSidebar.actionSeek is VerticalSeekBar
//    }
//
//    private fun computeProgressFromPosition(position: Int): Int {
//        val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//        val correctedProgress = correctProgress(position * seekbarMultiplier)
//        return limitRange(correctedProgress, 0, seekBar.max)
//    }
//
//    private fun computePositionFromProgress(progress: Int): Int {
//        val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//        var correctedProgress = correctProgress(progress)
//        correctedProgress = limitRange(correctedProgress, 0, seekBar.max - 1)
//        return correctedProgress / seekbarMultiplier
//    }
//
//    /**
//     * if seekbar is inverted, this will correct the progress
//     */
//    private fun correctProgress(progress: Int): Int {
//        val seekBar = binding.translatorSidebar.actionSeek as SeekBar
//        return if (displaySeekBarAsInverted()) seekBar.max - progress else progress
//    }

//    override fun onNoSourceTranslations() {
//        if (fragment !is FirstTabFragment) {
//            val newFragment = FirstTabFragment()
//            newFragment.arguments = intent.extras
//            fragment = newFragment
//            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, newFragment).commit()
//        }
//    }

//    override fun openTranslationMode(mode: TranslationViewMode, extras: Bundle?) {
//        val fragmentExtras = Bundle()
//        intent.extras?.let { fragmentExtras.putAll(it) }
//        if (extras != null) {
//            fragmentExtras.putAll(extras)
//        }
//
//        // close the keyboard when switching between modes
//        val focusedView = currentFocus
//        if (focusedView != null) {
//            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
//            imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
//        } else {
//            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
//        }
//
//        viewModel.setLastViewMode(mode)
//
//        when (mode) {
//            TranslationViewMode.READ -> {
//                if (fragment !is ReadModeFragment) {
//                    val newFragment = ReadModeFragment()
//                    newFragment.arguments = fragmentExtras
//                    fragment = newFragment
//                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container, newFragment).commit()
//                    // TODO: animate
//                    // TODO: update menu
//                }
//            }
//            TranslationViewMode.CHUNK -> {
//                if (fragment !is ChunkModeFragment) {
//                    val newFragment = ChunkModeFragment()
//                    newFragment.arguments = fragmentExtras
//                    fragment = newFragment
//                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container, newFragment).commit()
//                    // TODO: animate
//                    // TODO: update menu
//                }
//            }
//            TranslationViewMode.REVIEW -> {
//                if (fragment !is ReviewModeFragment) {
//                    fragmentExtras.putBoolean(STATE_FILTER_MERGE_CONFLICTS, mergeConflictFilterEnabled)
//                    val newFragment = ReviewModeFragment()
//                    newFragment.arguments = fragmentExtras
//                    fragment = newFragment
//                    supportFragmentManager.beginTransaction().replace(R.id.fragment_container, newFragment).commit()
//                    // TODO: animate
//                    // TODO: update menu
//                }
//            }
//        }
//    }

    /**
     * Restart scheduled translation commits
     */
    fun restartAutoCommitTimer() {
        commitTimer.cancel()
        commitTimer = Timer()
        commitTimer.schedule(object : TimerTask() {
            override fun run() {
                try {
                    viewModel.targetTranslation.commit()
                } catch (e: Exception) {
                    Logger.e(TargetTranslationActivity::class.java.name, "Failed to commit the latest translation of ${viewModel.targetTranslation.id}", e)
                }
            }
        }, COMMIT_INTERVAL, COMMIT_INTERVAL)
    }

    /**
     * callback on search state changes
     */
    fun onSearching(doingSearch: Boolean, numberOfChunkMatches: Int, atEnd: Boolean, atStart: Boolean) {
        setSearchSpinner(doingSearch, numberOfChunkMatches, atEnd, atStart)
    }

    override fun onHasSourceTranslations() {
        val newFragment = when (viewModel.state.value.viewMode) {
            TranslationViewMode.READ -> ReadModeFragment()
            TranslationViewMode.CHUNK -> ChunkModeFragment()
            TranslationViewMode.REVIEW -> ReviewModeFragment()
        }
        newFragment.arguments = intent.extras
        fragment = newFragment
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, newFragment).commit()
        // TODO: animate
        // TODO: update menu
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val currentFragment = fragment
        return if (currentFragment is ViewModeFragment) {
            if (!currentFragment.onTouchEvent(event)) {
                super.dispatchTouchEvent(event)
            } else {
                true
            }
        } else {
            super.dispatchTouchEvent(event)
        }
    }

    override fun onDestroy() {
        commitTimer.cancel()
        try {
            viewModel.targetTranslation.commit()
        } catch (e: Exception) {
            Logger.e(this.javaClass.name, "Failed to commit changes before closing translation", e)
        }
        App.closeKeyboard(this)
        super.onDestroy()
    }

    /**
     * Causes the activity to tell the fragment it needs to reload
     */
    fun notifyDatasetChanged() {
        val currentFragment = fragment
        if (currentFragment is ViewModeFragment && currentFragment.getAdapter() != null) {
            currentFragment.getAdapter()?.triggerNotifyDataSetChanged()
        }
    }

    private inner class SearchTimerTask(
        private val activity: TargetTranslationActivity,
        private val searchString: Editable
    ) : TimerTask() {
        override fun run() {
            activity.filter(searchString.toString())
        }
    }
}