package com.door43.translationstudio.ui.translate

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.App.Companion.closeKeyboard
import com.door43.translationstudio.App.Companion.showKeyboard
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.ActivityTargetTranslationDetailBinding
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.SettingsActivity
import com.door43.translationstudio.ui.dialogs.BackupDialog
import com.door43.translationstudio.ui.dialogs.FeedbackDialog
import com.door43.translationstudio.ui.dialogs.PrintDialog
import com.door43.translationstudio.ui.draft.DraftActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.translate.review.SearchSubject
import com.door43.widget.VerticalSeekBar
import com.door43.widget.VerticalSeekBarHint
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import it.moondroid.seekbarhint.library.SeekBarHint
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.tools.logger.Logger
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class TargetTranslationActivity : BaseActivity(), ViewModeFragment.OnEventListener,
    FirstTabFragment.OnEventListener, AdapterView.OnItemSelectedListener {

    @Inject lateinit var translator: Translator
    @Inject lateinit var library: Door43Client
    @Inject lateinit var prefRepository: IPreferenceRepository

    private lateinit var binding: ActivityTargetTranslationDetailBinding

    private var fragment: Fragment? = null

    private var targetTranslation: TargetTranslation? = null
    private var mCommitTimer = Timer()
    private var searchEnabled = false
    private var searchTextWatcher: TextWatcher? = null
    private var mSearchTimerTask: SearchTimerTask? = null
    private var mSearchTimer: Timer? = null
    private var searchString: String? = null

    private val enableGrids = false
    // allows for more granularity in setting position if cards are few
    private var seekbarMultiplier = 1
    // so we can update the seekbar maximum when item count has changed
    private var mOldItemCount = 1
    private var mHaveMergeConflict = false
    private var mMergeConflictFilterEnabled = false
    private var mFoundTextFormat = 0
    private var mSearchAtEnd = false
    private var mSearchAtStart = false
    private var mNumberOfChunkMatches = 0
    private var mSearchResumed = false
    private var mShowConflictSummary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTargetTranslationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // validate parameters
        val args = intent.extras
        val targetTranslationId = args!!.getString(Translator.EXTRA_TARGET_TRANSLATION_ID, null)
        mMergeConflictFilterEnabled = args.getBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, false)
        targetTranslation = translator.getTargetTranslation(targetTranslationId)
        if (targetTranslation == null) {
            Logger.e(
                TAG,
                "A valid target translation id is required. Received '$targetTranslationId' but the translation could not be found"
            )
            finish()
            return
        }

        if (savedInstanceState == null) {
            // reset cached values
            ViewModeFragment.reset()
        }

        // open used source translations by default
        if (translator.getOpenSourceTranslations(targetTranslation!!.id).isEmpty()) {
            val resourceContainerSlugs = targetTranslation!!.sourceTranslations
            for (slug in resourceContainerSlugs) {
                translator.addOpenSourceTranslation(targetTranslation!!.id, slug)
            }
        }

        // notify user that a draft translation exists the first time activity starts
        if (savedInstanceState == null && draftIsAvailable() && targetTranslation!!.numTranslated() == 0) {
            val snack = Snackbar.make(
                findViewById(android.R.id.content),
                R.string.draft_translation_exists,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.preview) {
                    val intent = Intent(this@TargetTranslationActivity, DraftActivity::class.java)
                    intent.putExtra(
                        DraftActivity.EXTRA_TARGET_TRANSLATION_ID,
                        targetTranslation!!.id
                    )
                    startActivity(intent)
                }
            ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
            snack.show()
        }

        // manual location settings
        val modeIndex = args.getInt(Translator.EXTRA_VIEW_MODE, -1)
        if (modeIndex > 0 && modeIndex < TranslationViewMode.entries.size) {
            translator.setLastViewMode(targetTranslationId, TranslationViewMode.entries[modeIndex])
        }

        mFoundTextFormat = R.string.found_in_chunks

        // inject fragments
        if (savedInstanceState != null) {
            fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        } else {
            val viewMode = translator.getLastViewMode(targetTranslation!!.id)
            fragment = when (viewMode) {
                TranslationViewMode.READ -> ReadModeFragment()
                TranslationViewMode.CHUNK -> ChunkModeFragment()
                TranslationViewMode.REVIEW -> ReviewModeFragment()
            }.apply {
                arguments = intent.extras
                supportFragmentManager
                    .beginTransaction().add(R.id.fragment_container, this)
                    .commit()
            }
            // TODO: animate
            // TODO: udpate menu
        }

        setUpSeekBar()
        buildMenu()

        with(binding) {
            searchPane.downSearch.setOnClickListener { moveSearch(true) }
            searchPane.upSearch.setOnClickListener { moveSearch(false) }

            translatorSidebar.warnMergeConflict.setOnClickListener {
                // toggle filter state
                mMergeConflictFilterEnabled = !mMergeConflictFilterEnabled
                // make sure we are in review mode
                openTranslationMode(TranslationViewMode.REVIEW, null)
                // update displayed state
                setMergeConflictFilter(mMergeConflictFilterEnabled, false)
            }

            translatorSidebar.actionRead.setOnClickListener {
                removeSearchBar()
                openTranslationMode(TranslationViewMode.READ, null)
            }

            translatorSidebar.actionChunk.setOnClickListener {
                removeSearchBar()
                openTranslationMode(TranslationViewMode.CHUNK, null)
            }

            translatorSidebar.actionReview.setOnClickListener {
                removeSearchBar()
                mMergeConflictFilterEnabled = false
                setMergeConflictFilter(mMergeConflictFilterEnabled, false)
                openTranslationMode(TranslationViewMode.REVIEW, null)
            }
        }

        if (savedInstanceState != null) {
            searchEnabled = savedInstanceState.getBoolean(STATE_SEARCH_ENABLED, false)
            mSearchResumed = searchEnabled
            mSearchAtEnd = savedInstanceState.getBoolean(STATE_SEARCH_AT_END, false)
            mSearchAtStart = savedInstanceState.getBoolean(STATE_SEARCH_AT_START, false)
            mNumberOfChunkMatches = savedInstanceState.getInt(STATE_SEARCH_FOUND_CHUNKS, 0)
            searchString = savedInstanceState.getString(STATE_SEARCH_TEXT, null)
            mHaveMergeConflict = savedInstanceState.getBoolean(STATE_HAVE_MERGE_CONFLICT, false)
            mMergeConflictFilterEnabled = savedInstanceState.getBoolean(
                STATE_MERGE_CONFLICT_FILTER_ENABLED, false
            )
            mShowConflictSummary = savedInstanceState.getBoolean(
                STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED, false
            )
        } else {
            mShowConflictSummary = mMergeConflictFilterEnabled
        }

        setupSidebarModeIcons()
        setSearchBarVisibility(searchEnabled)
        if (searchEnabled) {
            setSearchSpinner(
                true,
                mNumberOfChunkMatches,
                mSearchAtEnd,
                mSearchAtStart
            ) // restore initial state
        }
        restartAutoCommitTimer()
    }

    /**
     * enable/disable merge conflict filter in adapter
     * @param enableFilter
     * @param forceMergeConflict - if true, then will initialize have merge conflict flag to true
     */
    private fun setMergeConflictFilter(enableFilter: Boolean, forceMergeConflict: Boolean) {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            mMergeConflictFilterEnabled = enableFilter
            (fragment as? ViewModeFragment)?.apply {
                setShowMergeSummary(mShowConflictSummary)
                setMergeConflictFilter(enableFilter, forceMergeConflict)
            }
            onEnableMergeConflict(mHaveMergeConflict, mMergeConflictFilterEnabled)
        }
    }

    /**
     * called by adapter to set state for merge conflict icon
     */
    override fun onEnableMergeConflict(showConflicted: Boolean, active: Boolean) {
        mHaveMergeConflict = showConflicted
        mMergeConflictFilterEnabled = active
        with(binding.translatorSidebar) {
            warnMergeConflict.visibility =
                if (showConflicted) View.VISIBLE else View.GONE
            if (mMergeConflictFilterEnabled) {
                warnMergeConflict.setImageResource(R.drawable.ic_warning_white_24dp)
                val highlightedColor = resources.getColor(R.color.primary_dark)
                warnMergeConflict.setBackgroundColor(highlightedColor)
            } else {
                warnMergeConflict.setImageResource(R.drawable.ic_warning_inactive_24dp)
                warnMergeConflict.background = null // clear any previous background highlighting
            }
        }
    }

    private fun setUpSeekBar() {
        with(binding) {
            (translatorSidebar.actionSeek as? SeekBar)?.apply {
                max = 100
                progress = computePositionFromProgress(0)
                setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                        // progress = handleItemCountIfChanged(progress);
                        var correctedProgress = correctProgress(progress)
                        correctedProgress = limitRange(correctedProgress, 0, max - 1)
                        val position = correctedProgress / seekbarMultiplier
                        var percentage = 0

                        if (seekbarMultiplier > 1) { // if we need some granularity, calculate fractional amount
                            val fractional = correctedProgress - position * seekbarMultiplier
                            if (fractional != 0) {
                                percentage = 100 * fractional / seekbarMultiplier
                            }
                        }

                        // TODO: 2/16/17 record position

                        // If this change was initiated by a click on a UI element (rather than as a result
                        // of updates within the program), then update the view accordingly.
                        if (fragment is ViewModeFragment && fromUser) {
                            (fragment as ViewModeFragment).onScrollProgressUpdate(position, percentage)
                        }

                        val activity = seekBar.context as TargetTranslationActivity
                        activity.closeKeyboard()
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar) {
                        translatorSidebar.actionSeekGraduations.animate().alpha(1f)
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar) {
                        translatorSidebar.actionSeekGraduations.animate().alpha(0f)
                    }
                })
            }

            (translatorSidebar.actionSeek as? SeekBarHint)?.apply {
                setOnProgressChangeListener { _, progress ->
                    getFormattedChapter(
                        progress
                    )
                }
            }

            (translatorSidebar.actionSeek as? VerticalSeekBarHint)?.apply {
                setOnProgressChangeListener { _, progress ->
                    getFormattedChapter(
                        progress
                    )
                }
            }
        }
    }

    /**
     * clips value to within range min to max
     * @param value
     * @param min
     * @param max
     * @return
     */
    private fun limitRange(value: Int, min: Int, max: Int): Int {
        var newValue = value
        if (newValue < min) {
            newValue = min
        } else if (newValue > max) {
            newValue = max
        }
        return newValue
    }

    /**
     * get chapter string to display
     * @param progress
     * @return
     */
    private fun getFormattedChapter(progress: Int): String {
        val position = computePositionFromProgress(progress)
        val chapter = getChapterSlug(position)
        val displayedText = " $chapter "
        return displayedText
    }

    public override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean(STATE_SEARCH_ENABLED, searchEnabled)
        val searchText = filterText
        if (searchEnabled) {
            out.putString(STATE_SEARCH_TEXT, searchText)
            out.putBoolean(STATE_SEARCH_AT_END, mSearchAtEnd)
            out.putBoolean(STATE_SEARCH_AT_START, mSearchAtStart)
            out.putInt(STATE_SEARCH_FOUND_CHUNKS, mNumberOfChunkMatches)
        }
        out.putBoolean(STATE_HAVE_MERGE_CONFLICT, mHaveMergeConflict)
        out.putBoolean(STATE_MERGE_CONFLICT_FILTER_ENABLED, mMergeConflictFilterEnabled)
        if (fragment is ViewModeFragment) {
            out.putBoolean(
                STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED,
                (fragment as ViewModeFragment).ismMergeConflictSummaryDisplayed()
            )
        }
        super.onSaveInstanceState(out)
    }

    private fun buildMenu() {
        binding.translatorSidebar.actionMore.setOnClickListener {
            val moreMenu = PopupMenu(this@TargetTranslationActivity, it)
            ViewUtil.forcePopupMenuIcons(moreMenu)
            moreMenu.menuInflater.inflate(
                R.menu.menu_target_translation_detail,
                moreMenu.menu
            )

            // display menu item for draft translations
            val draftsMenuItem = moreMenu.menu.findItem(R.id.action_drafts_available)
            draftsMenuItem.setVisible(draftIsAvailable())

            val searchMenuItem = moreMenu.menu.findItem(R.id.action_search)
            val searchSupported: Boolean = this.isSearchSupported
            searchMenuItem.setVisible(searchSupported)

            val markAllChunksItem = moreMenu.menu.findItem(R.id.mark_chunks_done)
            val markAllChunksSupported: Boolean = this.isMarkAllChunksSupported
            markAllChunksItem.setVisible(markAllChunksSupported)

            moreMenu.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
                val id = item.itemId
                when (id) {
                    R.id.action_translations -> {
                        finish()
                        return@OnMenuItemClickListener true
                    }
                    R.id.action_publish -> {
                        val publishIntent =
                            Intent(this@TargetTranslationActivity, PublishActivity::class.java)
                        publishIntent.putExtra(
                            PublishActivity.EXTRA_TARGET_TRANSLATION_ID,
                            targetTranslation!!.id
                        )
                        publishIntent.putExtra(
                            PublishActivity.EXTRA_CALLING_ACTIVITY,
                            PublishActivity.ACTIVITY_TRANSLATION
                        )
                        startActivity(publishIntent)
                        // TRICKY: we may move back and forth between the publisher and translation activites
                        // so we finish to avoid filling the stack.
                        finish()
                        return@OnMenuItemClickListener true
                    }
                    R.id.action_drafts_available -> {
                        val intent =
                            Intent(this@TargetTranslationActivity, DraftActivity::class.java)
                        intent.putExtra(
                            DraftActivity.EXTRA_TARGET_TRANSLATION_ID,
                            targetTranslation!!.id
                        )
                        startActivity(intent)
                        return@OnMenuItemClickListener true
                    }
                    R.id.action_backup -> {
                        val backupFt = supportFragmentManager.beginTransaction()
                        val backupPrev =
                            supportFragmentManager.findFragmentByTag(BackupDialog.TAG)
                        if (backupPrev != null) {
                            backupFt.remove(backupPrev)
                        }
                        backupFt.addToBackStack(null)

                        val backupDialog = BackupDialog()
                        val args = Bundle()
                        args.putString(
                            BackupDialog.ARG_TARGET_TRANSLATION_ID,
                            targetTranslation!!.id
                        )
                        backupDialog.arguments = args
                        backupDialog.show(backupFt, BackupDialog.TAG)
                        return@OnMenuItemClickListener true
                    }
                    R.id.action_print -> {
                        val printFt = supportFragmentManager.beginTransaction()
                        val printPrev = supportFragmentManager.findFragmentByTag("printDialog")
                        if (printPrev != null) {
                            printFt.remove(printPrev)
                        }
                        printFt.addToBackStack(null)

                        val printDialog = PrintDialog()
                        val printArgs = Bundle()
                        printArgs.putString(
                            PrintDialog.ARG_TARGET_TRANSLATION_ID,
                            targetTranslation!!.id
                        )
                        printDialog.arguments = printArgs
                        printDialog.show(printFt, "printDialog")
                        return@OnMenuItemClickListener true
                    }
                    R.id.action_feedback -> {
                        val ft = supportFragmentManager.beginTransaction()
                        val prev = supportFragmentManager.findFragmentByTag("bugDialog")
                        if (prev != null) {
                            ft.remove(prev)
                        }
                        ft.addToBackStack(null)

                        val dialog = FeedbackDialog()
                        dialog.show(ft, "bugDialog")
                        return@OnMenuItemClickListener true
                    }
                    R.id.action_settings -> {
                        val settingsIntent =
                            Intent(this@TargetTranslationActivity, SettingsActivity::class.java)
                        startActivity(settingsIntent)
                        return@OnMenuItemClickListener true
                    }
                    R.id.action_search -> {
                        setSearchBarVisibility(true)
                        return@OnMenuItemClickListener true
                    }
                    R.id.mark_chunks_done -> {
                        (fragment as ViewModeFragment?)!!.markAllChunksDone()
                        return@OnMenuItemClickListener true
                    }
                    else -> false
                }
            })
            moreMenu.show()
        }
    }

    /**
     * hide search bar and clear search text
     */
    private fun removeSearchBar() {
        setSearchBarVisibility(false)
        filterText = null
        filter(null) // clear search filter
    }

    val isSearchSupported: Boolean
        /**
         * method to see if searching is supported
         */
        get() = (fragment as? ViewModeFragment)?.hasFilter() ?: false

    val isMarkAllChunksSupported: Boolean
        /**
         * Check to see if marking all chunks done is supported
         */
        get() = fragment is ReviewModeFragment

    /**
     * change state of search bar
     * @param show - if true set visible
     */
    private fun setSearchBarVisibility(show: Boolean) {
        // toggle search bar
        with (binding) {
            var visibility = View.GONE
            if (show) {
                visibility = View.VISIBLE
            } else {
                setSearchSpinner(false, 0, true, true)
            }
            searchPane.root.visibility = visibility
            searchEnabled = show

            if (searchTextWatcher != null) {
                // remove old listener
                searchPane.searchText.removeTextChangedListener(searchTextWatcher)
                searchTextWatcher = null
            }

            if (show) {
                searchTextWatcher = object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {
                    }

                    override fun onTextChanged(
                        s: CharSequence,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {
                    }

                    override fun afterTextChanged(s: Editable) {
                        mSearchTimer?.cancel()
                        mSearchTimerTask = SearchTimerTask(this@TargetTranslationActivity, s)
                        mSearchTimer = Timer().apply {
                            schedule(mSearchTimerTask, SEARCH_START_DELAY.toLong())
                        }
                    }
                }

                searchPane.searchText.addTextChangedListener(searchTextWatcher)
                if (mSearchResumed) {
                    // we don't have a way to reliably determine the state of the soft keyboard
                    //   so we don't initially show the keyboard on resume.  This should be less
                    //   annoying than always popping up the keyboard on resume
                    mSearchResumed = false
                } else {
                    setFocusOnTextSearchEdit()
                }
            } else {
                filter(null) // clear search filter
                closeKeyboard(this@TargetTranslationActivity)
            }

            if (searchString != null) { // restore after rotate
                searchPane.searchText.setText(searchString)
                if (show) {
                    filter(searchString)
                }
                searchString = null
            }

            searchPane.closeSearch.setOnClickListener { removeSearchBar() }

            val types: MutableList<String> = ArrayList()
            types.add(baseContext.resources.getString(R.string.search_source))
            types.add(baseContext.resources.getString(R.string.search_translation))
            val typesAdapter = ArrayAdapter(baseContext, android.R.layout.simple_spinner_item, types)
            typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            searchPane.searchType.adapter = typesAdapter

            // restore last search type
            val lastSearchSourceStr = prefRepository.getDefaultPref(
                SEARCH_SOURCE, SearchSubject.SOURCE.name.uppercase(
                    Locale.getDefault()
                )
            )
            var lastSearchSource = SearchSubject.SOURCE
            try {
                lastSearchSource =
                    SearchSubject.valueOf(lastSearchSourceStr!!.uppercase(Locale.getDefault()))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            searchPane.searchType.setSelection(lastSearchSource.ordinal)
            searchPane.searchType.onItemSelectedListener = this@TargetTranslationActivity
        }
    }

    /**
     * this seems crazy that we have to do a delay within a delay, but it is the only thing that works
     * to bring up keyboard.  Guessing that it is because there is so much redrawing that is
     * happening on bringing up the search bar.
     */
    @Deprecated("")
    private fun setFocusOnTextSearchEdit() {
        val handler1 = Handler(Looper.getMainLooper())
        handler1.post {
            binding.searchPane.searchText.isFocusableInTouchMode = true
            binding.searchPane.searchText.requestFocus()

            val handler2 = Handler(Looper.getMainLooper())
            handler2.post {
                showKeyboard(
                this@TargetTranslationActivity,
                binding.searchPane.searchText,
                true
                )
            }
        }
    }

    /**
     * notify listener of search state changes
     * @param doingSearch - search is currently processing
     * @param numberOfChunkMatches - number of chunks that have the search string
     * @param atEnd - we are at last search item
     * @param atStart - we are at first search item
     */
    private fun setSearchSpinner(
        doingSearch: Boolean,
        numberOfChunkMatches: Int,
        atEnd: Boolean,
        atStart: Boolean
    ) {
        mSearchAtEnd = atEnd
        mSearchAtStart = atStart
        mNumberOfChunkMatches = numberOfChunkMatches
        with (binding) {
            searchPane.searchProgress.visibility = if (doingSearch) View.VISIBLE else View.GONE
            val showSearchNavigation = !doingSearch && (numberOfChunkMatches > 0)
            val searchVisibility = if (showSearchNavigation) View.VISIBLE else View.INVISIBLE

            searchPane.downSearch.visibility = if (atEnd) View.INVISIBLE else searchVisibility
            searchPane.upSearch.visibility = if (atStart) View.INVISIBLE else searchVisibility

            val msg = resources.getString(mFoundTextFormat, numberOfChunkMatches)
            searchPane.found.visibility = if (!doingSearch) View.VISIBLE else View.INVISIBLE
            searchPane.found.text = msg
        }
    }

    /**
     * called if search type is changed
     * @param parent
     * @param view
     * @param pos
     * @param id
     */
    override fun onItemSelected(
        parent: AdapterView<*>?, view: View,
        pos: Int, id: Long
    ) {
        filter(filterText) // do search with search string in edit control
    }

    /**
     * called if no search type is selected
     * @param parent
     */
    override fun onNothingSelected(parent: AdapterView<*>?) {
        // do nothing
    }

    private val filterSubject: SearchSubject
        /**
         * get the type of search
         */
        get() {
            return with (binding.searchPane.searchType) {
                val pos = selectedItemPosition
                if (pos == 0) {
                    SearchSubject.SOURCE
                } else null
            } ?: SearchSubject.TARGET
        }

    private var filterText: String?
        /**
         * get search text in search bar
         */
        get() = binding.searchPane.searchText.text.toString()
        /**
         * set search text in search bar
         */
        private set(text) {
            binding.searchPane.searchText.setText(text)
        }


    /**
     * Filters the list, currently it just marks chunks with text
     * @param constraint
     */
    fun filter(constraint: String?) {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            (fragment as? ViewModeFragment)?.apply {
                // preserve current search type

                val subject: SearchSubject = filterSubject
                prefRepository.setDefaultPref(SEARCH_SOURCE, subject.name.uppercase(Locale.getDefault()))
                if (constraint != null) {
                    filter(constraint, subject)
                }
            }
        }
    }

    /**
     * move to next/previous search item
     * @param next if true then find next, otherwise will find previous
     */
    private fun moveSearch(next: Boolean) {
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            (fragment as? ViewModeFragment)?.onMoveSearch(next)
        }
    }

    /**
     * Checks if a draft is available
     *
     * @return
     */
    private fun draftIsAvailable(): Boolean {
        val draftTranslations = library.index().findTranslations(
            targetTranslation!!.targetLanguage.slug,
            targetTranslation!!.projectId,
            null,
            "book",
            null,
            0,
            -1
        )
        return draftTranslations.isNotEmpty()
    }

    override fun onResume() {
        super.onResume()
        notifyDatasetChanged()
        buildMenu()
        setMergeConflictFilter(
            mMergeConflictFilterEnabled,
            mMergeConflictFilterEnabled
        ) // restore last state
    }

    public override fun onPause() {
        super.onPause()

        if (fragment is ViewModeFragment) {
            mShowConflictSummary =
                (fragment as ViewModeFragment).ismMergeConflictSummaryDisplayed() // update current state
        }
    }

    fun closeKeyboard() {
        if (fragment is ViewModeFragment) {
            val enteringSearchText =
                searchEnabled && binding.searchPane.searchText.hasFocus()
            if (!enteringSearchText) { // we don't want to close keyboard if we are entering search text
                (fragment as? ViewModeFragment)?.closeKeyboard()
            }
        }
    }

    private fun checkIfCursorStillOnScreen() {
        val cursorPos = cursorPositionOnScreen
        if (cursorPos != null) {
            with (binding.fragmentContainer) {
                var visible = true

                val scrollBounds = Rect()
                getHitRect(scrollBounds)

                if (cursorPos.top < scrollBounds.top) {
                    visible = false
                } else if (cursorPos.bottom > scrollBounds.bottom) {
                    visible = false
                }

                if (!visible) {
                    closeKeyboard()
                }
            }
        }
    }

    private val cursorPositionOnScreen: Rect?
        get() {
            val focusedView = currentFocus
            if (focusedView != null) {
                // get view position on screen

                val l = IntArray(2)
                focusedView.getLocationOnScreen(l)
                val focusedViewX = l[0]
                val focusedViewY = l[1]

                if (focusedView is EditText) {
                    // getting relative cursor position

                    val editText = focusedView
                    val pos = editText.selectionStart
                    val layout = editText.layout
                    if (layout != null) {
                        val line = layout.getLineForOffset(pos)
                        val baseline = layout.getLineBaseline(line)
                        val ascent = layout.getLineAscent(line)

                        // convert relative positions to absolute position
                        val x = focusedViewX + layout.getPrimaryHorizontal(pos).toInt()
                        val bottomY = focusedViewY + baseline
                        val y = bottomY + ascent

                        return Rect(x, y, x, bottomY) // ignore width of cursor for now
                    }
                }
            }

            return null
        }

    override fun onScrollProgress(position: Int) {
//        position = handleItemCountIfChanged(position);
        // TODO: 2/16/17 record scroll position
        val progress = computeProgressFromPosition(position)
        // Too much logging
        //Log.d(TAG, "onScrollProgress: position=" + position + ", mapped to progressbar=" + progress);
        binding.searchPane.searchProgress.progress = progress
        checkIfCursorStillOnScreen()
    }

    override fun onDataSetChanged(count: Int) {
        with(binding.searchPane.searchProgress) {
            var count = count
            val initialMax = max
            val initialProgress = progress

            count = setSeekbarMax(count)
            val newMax = max
            if (initialMax != newMax) { // if seekbar maximum has changed
                // adjust proportionally
                val newProgress = newMax * initialProgress / initialMax
                progress = newProgress
            }
            closeKeyboard()
            setupGraduations()
        }
    }

    private val itemCount: Int
        /**
         * get number of items in adapter
         * @return
         */
        get() = (fragment as? ViewModeFragment)?.itemCount ?: 0

    /**
     * sets seekbar maximum based on item count, and add granularity if item count is small
     * @param itemCount
     * @return
     */
    private fun setSeekbarMax(itemCount: Int): Int {
        var itemCount = itemCount
        val minimumSteps = 300

        Log.i(TAG, "setSeekbarMax: itemCount=$itemCount")

        if (itemCount < 1) { // sanity check
            itemCount = 1
        }

        seekbarMultiplier =
            if (itemCount < minimumSteps) {  // increase step size if number of cards is small, this gives more granularity in positioning
                (minimumSteps / itemCount) + 1
            } else {
                1
            }

        val newMax = itemCount * seekbarMultiplier
        val oldMax = binding.searchPane.searchProgress.max
        if (newMax != oldMax) {
            Log.i(
                TAG,
                "setSeekbarMax: oldMax=$oldMax, newMax=$newMax, mSeekbarMultiplier=$seekbarMultiplier"
            )
            binding.searchPane.searchProgress.max = newMax
            mOldItemCount = itemCount
        } else {
            Log.i(TAG, "setSeekbarMax: max unchanged=$oldMax")
        }
        return itemCount
    }

    /**
     * initialize text on graduations if enabled
     */
    private fun setupGraduations() {
        if (enableGrids) {
            with(binding) {
                val numCards = searchPane.searchProgress.max / seekbarMultiplier

                val maxChapterStr = getChapterSlug(numCards - 1)
                val maxChapter = maxChapterStr.toInt()

                // Set up visibility of the graduation bar.
                // Display graduations evenly spaced by number of chapters (but not more than the number
                // of chapters that exist). As a special case, display nothing if there's only one chapter.
                // Also, show nothing unless we're in read mode, since the other modes are indexed by
                // frame, not by chapter, so displaying either frame numbers or chapter numbers would be
                // nonsensical.
                var numVisibleGraduations =
                    min(numCards.toDouble(), translatorSidebar.actionSeekGraduations.childCount.toDouble())
                        .toInt()

                if ((maxChapter > 0) && (maxChapter < numVisibleGraduations)) {
                    numVisibleGraduations = maxChapter
                }

                if (numVisibleGraduations < 2) {
                    numVisibleGraduations = 0
                }

                // Set up the visible chapters.
                for (i in 0 until numVisibleGraduations) {
                    val container = translatorSidebar.actionSeekGraduations.getChildAt(i) as ViewGroup
                    container.visibility = View.VISIBLE
                    val text = container.getChildAt(1) as TextView

                    val position = i * (numCards - 1) / (numVisibleGraduations - 1)
                    val chapter = getChapterSlug(position)
                    text.text = chapter
                }

                // Un-display the invisible chapters.
                for (i in numVisibleGraduations until translatorSidebar.actionSeekGraduations.childCount) {
                    translatorSidebar.actionSeekGraduations.getChildAt(i).visibility = View.GONE
                }
            }
        }
    }

    /**
     * get the chapter slug for the position
     * @param position
     * @return
     */
    private fun getChapterSlug(position: Int): String {
        return (fragment as? ViewModeFragment)?.getChapterSlug(position) ?: (position + 1).toString()
    }

    /**
     * user has selected to update sources
     */
    override fun onUpdateSources() {
        setResult(RESULT_DO_UPDATE)
        finish()
    }

    private fun displaySeekBarAsInverted(): Boolean {
        return binding.searchPane.searchProgress is VerticalSeekBar
    }

    private fun computeProgressFromPosition(position: Int): Int {
        val correctedProgress = correctProgress(position * seekbarMultiplier)
        return limitRange(correctedProgress, 0, binding.searchPane.searchProgress.max)
    }

    private fun computePositionFromProgress(progress: Int): Int {
        var correctedProgress = correctProgress(progress)
        correctedProgress = limitRange(correctedProgress, 0, binding.searchPane.searchProgress.max - 1)
        return correctedProgress / seekbarMultiplier
    }

    /**
     * if seekbar is inverted, this will correct the progress
     * @param progress
     * @return
     */
    private fun correctProgress(progress: Int): Int {
        return if (displaySeekBarAsInverted()) binding.searchPane.searchProgress.max - progress else progress
    }

    override fun onNoSourceTranslations(targetTranslationId: String) {
        if (fragment !is FirstTabFragment) {
            fragment = FirstTabFragment().apply {
                setArguments(intent.extras)
                supportFragmentManager.beginTransaction().replace(R.id.fragment_container, this)
                    .commit()
                buildMenu()
            }
        }
    }

    override fun openTranslationMode(mode: TranslationViewMode, extras: Bundle?) {
        val fragmentExtras = Bundle()
        fragmentExtras.putAll(intent.extras)
        if (extras != null) {
            fragmentExtras.putAll(extras)
        }

        // close the keyboard when switching between modes
        if (currentFocus != null) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
        } else {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }

        translator.setLastViewMode(targetTranslation!!.id, mode)
        setupSidebarModeIcons()

        when (mode) {
            TranslationViewMode.READ -> if (fragment !is ReadModeFragment) {
                fragment = ReadModeFragment().apply {
                    setArguments(fragmentExtras)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, this).commit()
                    // TODO: animate
                    // TODO: update menu
                }
            }

            TranslationViewMode.CHUNK -> if (fragment !is ChunkModeFragment) {
                fragment = ChunkModeFragment().apply {
                    setArguments(fragmentExtras)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, this).commit()
                    // TODO: animate
                    // TODO: update menu
                }
            }

            TranslationViewMode.REVIEW -> if (fragment !is ReviewModeFragment) {
                fragment = ReviewModeFragment().apply {
                    setArguments(fragmentExtras)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, this).commit()
                    // TODO: animate
                    // TODO: update menu
                }
            }
        }
    }

    /**
     * Restart scheduled translation commits
     */
    override fun restartAutoCommitTimer() {
        mCommitTimer.cancel()
        mCommitTimer = Timer()
        mCommitTimer.schedule(object : TimerTask() {
            override fun run() {
                if (targetTranslation != null) {
                    try {
                        targetTranslation!!.commit()
                    } catch (e: Exception) {
                        Logger.e(
                            TargetTranslationActivity::class.java.name,
                            "Failed to commit the latest translation of " + targetTranslation!!.id,
                            e
                        )
                    }
                } else {
                    Logger.w(
                        TAG,
                        "cannot auto commit target translation. The target translation is null."
                    )
                    mCommitTimer.cancel()
                }
            }
        }, COMMIT_INTERVAL, COMMIT_INTERVAL)
    }

    /**
     * callback on search state changes
     * @param doingSearch - search is currently processing
     * @param numberOfChunkMatches - number of chunks that have the search string
     * @param atEnd - we are at last search item highlighted
     * @param atStart - we are at first search item highlighted
     */
    override fun onSearching(
        doingSearch: Boolean,
        numberOfChunkMatches: Int,
        atEnd: Boolean,
        atStart: Boolean
    ) {
        setSearchSpinner(doingSearch, numberOfChunkMatches, atEnd, atStart)
    }

    override fun onHasSourceTranslations() {
        val viewMode = translator.getLastViewMode(targetTranslation!!.id)
        fragment = when (viewMode) {
            TranslationViewMode.READ -> {
                ReadModeFragment()
            }

            TranslationViewMode.CHUNK -> {
                ChunkModeFragment()
            }

            TranslationViewMode.REVIEW -> {
                ReviewModeFragment()
            }
        }.apply {
            arguments = intent.extras
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, this)
                .commit()
            // TODO: animate
            // TODO: update menu
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val onTouchEvent = (fragment as? ViewModeFragment)?.onTouchEvent(event)
        return onTouchEvent ?: super.dispatchTouchEvent(event)
    }

    override fun onDestroy() {
        mCommitTimer.cancel()
        try {
            targetTranslation!!.commit()
        } catch (e: Exception) {
            Logger.e(this.javaClass.name, "Failed to commit changes before closing translation", e)
        }
        closeKeyboard(this@TargetTranslationActivity)
        super.onDestroy()
    }

    /**
     * Causes the activity to tell the fragment it needs to reload
     */
    private fun notifyDatasetChanged() {
        (fragment as? ViewModeFragment)?.adapter?.triggerNotifyDataSetChanged()
    }

    /**
     * Causes the activity to tell the fragment that everything needs to be redrawn
     */
    fun redrawTarget() {
        (fragment as? ViewModeFragment)?.onResume()
    }

    /**
     * Updates the visual state of all the sidebar icons to match the application's current mode.
     *
     * Call this when creating the activity or when changing modes.
     */
    private fun setupSidebarModeIcons() {
        val viewMode = translator.getLastViewMode(targetTranslation!!.id)

        with (binding.translatorSidebar) {
            // Set the non-highlighted icons by default.
            actionReview.setImageResource(R.drawable.ic_view_week_inactive_24dp)
            actionChunk.setImageResource(R.drawable.ic_content_copy_inactive_24dp)
            actionRead.setImageResource(R.drawable.ic_subject_inactive_24dp)

            // Clear the highlight background.
            //
            // This is more properly done with setBackground(), but that requires a higher API
            // level than this application's minimum. Equivalently use setBackgroundDrawable(),
            // which is deprecated, instead.
            actionReview.background = null
            actionChunk.background = null
            actionRead.background = null

            // For the active view, set the correct icon, and highlight the background.
            val highlightedColor = resources.getColor(R.color.primary_dark)
            when (viewMode) {
                TranslationViewMode.READ -> {
                    actionRead.setImageResource(R.drawable.ic_subject_white_24dp)
                    actionRead.setBackgroundColor(highlightedColor)
                }

                TranslationViewMode.CHUNK -> {
                    actionChunk.setImageResource(R.drawable.ic_content_copy_white_24dp)
                    actionChunk.setBackgroundColor(highlightedColor)
                }

                TranslationViewMode.REVIEW -> if (mMergeConflictFilterEnabled) {
                    warnMergeConflict.setBackgroundColor(highlightedColor) // highlight background of the conflict icon
                    onEnableMergeConflict(showConflicted = true, true)
                } else {
                    actionReview.setBackgroundColor(highlightedColor)
                    actionReview.setImageResource(R.drawable.ic_view_week_white_24dp)
                }
            }
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

    companion object {
        private const val TAG = "TranslationActivity"

        private const val COMMIT_INTERVAL = (2 * 60 * 1000 // commit changes every 2 minutes
                ).toLong()
        const val SEARCH_START_DELAY: Int = 1000
        const val STATE_SEARCH_ENABLED: String = "state_search_enabled"
        const val STATE_SEARCH_TEXT: String = "state_search_text"
        const val STATE_HAVE_MERGE_CONFLICT: String = "state_have_merge_conflict"
        const val STATE_MERGE_CONFLICT_FILTER_ENABLED: String =
            "state_merge_conflict_filter_enabled"
        const val STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED: String =
            "state_merge_conflict_summary_displayed"
        const val SEARCH_SOURCE: String = "search_source"
        const val STATE_SEARCH_AT_END: String = "state_search_at_end"
        const val STATE_SEARCH_AT_START: String = "state_search_at_start"
        const val STATE_SEARCH_FOUND_CHUNKS: String = "state_search_found_chunks"
        const val RESULT_DO_UPDATE: Int = 42
    }
}