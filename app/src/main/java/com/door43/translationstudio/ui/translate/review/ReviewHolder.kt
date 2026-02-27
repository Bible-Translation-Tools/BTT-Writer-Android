package com.door43.translationstudio.ui.translate.review

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.door43.data.AssetsProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.FileHistory
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentMergeCardBinding
import com.door43.translationstudio.databinding.FragmentResourcesListItemBinding
import com.door43.translationstudio.format
import com.door43.translationstudio.formatSub
import com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.Companion.MAX_SOURCE_ITEMS
import com.door43.translationstudio.ui.translate.IReviewListItemBinding
import com.door43.translationstudio.ui.translate.ReviewListItem
import com.door43.translationstudio.ui.translate.ReviewModeAdapter
import com.door43.translationstudio.ui.translate.TranslationHelp
import com.door43.usecases.ParseMergeConflicts
import com.door43.widget.ViewUtil
import com.google.android.material.tabs.TabLayout
import org.eclipse.jgit.api.errors.GitAPIException
import org.unfoldingword.resourcecontainer.Language
import org.unfoldingword.resourcecontainer.Link
import org.unfoldingword.tools.taskmanager.ThreadableUI
import java.io.IOException

/**
 * Represents a review mode view
 */
@SuppressLint("ClickableViewAccessibility")
class ReviewHolder(
    var binding: IReviewListItemBinding,
    private val typography: Typography,
    private val assetsProvider: AssetsProvider,
    private val reviewModeListener: OnReviewModeListener?
) : RecyclerView.ViewHolder(binding.root) {

    private val context: Context = binding.root.context
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val resourceTabClickListener: TabLayout.OnTabSelectedListener
    private val tabSelectedListener: TabLayout.OnTabSelectedListener
    private var mergeTexts: MutableList<TextView>? = null
    private var notes: List<TranslationHelp> = ArrayList()
    private var questions: List<TranslationHelp> = ArrayList()
    private var words: List<Link> = ArrayList()
    private var initialTextSize = 0f
    private var marginInitialLeft = 0

    private val editableTextWatcher: TextWatcher

    private enum class MergeConflictDisplayState {
        NORMAL,
        SELECTED,
        DESELECTED
    }

    companion object {
        private const val TAB_NOTES = 0
        private const val TAB_WORDS = 1
        private const val TAB_QUESTIONS = 2
    }

    init {
        val editButtonDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                reviewModeListener?.onEditorToggle(this@ReviewHolder)
                return true
            }
        })

        resourceTabClickListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val tag = tab.tag as Int
                reviewModeListener?.let {
                    when (tag) {
                        TAB_NOTES -> it.onResourceTabNotesSelected(this@ReviewHolder)
                        TAB_WORDS -> it.onResourceTabWordsSelected(this@ReviewHolder)
                        TAB_QUESTIONS -> it.onResourceTabQuestionsSelected(this@ReviewHolder)
                    }
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                clearHelps()
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}
        }

        tabSelectedListener = object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val sourceTranslationId = tab.tag as String
                reviewModeListener?.onSourceTranslationTabClick(sourceTranslationId)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {}
        }

        val resourceCardDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                reviewModeListener?.onTapResourceCard()
                return true
            }
        })

        editableTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (binding.targetEditableBody?.hasFocus() == true) {
                    reviewModeListener?.onApplyChangedText(s, this@ReviewHolder)
                }
            }

            override fun afterTextChanged(s: Editable) {}
        }

        // Attach listeners when view is created
        itemView.post {
            binding.editButton?.setOnTouchListener { _, event -> editButtonDetector.onTouchEvent(event) }
            binding.resourceCard.setOnTouchListener { _, event -> resourceCardDetector.onTouchEvent(event) }

            binding.targetBody?.setOnTouchListener { v, event ->
                v.onTouchEvent(event)
                v.clearFocus()
                true
            }

            binding.undoButton?.setOnClickListener {
                reviewModeListener?.onUndoTextInTarget(this@ReviewHolder)
            }

            binding.redoButton?.setOnClickListener {
                reviewModeListener?.onRedoTextInTarget(this@ReviewHolder)
            }

            binding.doneSwitch?.setOnCheckedChangeListener { buttonView, isChecked ->
                if (reviewModeListener != null && buttonView.isPressed) {
                    reviewModeListener.onDoneSwitchClicked(this@ReviewHolder, isChecked)
                }
            }

            binding.addNoteButton?.setOnClickListener {
                reviewModeListener?.onCreateFootnoteAtSelection(this@ReviewHolder)
            }

            binding.cancelButton?.setOnClickListener {
                val position = bindingAdapterPosition
                if (reviewModeListener != null && position != RecyclerView.NO_POSITION) {
                    reviewModeListener.onMergeConflictItemCancel(position)
                }
            }

            binding.confirmButton?.setOnClickListener {
                val position = bindingAdapterPosition
                if (reviewModeListener != null && position != RecyclerView.NO_POSITION) {
                    reviewModeListener.onMergeConflictItemConfirm(position)
                }
            }

            // change tabs listener
            binding.newTabButton.setOnClickListener {
                reviewModeListener?.onNewSourceTranslationTabClick()
            }
        }
    }

    fun bind(item: ReviewListItem) {
        showResourceCard(item.resourcesOpened, false)
        ViewUtil.makeLinksClickable(binding.sourceBody)

        // render the cards
        renderSourceCard(item)

        if (itemViewType == ReviewModeAdapter.VIEW_TYPE_CONFLICT) {
            renderConflictingTargetCard(item)
        } else {
            renderTargetCard(item)
        }

        renderResourceCard(item)

        // set up fonts
        binding.sourceBody.format(
            typography,
            assetsProvider,
            TranslationType.SOURCE,
            item.source.language.slug,
            item.source.language.direction
        )
        if (!item.hasMergeConflicts) {
            binding.targetBody?.format(
                typography,
                assetsProvider,
                TranslationType.TARGET,
                item.target.targetLanguage.slug,
                item.target.targetLanguage.direction
            )
            binding.targetEditableBody?.format(
                typography,
                assetsProvider,
                TranslationType.TARGET,
                item.target.targetLanguage.slug,
                item.target.targetLanguage.direction
            )
        } else {
            binding.conflictText?.formatSub(
                typography,
                assetsProvider,
                TranslationType.TARGET,
                item.target.targetLanguage.slug,
                item.target.targetLanguage.direction
            )
        }
        binding.targetTitle.formatSub(
            typography,
            assetsProvider,
            TranslationType.TARGET,
            item.target.targetLanguage.slug,
            item.target.targetLanguage.direction
        )
    }

    fun attachTextChangeListener() {
        binding.targetEditableBody?.let {
            it.removeTextChangedListener(editableTextWatcher)
            it.addTextChangedListener(editableTextWatcher)
        }
    }

    fun removeTextChangeListener() {
        binding.targetEditableBody?.removeTextChangedListener(editableTextWatcher)
    }

    private fun renderSourceCard(item: ReviewListItem) {
        item.renderedSourceText?.let {
            setSource(it)
        } ?: showLoadingSource()

        reviewModeListener?.let { listener ->
            val renderedText = listener.onRenderSourceText(item)
            item.renderedSourceText = renderedText
            setSource(renderedText)

            // update the search
            val position = bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                listener.onSearchItemUpdated(position, binding.sourceBody, false)
            }
        }

        val tabs = item.tabs
        renderSourceTabs(tabs, item.source.slug)

        binding.newTabButton.visibility = if (tabs.size >= MAX_SOURCE_ITEMS) View.GONE else View.VISIBLE
    }

    /**
     * Renders a target card that has merge conflicts
     *
     * @param item the review list item
     */
    private fun renderConflictingTargetCard(item: ReviewListItem) {
        // render title
        binding.targetTitle.text = item.targetTitle
        if (binding.mergeConflictLayout == null) { // sanity check
            return
        }

        displayMergeConflictsOnTargetCard(item)
        rebuildControls(item)

        binding.undoButton?.visibility = View.GONE
        binding.redoButton?.visibility = View.GONE
    }

    /**
     * Renders a normal target card
     *
     * @param item the review list item
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun renderTargetCard(item: ReviewListItem) {
        // Remove text change listener before rendering
        removeTextChangeListener()
        rebuildControls(item)

        // insert rendered text
        if (item.isEditing) {
            // editing mode
            binding.targetEditableBody?.setText(item.renderedTargetText)
        } else {
            // verse marker mode
            binding.targetBody?.let {
                it.setText(item.renderedTargetText)
                ViewUtil.makeLinksClickable(it)
                it.isEnabled = !item.isDisabled
            }
        }

        // title
        binding.targetTitle.text = item.targetTitle

        // render target body
        if (item.renderedTargetText == null) {
            binding.targetEditableBody?.setText(item.targetText)
            binding.targetBody?.setText(item.targetText)

            reviewModeListener?.let { listener ->
                val text = if (item.isComplete || item.isEditing) {
                    listener.onRenderTargetText(this, item, true)
                } else {
                    listener.onRenderTargetText(this, item)
                }
                item.renderedTargetText = text
            }

            if (item.isEditing) {
                // edit mode
                binding.targetEditableBody?.let { body ->
                    body.setText(item.renderedTargetText)
                    reviewModeListener?.let { listener ->
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onSearchItemUpdated(position, body, true)
                        }
                    }
                }
            } else {
                // verse marker mode
                binding.targetBody?.let { body ->
                    body.setText(item.renderedTargetText)
                    reviewModeListener?.let { listener ->
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onSearchItemUpdated(position, body, true)
                        }
                    }
                    body.setOnTouchListener { v, event ->
                        v.onTouchEvent(event)
                        v.clearFocus()
                        true
                    }
                    setFinishedMode(item.isComplete)
                    ViewUtil.makeLinksClickable(body)
                }
            }

            reviewModeListener?.onAddMissingVerses(this)
        } else if (item.isEditing) {
            // editing mode
            binding.targetEditableBody?.let { body ->
                reviewModeListener?.let { listener ->
                    item.renderedTargetText = listener.onRenderTargetText(this, item, true)
                }
                body.setText(item.renderedTargetText)

                if (item.refreshSearchHighlightTarget && reviewModeListener != null) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        reviewModeListener.onSearchItemUpdated(position, body, true)
                    }
                }
            }
        } else {
            // verse marker mode
            binding.targetBody?.let { body ->
                body.setText(item.renderedTargetText)
                ViewUtil.makeLinksClickable(body)

                if (item.refreshSearchHighlightTarget && reviewModeListener != null) {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        reviewModeListener.onSearchItemUpdated(position, body, true)
                    }
                }
            }
        }

        // Reattach text change listener
        attachTextChangeListener()

        // display as finished
        itemView.post { setFinishedMode(item.isComplete) }
    }

    /**
     * Initiates rendering the resource card
     *
     * @param item the review list item
     */
    private fun renderResourceCard(item: ReviewListItem) {
        clearResourceCard()

        // skip if chapter title/reference or udb
        if (!item.isChunk || item.source.resource.slug == "udb") {
            return
        }

        showLoadingResources()

        reviewModeListener?.onRenderHelps(item)
    }

    /**
     * Returns the full width of the resource card
     * @return resource card width
     */
    fun getResourceCardWidth(): Int {
        val rightMargin = (binding.resourceCard.layoutParams as ViewGroup.MarginLayoutParams).rightMargin
        return binding.resourceCard.width + rightMargin
    }

    private fun showLoadingResources() {
        clearHelps()
        binding.resourceTabs.removeAllTabs()

        val layout = RelativeLayout(context)
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleLarge)
        progressBar.isIndeterminate = true
        progressBar.visibility = View.VISIBLE
        val params = RelativeLayout.LayoutParams(100, 100)
        params.addRule(RelativeLayout.CENTER_IN_PARENT)
        layout.addView(progressBar, params)
        binding.resourceList.addView(layout)
    }

    private fun showLoadingSource() {
        binding.sourceBody.text = ""
        binding.sourceBody.visibility = View.GONE
        binding.sourceLoader.visibility = View.VISIBLE
    }

    private fun setSource(sourceText: CharSequence) {
        binding.sourceBody.text = sourceText
        binding.sourceBody.visibility = View.VISIBLE
        binding.sourceLoader.visibility = View.GONE
    }

    fun setResources(
        language: Language,
        notes: List<TranslationHelp>,
        questions: List<TranslationHelp>,
        words: List<Link>
    ) {
        this.notes = notes
        this.questions = questions
        this.words = words
        clearHelps()
        binding.resourceTabs.removeOnTabSelectedListener(resourceTabClickListener)

        // rebuild tabs
        binding.resourceTabs.removeAllTabs()
        if (notes.isNotEmpty()) {
            val tab = binding.resourceTabs.newTab()
            tab.setText(R.string.label_translation_notes)
            tab.tag = TAB_NOTES
            binding.resourceTabs.addTab(tab)
        }
        if (words.isNotEmpty()) {
            val tab = binding.resourceTabs.newTab()
            tab.setText(R.string.translation_words)
            tab.tag = TAB_WORDS
            binding.resourceTabs.addTab(tab)
        }
        if (questions.isNotEmpty()) {
            val tab = binding.resourceTabs.newTab()
            tab.setText(R.string.questions)
            tab.tag = TAB_QUESTIONS
            binding.resourceTabs.addTab(tab)
        }

        // select default tab
        if (binding.resourceTabs.tabCount > 0) {
            val tab = binding.resourceTabs.getTabAt(0)
            if (tab != null) {
                tab.select()
                val tag = tab.tag as Int
                // show the contents
                when (tag) {
                    TAB_NOTES -> showNotes(language)
                    TAB_WORDS -> showWords(language)
                    TAB_QUESTIONS -> showQuestions(language)
                }
            }
        }
        binding.resourceTabs.addOnTabSelectedListener(resourceTabClickListener)
    }

    /**
     * set the UI to reflect the finished mode
     *
     * @param isComplete if the item is complete
     */
    private fun setFinishedMode(isComplete: Boolean) {
        if (isComplete) {
            binding.editButton?.visibility = View.GONE
            binding.undoButton?.visibility = View.GONE
            binding.redoButton?.visibility = View.GONE
            binding.addNoteButton?.visibility = View.GONE
            binding.doneSwitch?.isChecked = true
            binding.targetInnerCard.setBackgroundResource(R.color.card_background_color)
        } else {
            binding.editButton?.visibility = View.VISIBLE
            binding.doneSwitch?.isChecked = false
        }
    }

    /**
     * Removes the tabs and all the loaded resources from the resource tab
     */
    private fun clearResourceCard() {
        clearHelps()
        binding.resourceTabs.removeOnTabSelectedListener(resourceTabClickListener)
        binding.resourceTabs.removeAllTabs()
        notes = ArrayList()
        questions = ArrayList()
        words = ArrayList()
    }

    private fun clearHelps() {
        if (binding.resourceList.childCount > 0) binding.resourceList.removeAllViews()
    }

    /**
     * Displays the notes
     * @param language language
     */
    fun showNotes(language: Language) {
        clearHelps()
        for (note in notes) {
            // TODO: 2/28/17 it would be better if we could build this in code
            val notesBinding = FragmentResourcesListItemBinding.inflate(inflater)
            notesBinding.root.text = note.title
            notesBinding.root.setOnClickListener {
                reviewModeListener?.onNoteClick(note, getResourceCardWidth())
            }
            notesBinding.root.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                language.slug,
                language.direction
            )
            binding.resourceList.addView(notesBinding.root)
        }
    }

    /**
     * Displays the words
     * @param language language
     */
    fun showWords(language: Language) {
        clearHelps()
        for (word in words) {
            val rcSlug = "${language.slug}_${word.project}_${word.resource}"
            val wordsBinding = FragmentResourcesListItemBinding.inflate(inflater)
            wordsBinding.root.text = word.title
            wordsBinding.root.setOnClickListener {
                reviewModeListener?.onWordClick(rcSlug, word, getResourceCardWidth())
            }
            wordsBinding.root.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                language.slug,
                language.direction
            )
            binding.resourceList.addView(wordsBinding.root)
        }
    }

    /**
     * Displays the questions
     * @param language language
     */
    fun showQuestions(language: Language) {
        clearHelps()
        for (question in questions) {
            val questionsBinding = FragmentResourcesListItemBinding.inflate(inflater)
            questionsBinding.root.text = question.title
            questionsBinding.root.setOnClickListener {
                reviewModeListener?.onQuestionClick(question, getResourceCardWidth())
            }
            questionsBinding.root.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                language.slug,
                language.direction
            )
            binding.resourceList.addView(questionsBinding.root)
        }
    }

    /**
     * set up the merge conflicts on the card
     * @param item the review list item
     */
    private fun displayMergeConflictsOnTargetCard(item: ReviewListItem) {
        val language = item.source.language
        item.mergeItems = ParseMergeConflicts.execute(item.targetText)

        if (mergeTexts != null) { // if previously rendered (could be recycled view)
            while (mergeTexts!!.size > item.mergeItems.size) { // if too many items, remove extras
                val lastPosition = mergeTexts!!.size - 1
                val v = mergeTexts!![lastPosition]
                binding.mergeConflictLayout?.removeView(v)
                mergeTexts!!.removeAt(lastPosition)
            }
        } else {
            mergeTexts = ArrayList()
        }

        val tailColor = ContextCompat.getColor(context, R.color.accent_light)

        for (i in item.mergeItems.indices) {
            val createNewCard = i >= mergeTexts!!.size
            var textView: TextView? = null

            if (createNewCard) {
                // create new card
                binding.mergeConflictLayout?.let { layout ->
                    val mergeBinding = FragmentMergeCardBinding.inflate(inflater)
                    textView = mergeBinding.root

                    layout.addView(textView)
                    mergeTexts!!.add(textView)

                    if (i % 2 == 1) { //every other card is different color
                        textView.setBackgroundColor(tailColor)
                    }
                }
            } else {
                textView = mergeTexts!![i] // get previously created card
            }

            if (initialTextSize == 0f && textView != null) { // see if we need to initialize values
                initialTextSize = typography.getFontSize(TranslationType.SOURCE)
                marginInitialLeft = getLeftMargin(textView)
            }

            textView?.let {
                it.format(
                    typography,
                    assetsProvider,
                    TranslationType.SOURCE,
                    language.slug,
                    language.direction
                )

                it.setOnClickListener {
                    item.mergeItemSelected = i
                    reviewModeListener?.let { listener ->
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onNotifyItemChanged(position)
                        }
                    }
                }
            }
        }

        displayMergeConflictSelectionState(item)
    }

    /**
     * set merge conflict selection state
     */
    private fun displayMergeConflictSelectionState(item: ReviewListItem) {
        for (i in item.mergeItems.indices) {
            val mergeConflictCard = item.mergeItems[i]
            val textView = mergeTexts!![i]

            if (item.mergeItemSelected >= 0) {
                if (item.mergeItemSelected == i) {
                    displayMergeSelectionState(MergeConflictDisplayState.SELECTED, textView, mergeConflictCard)
                } else {
                    displayMergeSelectionState(MergeConflictDisplayState.DESELECTED, textView, mergeConflictCard)
                }
                binding.conflictText?.visibility = View.GONE
                binding.buttonBar?.visibility = View.VISIBLE
            } else {
                displayMergeSelectionState(MergeConflictDisplayState.NORMAL, textView, mergeConflictCard)
                binding.conflictText?.visibility = View.VISIBLE
                binding.buttonBar?.visibility = View.GONE
            }
        }
    }

    /**
     * display the selection state for card
     * @param state the merge conflict display state
     * @param view the view to display the state on
     * @param text the text to display
     */
    private fun displayMergeSelectionState(state: MergeConflictDisplayState, view: TextView, text: CharSequence) {
        val span: SpannableStringBuilder

        when (state) {
            MergeConflictDisplayState.SELECTED -> {
                setHorizontalMargin(view, marginInitialLeft) // shrink margins to emphasize
                span = SpannableStringBuilder(text)
                // bold text to emphasize
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, initialTextSize) // grow text to emphasize
                span.setSpan(StyleSpan(Typeface.BOLD), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                view.text = span
            }
            MergeConflictDisplayState.DESELECTED -> {
                setHorizontalMargin(view, 2 * marginInitialLeft) // grow margins to de-emphasize
                span = SpannableStringBuilder(text)
                // set text gray to de-emphasize
                val color = ContextCompat.getColor(context, R.color.dark_disabled_text)
                span.setSpan(ForegroundColorSpan(color), 0, span.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, initialTextSize * 0.8f) // shrink text to de-emphasize
                view.text = span
            }
            MergeConflictDisplayState.NORMAL -> {
                setHorizontalMargin(view, marginInitialLeft) // restore original margins
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, initialTextSize) // restore initial test size
                view.text = text // remove text emphasis
            }
        }
    }

    /**
     * Sets the left and right margins on a view
     *
     * @param view the view to receive the margin
     * @param margin the new margin
     */
    private fun setHorizontalMargin(view: TextView, margin: Int) {
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        params.leftMargin = margin
        params.rightMargin = margin
        view.requestLayout()
    }

    /**
     * get the left margin for view
     * @param v view
     * @return the left margin
     */
    private fun getLeftMargin(v: View): Int {
        val p = v.layoutParams as ViewGroup.MarginLayoutParams
        return p.leftMargin
    }

    /**
     * Shows/hides the resource card
     * @param show will be shown if true
     * @param animate animates the change
     */
    private fun showResourceCard(show: Boolean, animate: Boolean) {
        val openWeight = 1f
        val closedWeight = 0.765f
        if (animate) {
            val duration = 400L
            binding.mainContent.animation?.cancel()
            binding.mainContent.clearAnimation()
            val anim: ObjectAnimator = if (show) {
                binding.resourceLayout.visibility = View.VISIBLE
                ObjectAnimator.ofFloat(binding.mainContent, "weightSum", openWeight, closedWeight)
            } else {
                binding.resourceLayout.visibility = View.INVISIBLE
                ObjectAnimator.ofFloat(binding.mainContent, "weightSum", closedWeight, openWeight)
            }
            anim.duration = duration
            anim.addUpdateListener { binding.mainContent.requestLayout() }
            anim.start()
        } else {
            if (show) {
                binding.resourceLayout.visibility = View.VISIBLE
                binding.mainContent.weightSum = closedWeight
            } else {
                binding.resourceLayout.visibility = View.INVISIBLE
                binding.mainContent.weightSum = openWeight
            }
        }
    }

    private fun renderSourceTabs(tabs: List<ContentValues>, sourceSlug: String) {
        binding.translationTabs.removeOnTabSelectedListener(tabSelectedListener)
        binding.translationTabs.removeAllTabs()

        for (values in tabs) {
            val tag = values.getAsString("tag")
            val title = values.getAsString("title")

            reviewModeListener?.let { listener ->
                val tabLayout = listener.onCreateRemovableTabLayout(tag, title)

                if (tabLayout != null) {
                    val tab = binding.translationTabs.newTab()
                    tab.tag = tag
                    tab.customView = tabLayout
                    binding.translationTabs.addTab(tab)
                }

                listener.onApplyLanguageTypefaceToTab(binding.translationTabs, values, title)
            }
        }

        // open selected tab
        for (i in 0 until binding.translationTabs.tabCount) {
            val tab = binding.translationTabs.getTabAt(i)
            if (sourceSlug == tab?.tag) {
                tab.select()
                break
            }
        }

        // tabs listener
        binding.translationTabs.addOnTabSelectedListener(tabSelectedListener)
    }

    /**
     * get appropriate edit text - it is different when editing versus viewing
     * @return the edit text
     */
    fun getEditText(isEditing: Boolean): EditText? {
        return if (!isEditing) {
            binding.targetBody
        } else {
            binding.targetEditableBody
        }
    }

    /**
     * Sets the correct ui state for translation controls
     */
    fun rebuildControls(item: ReviewListItem) {
        if (item.isEditing) {
            prepareUndoRedoUI(item)

            val allowFootnote = item.targetTranslationFormat == TranslationFormat.USFM && item.isChunk
            binding.editButton?.setImageResource(R.drawable.ic_done_secondary_24dp)
            binding.addNoteButton?.visibility = if (allowFootnote) View.VISIBLE else View.GONE
            binding.undoButton?.visibility = View.GONE
            binding.redoButton?.visibility = View.GONE
            binding.targetBody?.visibility = View.GONE

            binding.targetEditableBody?.let {
                it.visibility = View.VISIBLE
                it.setEnableLines(true)
            }
        } else {
            binding.editButton?.setImageResource(R.drawable.ic_mode_edit_secondary_24dp)
            binding.undoButton?.visibility = View.GONE
            binding.redoButton?.visibility = View.GONE
            binding.addNoteButton?.visibility = View.GONE
            binding.targetBody?.visibility = View.VISIBLE

            binding.targetEditableBody?.let {
                it.visibility = View.GONE
                it.setEnableLines(false)
            }
        }
    }

    /**
     * check history to see if we should show undo/redo buttons
     */
    private fun prepareUndoRedoUI(item: ReviewListItem) {
        val history: FileHistory? = item.fileHistory
        val thread = object : ThreadableUI(context) {
            override fun onStop() {}

            override fun run() {
                try {
                    history?.loadCommits()
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: GitAPIException) {
                    e.printStackTrace()
                }
            }

            override fun onPostExecute() {
                binding.redoButton?.visibility = if (history?.hasNext() == true) View.VISIBLE else View.GONE
                binding.undoButton?.visibility = if (history?.hasPrevious() == true) View.VISIBLE else View.GONE
            }
        }
        thread.start()
    }
}