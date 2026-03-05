package com.door43.translationstudio.ui.translate

import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.door43.data.AssetsProvider
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.core.entity.SourceTranslation
import com.door43.translationstudio.databinding.FragmentStackedCardListBinding
import com.door43.translationstudio.databinding.RemovableTabBinding
import com.door43.translationstudio.getBestFontForLanguage
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.translate.review.SearchSubject
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONException
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.unfoldingword.resourcecontainer.ResourceContainer
import org.unfoldingword.tools.logger.Logger
import org.unfoldingword.tools.taskmanager.ManagedTask
import kotlin.math.abs
import kotlin.math.asin

/**
 * Created by joel on 9/18/2015.
 */
abstract class ViewModeFragment : BaseFragment(),
    ViewModeAdapter.OnEventListener,
    ChooseSourceTranslationDialog.OnClickListener,
    ManagedTask.OnFinishedListener {

    private var layoutManager: LinearLayoutManager? = null
    private var adapter: ViewModeAdapter<*>? = null
    private var fingerScroll = false
    private var listener: OnEventListener? = null
    private var gesture: GestureDetector? = null
    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private var _binding: FragmentStackedCardListBinding? = null
    protected val binding: FragmentStackedCardListBinding get() = _binding!!

    // Using Koin's inject based on your project configuration
    protected val viewModel: TargetTranslationViewModel by activityViewModel()

    protected var chapterSlug: String? = null
    protected var chunkSlug: String? = null

    val typography: Typography by inject()
    val renderingProvider: RenderingProvider by inject()
    val assetsProvider: AssetsProvider by inject()

    companion object {
        val TAG: String = ViewModeFragment::class.java.simpleName

        /**
         * Resets the static variables
         */
        fun reset() {
            ContainerCache.empty()
        }
    }

    /**
     * Returns an instance of the adapter
     * @return
     */
    internal abstract fun generateAdapter(): ViewModeAdapter<*>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = generateAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStackedCardListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments
        requireNotNull(args)

        chapterSlug = args.getString(Translator.EXTRA_CHAPTER_ID, null)
        chunkSlug = args.getString(Translator.EXTRA_FRAME_ID, null)

        initRecyclerView()
        initGestureDetector()

        progressDialog = ProgressHelper.newInstance(
            childFragmentManager,
            R.string.loading_sources,
            false
        )

        setupObservers()
        viewModel.setSelectedResourceContainer()

        // notify activity contents changed
        adapter?.apply {
            onDataSetChanged(itemCount)
            onClickListener = this@ViewModeFragment
        }

        // let child classes modify the view
        onPrepareView(binding.root)
    }

    private fun initRecyclerView() {
        layoutManager = LinearLayoutManager(activity)
        binding.translationCards.layoutManager = layoutManager
        binding.translationCards.itemAnimator = DefaultItemAnimator()
        binding.translationCards.adapter = adapter

        adapter?.onClickListener = this

        binding.translationCards.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                fingerScroll = true
                super.onScrollStateChanged(recyclerView, newState)
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (fingerScroll && listener != null) {
                    listener?.onScrollProgress(currentPosition)
                }
            }
        })
    }

    private fun initGestureDetector() {
        gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            var mLastOnDownEvent: MotionEvent? = null
            private val SWIPE_THRESHOLD_VELOCITY = 20f
            private val SWIPE_MIN_DISTANCE = 50f
            private val SWIPE_MAX_ANGLE_DEG = 30.0

            override fun onDown(e: MotionEvent): Boolean {
                mLastOnDownEvent = e
                return super.onDown(e)
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                var event1 = e1
                if (event1 == null) {
                    event1 = mLastOnDownEvent
                }
                if (event1 == null) return false

                try {
                    val distanceX = e2.x - event1.x
                    val distanceY = e2.y - event1.y
                    // don't handle vertical swipes (division error)
                    if (distanceX == 0f) return false

                    val flingAngle = Math.toDegrees(asin(abs(distanceY / distanceX).toDouble()))
                    if (flingAngle <= SWIPE_MAX_ANGLE_DEG && abs(distanceX) >= SWIPE_MIN_DISTANCE && abs(velocityX) >= SWIPE_THRESHOLD_VELOCITY) {
                        if (distanceX > 0) {
                            onRightSwipe(event1, e2)
                        } else {
                            onLeftSwipe(event1, e2)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    protected open fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.model
                        .map { it.progress }
                        .distinctUntilChanged()
                        .collect { progress ->
                            if (progress != null) {
                                progressDialog?.apply {
                                    show()
                                    setProgress(progress.progress)
                                    setMessage(progress.message)
                                    setMax(progress.max)
                                }
                            } else {
                                progressDialog?.dismiss()
                            }
                        }
                }

                launch {
                    viewModel.model
                        .map { it.items }
                        .distinctUntilChanged()
                        .collect { items ->
                            if (items.isNotEmpty()) {
                                adapter?.let {
                                    if (chapterSlug == null) {
                                        chapterSlug = viewModel.getLastFocusChapterId()
                                    }
                                    if (chunkSlug == null) {
                                        chunkSlug = viewModel.getLastFocusFrameId()
                                    }
                                    it.initializeListItems(items, chapterSlug, chunkSlug)
                                    doScrollToPosition(it.startPosition, 0)
                                }
                            } else {
                                listener?.onNoSourceTranslations()
                            }
                        }
                }
            }
        }
    }

    /**
     * scroll panes to go to specific position with vertical offset
     */
    fun doScrollToPosition(position: Int, offset: Int) {
        layoutManager?.let {
            it.scrollToPositionWithOffset(position, offset)
            Logger.i(TAG, "doScrollToPosition: position=$position, offset=$offset")
        }
        listener?.onScrollProgress(position)
    }

    /**
     * set true if we want to initially show a summary of merge conflicts
     */
    fun setShowMergeSummary(showMergeSummary: Boolean) {
        adapter?.showMergeSummary = showMergeSummary
    }

    /**
     * get the chapter slug for the position
     */
    fun getChapterSlug(position: Int): String {
        return adapter?.getChapterSlug(position) ?: (position + 1).toString()
    }

    /**
     * Called when the user performs a swipe towards the right
     */
    protected open fun onRightSwipe(e1: MotionEvent, e2: MotionEvent) {}

    /**
     * Called when the user performs a swipe towards the left
     */
    protected open fun onLeftSwipe(e1: MotionEvent, e2: MotionEvent) {}

    /**
     * Returns the currently selected resource container
     */
    protected fun getSelectedResourceContainer(): ResourceContainer? {
        return viewModel.resourceContainer
    }

    /**
     * Scrolls to the given frame
     */
    override fun scrollToChunk(chapterSlug: String, chunkSlug: String) {
        closeKeyboard()
        val position = adapter?.getItemPosition(chapterSlug, chunkSlug) ?: -1
        if (position != -1) {
            doScrollToPosition(position, 0)
        }
    }

    /**
     * Similar to scrollToChunk except it will automatically guess what chunk to scroll to.
     */
    fun scrollToVerse(chapterSlug: String, verseSlug: String) {
        val chunkSlug = getVerseChunk(chapterSlug, verseSlug)
        scrollToChunk(chapterSlug, chunkSlug)
    }

    /**
     * Returns the corresponding chunk slug.
     * Override this method if you need to map verses to chunks.
     */
    open fun getVerseChunk(chapterSlug: String, verseSlug: String): String {
        return verseSlug
    }

    /**
     * Returns the adapter position of a view holder under the coordinates
     */
    protected fun findViewHolderAdapterPosition(x: Float, y: Float): Int {
        val view = binding.translationCards.findChildViewUnder(x, y)
        return if (view != null) {
            binding.translationCards.getChildAdapterPosition(view)
        } else {
            0
        }
    }

    /**
     * Returns a viewholder item for the adapter position
     */
    protected fun getViewHolderForAdapterPosition(position: Int): RecyclerView.ViewHolder? {
        return binding.translationCards.findViewHolderForAdapterPosition(position)
    }

    /**
     * Returns a sample viewholder so we can check on the state of the ui
     */
    protected fun getViewHolderSample(): RecyclerView.ViewHolder? {
        return layoutManager?.let {
            val position = currentPosition
            binding.translationCards.findViewHolderForLayoutPosition(position)
        }
    }

    protected open fun onPrepareView(rootView: View) {
        // place holder so child classes can modify the view
    }

    fun getAdapter(): ViewModeAdapter<*>? {
        return adapter
    }

    override fun onDataSetChanged(count: Int) {
        listener?.onDataSetChanged(count)
    }

    override fun onEnableMergeConflict(showConflicted: Boolean, active: Boolean) {
        listener?.onEnableMergeConflict(showConflicted, active)
    }

    override fun getVisibleViewHolder(position: Int): RecyclerView.ViewHolder? {
        return layoutManager?.let {
            binding.translationCards.findViewHolderForAdapterPosition(position)
        }
    }

    /**
     * gets item count of adapter
     */
    fun getItemCount(): Int {
        return adapter?.itemCount ?: 0
    }

    /**
     * enable/disable merge conflict filter in adapter
     */
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // for triggerNotifyDataSetChanged if internal/protected
    fun setMergeConflictFilter(enableFilter: Boolean, forceMergeConflict: Boolean) {
        adapter?.let {
            it.setMergeConflictFilter(enableFilter, forceMergeConflict)
            it.triggerNotifyDataSetChanged()
        }
    }

    /**
     * Filters the adapter by the constraint
     */
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    fun filter(constraint: CharSequence, subject: SearchSubject) {
        adapter?.let {
            it.filter(constraint, subject, currentPosition)
            it.triggerNotifyDataSetChanged()
        }
    }

    /**
     * move to next/previous search item
     */
    fun onMoveSearch(next: Boolean) {
        adapter?.onMoveSearch(next)
    }

    /**
     * Checks if filtering is enabled.
     */
    fun hasFilter(): Boolean {
        return adapter?.hasFilter() ?: false
    }

    /**
     * returns true if merge conflict summary dialog is being displayed.
     */
    fun ismMergeConflictSummaryDisplayed(): Boolean {
        return adapter?.isMergeConflictSummaryDisplayed() ?: false
    }

    override fun showKeyboard(view: View) {
        activity?.let { App.showKeyboard(it, view) }
    }

    /**
     * Forces the software keyboard to close
     */
    override fun closeKeyboard() {
        activity?.let { App.closeKeyboard(it) }
    }

    override fun onTranslationWordClick(resourceContainerSlug: String, chapterSlug: String, width: Int) {}

    override fun onTranslationManualClick(section: String, slug: String) {}

    override fun onTranslationNoteClick(note: TranslationHelp, width: Int) {}

    override fun onTranslationQuestionClick(question: TranslationHelp, width: Int) {}

    /**
     * Require correct interface
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        onAttachToContext(context)
    }

    /**
     * This method will be called when the fragment attaches to the context/activity
     */
    protected open fun onAttachToContext(context: Context) {
        try {
            this.listener = context as OnEventListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement ViewModeFragment.OnEventListener")
        }
    }

    /**
     * Called when the scroll progress manually changes
     */
    fun onScrollProgressUpdate(scrollProgress: Int, percent: Int) {
        fingerScroll = false
        Log.d(TAG, "onScrollProgressUpdate: scrollProgress=$scrollProgress, percent=$percent")
        if (percent == 0) {
            binding.translationCards.scrollToPosition(scrollProgress)
        } else {
            fineScrollToPosition(scrollProgress, percent)
        }
    }

    /**
     * makes sure view is visible, plus it scrolls down proportionally in view
     */
    private fun fineScrollToPosition(position: Int, percent: Int) {
        binding.translationCards.scrollToPosition(position) // do coarse adjustment

        val visibleChild = binding.translationCards.getChildAt(0) ?: return

        val holder = binding.translationCards.getChildViewHolder(visibleChild) ?: return

        val itemHeight = holder.itemView.height
        val offset = (percent * itemHeight / 100)

        val layoutManager = binding.translationCards.layoutManager as? LinearLayoutManager
        layoutManager?.scrollToPositionWithOffset(position, -offset)
    }

    fun setScrollProgress(position: Int) {
        // TODO: 6/28/16 update scrollbar
    }

    override fun onSourceTranslationTabClick(sourceTranslationId: String) {
        chapterSlug = null
        chunkSlug = null
        updateListStartPosition()
        viewModel.setSelectedResourceContainer(sourceTranslationId)
    }

    override fun onSourceRemoveButtonClicked(sourceTranslationId: String) {
        viewModel.removeOpenSourceTranslation(sourceTranslationId)
        val sourceTranslationIds = viewModel.getOpenSourceTranslations()

        if (sourceTranslationIds.isNotEmpty()) {
            val selectedSourceId = viewModel.getSelectedSourceTranslationId()
            if (selectedSourceId != null) {
                viewModel.setSelectedResourceContainer(selectedSourceId)
            }
        } else {
            listener?.onNoSourceTranslations()
        }
    }

    override fun onNewSourceTranslationTabClick() {
        val ft = parentFragmentManager.beginTransaction()
        val prev = parentFragmentManager.findFragmentByTag("tabsDialog")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        val dialog = ChooseSourceTranslationDialog()
        val args = Bundle()
        args.putString(
            ChooseSourceTranslationDialog.ARG_TARGET_TRANSLATION_ID,
            viewModel.targetTranslation.id
        )
        dialog.setOnClickListener(this)
        dialog.arguments = args
        dialog.show(ft, "tabsDialog")
    }

    override fun onCreateRemovableTabLayout(tag: String, title: String): View {
        val tabBinding = RemovableTabBinding.inflate(LayoutInflater.from(context))

        tabBinding.tab.text = title
        tabBinding.close.tag = tag

        tabBinding.close.setOnClickListener { view ->
            val sourceTranslationId = view.tag as String
            onSourceRemoveButtonClicked(sourceTranslationId)
        }

        return tabBinding.root
    }

    /**
     * if language is specified in values, finds the created tab that has the title text and applies the Typeface for the language
     */
    override fun onApplyLanguageTypefaceToTab(layout: TabLayout, values: ContentValues, title: String) {
        if (values.containsKey("language")) {
            val code = values.getAsString("language")
            val typeface = getBestFontForLanguage(
                typography,
                assetsProvider,
                code
            )
            val view = findTab(layout, title)
            if (view != null) {
                view.setTypeface(typeface, Typeface.NORMAL)
            }
        }
    }

    override fun onCancelTabsDialog(targetTranslationId: String) {}

    override fun onConfirmTabsDialog(sourceTranslationIds: List<String>) {
        val oldSourceTranslationIds = viewModel.getOpenSourceTranslations()
        for (id in oldSourceTranslationIds) {
            viewModel.removeOpenSourceTranslation(id)
        }
        if (sourceTranslationIds.isNotEmpty()) {
            setSelectedSources(sourceTranslationIds)
            val selectedSourceId = viewModel.getSelectedSourceTranslationId()
            if (selectedSourceId != null) {
                viewModel.setSelectedResourceContainer(selectedSourceId)
            }
        } else {
            listener?.onNoSourceTranslations()
        }
    }

    /**
     * finds a TextView with match text within viewGroup (recursive)
     */
    private fun findTab(viewGroup: ViewGroup, match: String): TextView? {
        val count = viewGroup.childCount
        for (i in 0 until count) {
            val view = viewGroup.getChildAt(i)
            if (view is ViewGroup) {
                val foundView = findTab(view, match)
                if (foundView != null) {
                    return foundView
                }
            } else if (view is TextView) {
                val text = view.text
                if (match == text.toString()) {
                    return view
                }
            }
        }
        return null
    }

    private fun setSelectedSources(sourceSlugs: List<String>) {
        val sources = ArrayList<SourceTranslation>()
        for (slug in sourceSlugs) {
            try {
                viewModel.addOpenSourceTranslation(slug)
            } catch (e: Exception) {
                Logger.e(
                    this.javaClass.name,
                    "Error while adding source $slug for ${viewModel.targetTranslation.id}"
                )
                e.printStackTrace()
            }

            val translation = viewModel.getTranslation(slug)
            if (translation != null) {
                val modifiedAt = viewModel.getResourceContainerLastModified(translation)
                sources.add(SourceTranslation(translation, modifiedAt))
            }
        }

        try {
            viewModel.targetTranslation.setSourceTranslations(sources)
        } catch (e: JSONException) {
            Logger.e(
                this.javaClass.name,
                "Failed to set source translations for the target translation ${viewModel.targetTranslation.id}",
                e
            )
        }
    }

    /**
     * Updates the position where the list should start when first built
     */
    private fun updateListStartPosition() {
        val lastItemPosition = currentPosition
        val chapterId = adapter?.getFocusedChapterSlug(lastItemPosition)
        val frameId = adapter?.getFocusedChunkSlug(lastItemPosition)
        if (chapterId != null) {
            viewModel.setLastFocus(chapterId, frameId)
            adapter?.updateListStartPosition(lastItemPosition)
        }
    }

    override fun onDestroy() {
        viewModel.cancelRenderJobs()
        if (layoutManager != null) {
            // save position state
            updateListStartPosition()
        }
        adapter?.onClickListener = null
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (progressDialog != null) {
            progressDialog = null
        }
    }

    /**
     * gets the currently viewed position
     */
    val currentPosition: Int
        get() {
            return layoutManager?.findFirstVisibleItemPosition() ?: 0
        }

    /**
     * Receives touch events directly from the activity
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        return if (gesture != null) {
            gesture!!.onTouchEvent(event)
        } else {
            Logger.w(this.javaClass.name, "The gesture detector was not initialized so the touch was not handled")
            false
        }
    }

    /**
     * Opens a translation mode
     */
    override fun openTranslationMode(mode: TranslationViewMode, extras: Bundle?) {
        listener?.openTranslationMode(mode, extras)
    }

    /**
     * Restarts the auto commit timer
     */
    override fun restartAutoCommitTimer() {
        listener?.restartAutoCommitTimer()
    }

    /**
     * notify listener of search state changes
     */
    override fun onSearching(doingSearch: Boolean, numberOfChunkMatches: Int, atEnd: Boolean, atStart: Boolean) {
        listener?.onSearching(doingSearch, numberOfChunkMatches, atEnd, atStart)
    }

    /**
     * user has selected to update sources
     */
    override fun onUpdateSources() {
        listener?.onUpdateSources()
    }

    override fun onTaskFinished(task: ManagedTask) {}

    /**
     * called to set new selected position
     */
    override fun onSetSelectedPosition(position: Int, offset: Int) {
        Log.d(TAG, "onSetSelectedPosition: position=$position, offset=$offset")
        doScrollToPosition(position, offset)
    }

    abstract fun markAllChunksDone()

    interface OnEventListener {
        /**
         * Called when the user scrolls with their finger
         */
        fun onScrollProgress(progress: Int)

        /**
         * Called when the dataset in the adapter changed
         */
        fun onDataSetChanged(count: Int)

        /**
         * No source translation has been chosen
         */
        fun onNoSourceTranslations()

        /**
         * Opens a particular translation mode
         */
        fun openTranslationMode(mode: TranslationViewMode, extras: Bundle?)

        /**
         * Restarts the timer to auto commit changes
         */
        fun restartAutoCommitTimer()

        /**
         * notify listener of search state changes
         */
        fun onSearching(doingSearch: Boolean, numberOfChunkMatches: Int, atEnd: Boolean, atStart: Boolean)

        /**
         * enable/disable merge conflict indicator
         */
        fun onEnableMergeConflict(showConflicted: Boolean, active: Boolean)

        /**
         * user has selected to update sources
         */
        fun onUpdateSources()
    }
}