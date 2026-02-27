package com.door43.translationstudio.ui.translate

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Html
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.door43.data.AssetsProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.FrameTranslation
import com.door43.translationstudio.core.MergeConflictsHandler
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentFootnotePromptBinding
import com.door43.translationstudio.databinding.FragmentReviewListItemBinding
import com.door43.translationstudio.databinding.FragmentReviewListItemMergeConflictBinding
import com.door43.translationstudio.databinding.FragmentVerseMarkerBinding
import com.door43.translationstudio.rendering.ClickableRenderingEngine
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.DefaultRenderer
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.spannables.NoteSpan
import com.door43.translationstudio.ui.spannables.Span
import com.door43.translationstudio.ui.spannables.USFMNoteSpan
import com.door43.translationstudio.ui.spannables.USFMVerseSpan
import com.door43.translationstudio.ui.spannables.VerseSpan
import com.door43.translationstudio.ui.translate.review.OnReviewModeListener
import com.door43.translationstudio.ui.translate.review.ReviewHolder
import com.door43.translationstudio.ui.translate.review.SearchSubject
import com.door43.util.ColorUtil
import com.door43.widget.ViewUtil
import com.google.android.material.tabs.TabLayout
import org.eclipse.jgit.revwalk.RevCommit
import org.unfoldingword.resourcecontainer.Link
import org.unfoldingword.tools.logger.Logger
import org.unfoldingword.tools.taskmanager.ThreadableUI
import java.io.IOException
import java.util.regex.Pattern

open class ReviewModeAdapter(
    openResources: Boolean,
    enableMergeConflictsFilter: Boolean,
    typography: Typography,
    assetsProvider: AssetsProvider,
    renderingProvider: RenderingProvider
) : ViewModeAdapter<ReviewHolder>(), OnReviewModeListener {

    interface OnRenderHelpsListener {
        fun onRenderHelps(item: ListItem)
    }

    interface OnShowToastListener {
        fun onShowToast(message: String)
        fun onShowToast(resId: Int)
    }

    companion object {
        private val TAG = ReviewModeAdapter::class.java.simpleName
        const val HIGHLIGHT_COLOR = Color.YELLOW
        const val VIEW_TYPE_NORMAL = 0
        const val VIEW_TYPE_CONFLICT = 1

        private val USFM_CONSECUTIVE_VERSE_MARKERS =
            Pattern.compile("\\\\v\\s(\\d+(-\\d+)?)\\s*\\\\v\\s(\\d+(-\\d+)?)")

        private val USFM_VERSE_MARKER =
            Pattern.compile(USFMVerseSpan.PATTERN)

        private val CONSECUTIVE_VERSE_MARKERS =
            Pattern.compile("(<verse [^>]+/>\\s*){2}")

        private val VERSE_MARKER =
            Pattern.compile("<verse\\s+number=\"(\\d+)\"[^>]*>")
    }

    private var searchText: CharSequence? = null
    private var searchSubject: SearchSubject? = null
    private var haveMergeConflict = false
    private var mergeConflictFilterOn: Boolean
    private var chunkSearchMatchesCounter = 0
    private var searchPosition = 0
    private var searchSubPositionItems = 0
    private var searchingTarget = true
    private var numberOfChunkMatches = -1
    private var visiblePositions = HashSet<Int>()
    private var mergeConflictSummaryDisplayed = false
    private var resourcesOpened: Boolean

    var renderHelpsListener: OnRenderHelpsListener? = null
    var itemActionListener: OnShowToastListener? = null

    init {
        this.resourcesOpened = openResources
        this.mergeConflictFilterOn = enableMergeConflictsFilter
        this.typography = typography
        this.renderingProvider = renderingProvider
        this.assetsProvider = assetsProvider
    }

    override fun initializeListItems(
        listItems: List<ListItem>,
        startingChapter: String,
        startingChunk: String
    ) {
        super.initializeListItems(listItems, startingChapter, startingChunk)
        setResourcesOpened(resourcesOpened)
        filter(searchText, searchSubject, searchPosition)
        triggerNotifyDataSetChanged()
        updateMergeConflict()
    }

    override fun createListItem(item: ListItem): ReviewListItem {
        return item.toType(::ReviewListItem) as ReviewListItem
    }

    override fun onNoteClick(note: TranslationHelp, resourceCardWidth: Int) {
        onClickListener?.onTranslationNoteClick(note, resourceCardWidth)
    }

    override fun onWordClick(resourceContainerSlug: String, word: Link, resourceCardWidth: Int) {
        onClickListener?.onTranslationWordClick(
            resourceContainerSlug,
            word.chapter,
            resourceCardWidth
        )
    }

    override fun onQuestionClick(question: TranslationHelp, resourceCardWidth: Int) {
        onClickListener?.onTranslationQuestionClick(question, resourceCardWidth)
    }

    override fun onResourceTabNotesSelected(holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            val item = filteredItems[position] as ReviewListItem
            holder.showNotes(item.source.language)
        }
    }

    override fun onResourceTabWordsSelected(holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            val item = filteredItems[position] as ReviewListItem
            holder.showWords(item.source.language)
        }
    }

    override fun onResourceTabQuestionsSelected(holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            val item = filteredItems[position] as ReviewListItem
            holder.showQuestions(item.source.language)
        }
    }

    override fun onSourceTranslationTabClick(sourceTranslationId: String) {
        onClickListener?.onSourceTranslationTabClick(sourceTranslationId)
    }

    override fun onNewSourceTranslationTabClick() {
        onClickListener?.onNewSourceTranslationTabClick()
    }

    override fun onTapResourceCard() {
        //if (!mResourcesOpened) openResources();
    }

    /**
     * check all cards for merge conflicts to see if we should show warning.
     * Runs as background task.
     */
    private fun updateMergeConflict() {
        doCheckForMergeConflict()
    }

    override fun getFocusedChunkSlug(position: Int): String {
        return if (position in 0 until filteredItems.size) {
            filteredItems[position].chunkSlug
        } else ""
    }

    override fun getFocusedChapterSlug(position: Int): String {
        return if (position in 0 until filteredItems.size) {
            filteredItems[position].chapterSlug
        } else ""
    }

    override fun getItemPosition(chapterSlug: String, chunkSlug: String): Int {
        val item = getItem(chapterSlug, chunkSlug)
        return filteredItems.indexOf(item)
    }

    override fun getItem(chapterSlug: String, chunkSlug: String): ListItem? {
        for (item in filteredItems) {
            if (chapterSlug == item.chapterSlug && chunkSlug == item.chunkSlug) {
                return item
            }
        }
        return null
    }

    override fun setResourcesOpened(status: Boolean) {
        resourcesOpened = status
        for (item in items) {
            (item as ReviewListItem).resourcesOpened = status
        }
        triggerNotifyDataSetChanged()
    }

    override fun getItem(position: Int): ReviewListItem? {
        return super.getItem(position) as? ReviewListItem
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (item != null) {
            val conflicted = item.hasMergeConflicts
            if (conflicted) {
                showMergeConflictIcon(true, mergeConflictFilterOn)
                return VIEW_TYPE_CONFLICT
            }
        }
        return VIEW_TYPE_NORMAL
    }

    override fun onCreateManagedViewHolder(parent: ViewGroup, viewType: Int): ReviewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = if (viewType == VIEW_TYPE_CONFLICT) {
            ReviewListItemMergeConflictBinding(
                FragmentReviewListItemMergeConflictBinding.inflate(inflater, parent, false)
            )
        } else {
            ReviewListItemBinding(
                FragmentReviewListItemBinding.inflate(inflater, parent, false)
            )
        }
        return ReviewHolder(binding, typography, assetsProvider, this)
    }

    /**
     * Perform task garbage collection
     *
     * @param range the position that left the screen
     */
    override fun onVisiblePositionsChanged(range: IntArray) {
        // constrain the upper bound
        if (range[1] >= filteredItems.size) range[1] = filteredItems.size - 1
        if (range[0] >= filteredItems.size) range[0] = filteredItems.size - 1

        val visible = HashSet<Int>()
        // record visible positions;
        for (i in range[0] until range[1]) {
            visible.add(i)
        }
        // notify not-visible
        this.visiblePositions.removeAll(visible)
        for (i in this.visiblePositions) {
            // TODO Check if there is a need to cancel render tasks for hidden items
            // runTaskGarbageCollection(i);
        }

        this.visiblePositions = visible
    }

    @Suppress("DEPRECATION")
    override fun markAllChunksDone() {
        AlertDialog.Builder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.project_checklist_title)
            .setMessage(Html.fromHtml(context.getString(R.string.project_checklist_body)))
            .setPositiveButton(R.string.confirm) { _, _ ->
                var marked = 0
                val total = filteredItems.size
                for (item in filteredItems) {
                    try {
                        markChunkCompleted(item, item.target.format)
                        marked++
                    } catch (e: Exception) {
                        val msg = String.format(
                            "There was an error in markAllChunksDone. Translation: " +
                                    "%s, chapter: %s, chunk: %s. Error: %s",
                            item.target.id,
                            item.chapterSlug,
                            item.chunkSlug,
                            e.message
                        )
                        Logger.e(TAG, msg)
                    }
                }

                try {
                    filteredItems[0].target.commit()

                    AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                        .setTitle(R.string.result)
                        .setMessage(String.format(context.getString(R.string.mark_chunks_done_result), marked, total))
                        .setPositiveButton(R.string.label_ok, null)
                        .show()

                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to commit translation of ${filteredItems[0].target.id}", e)
                }

                triggerNotifyDataSetChanged()
            }
            .setNegativeButton(R.string.title_cancel, null)
            .show()
    }

    override fun onBindManagedViewHolder(holder: ReviewHolder, position: Int) {
        val item = filteredItems[position] as ReviewListItem
        holder.bind(item)
    }

    override fun onEditorToggle(holder: ReviewHolder) {
        val handler = Handler(Looper.getMainLooper())

        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ReviewListItem
        item.isEditing = !item.isEditing

        if (item.isEditing) {
            val view = holder.binding.targetEditableBody
            if (view != null) {
                item.renderedTargetText = this.renderTargetText(holder, item, true)
                view.setText(item.renderedTargetText)

                handler.post {
                    onClickListener?.showKeyboard(view)
                    view.requestFocus()
                }
            }
        } else {
            val view = holder.binding.targetBody
            if (view != null) {
                // re-render for verse mode
                item.renderedTargetText = renderTargetText(holder, item)
                view.setText(item.renderedTargetText)

                handler.post {
                    onClickListener?.closeKeyboard()
                    view.requestFocus()
                }
            }
        }

        addMissingVerses(holder)
        holder.rebuildControls(item)
    }

    override fun onApplyChangedText(s: CharSequence, holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ReviewListItem
        applyChangedText(s, item)

        // commit immediately if editing history
        val history = item.fileHistory
        if (history != null && !history.isAtHead) {
            history.reset()
            holder.rebuildControls(item)
        }
    }

    override fun onUndoTextInTarget(holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ReviewListItem
        undoTextInTarget(holder, item)
    }

    override fun onRedoTextInTarget(holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ReviewListItem
        redoTextInTarget(holder, item)
    }

    @Suppress("DEPRECATION")
    override fun onDoneSwitchClicked(holder: ReviewHolder, checked: Boolean) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ReviewListItem
        if (checked) {
            if (item.isEditing && holder.binding.targetEditableBody != null) {
                // make sure to capture verse marker changes before dialog is displayed
                val changes: Editable? = holder.binding.targetEditableBody?.text
                item.renderedTargetText = changes
                if (changes != null) {
                    item.targetText = Translator.compileTranslation(changes)
                }
            }

            AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(R.string.chunk_checklist_title)
                .setMessage(Html.fromHtml(context.getString(R.string.chunk_checklist_body)))
                .setPositiveButton(R.string.confirm) { _, _ ->
                    try {
                        markChunkCompleted(item, item.target.format)
                        item.target.commit()
                    } catch (e: Exception) {
                        Logger.e(TAG, "Failed to commit translation of ${item.target.id}", e)
                        itemActionListener?.onShowToast(e.message ?: "Error")
                    }
                    triggerNotifyDataSetChanged()
                }
                .setNegativeButton(R.string.title_cancel) { _, _ ->
                    // off if not accepted
                    holder.binding.doneSwitch?.isChecked = false // force back
                }
                .show()
        } else {
            reOpenItem(item)
        }
    }

    override fun onCreateFootnoteAtSelection(holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ReviewListItem
        createFootnoteAtSelection(holder, item)
    }

    /**
     * Generate spannable for source text. Will add click listener for notes if supported
     */
    override fun onRenderSourceText(item: ReviewListItem): CharSequence {
        val renderingGroup = RenderingGroup()
        val enableSearch = searchText != null && searchSubject == SearchSubject.SOURCE

        if (Clickables.isClickableFormat(item.sourceTranslationFormat)) {
            val noteClickListener = object : Span.OnClickListener {
                override fun onClick(view: View, span: Span, start: Int, end: Int) {
                    if (span is NoteSpan) {
                        onSourceFootnoteClick(item, span, start, end)
                    }
                }

                override fun onLongClick(view: View, span: Span, start: Int, end: Int) {}
            }
            renderingProvider.setupRenderingGroup(
                item.sourceTranslationFormat,
                renderingGroup,
                null,
                noteClickListener,
                false
            )
        } else {
            renderingGroup.addEngine(DefaultRenderer(null))
        }

        if (enableSearch) {
            renderingGroup.setSearchString(searchText, HIGHLIGHT_COLOR)
        }

        renderingGroup.init(item.sourceText)
        val results = renderingGroup.start()
        item.hasMissingVerses = renderingGroup.isAddedMissingVerse
        return results ?: ""
    }

    override fun onSearchItemUpdated(position: Int, view: TextView, isTarget: Boolean) {
        val item = filteredItems[position] as ReviewListItem
        val selectPosition = checkForSelectedSearchItem(item, position, isTarget)

        if (isTarget) {
            item.refreshSearchHighlightTarget = false
            selectCurrentSearchItem(position, selectPosition, view)
        } else {
            item.refreshSearchHighlightSource = false
            selectCurrentSearchItem(position, selectPosition, view)
        }
    }

    override fun onMergeConflictItemCancel(position: Int) {
        val item = filteredItems[position] as ReviewListItem
        item.mergeItemSelected = -1
        notifyItemChanged(position)
    }

    override fun onMergeConflictItemConfirm(position: Int) {
        val item = filteredItems[position] as ReviewListItem
        if (item.mergeItemSelected >= 0 && item.mergeItemSelected < item.mergeItems.size) {
            val selectedText = item.mergeItems[item.mergeItemSelected]
            applyNewCompiledText(selectedText.toString(), item)
            item.targetText = selectedText.toString()
            reOpenItem(item)
            item.hasMergeConflicts = MergeConflictsHandler.isMergeConflicted(selectedText)
            item.mergeItemSelected = -1
            item.isEditing = false

            // if in merge conflict mode and merge conflicts resolved, remove item
            if (!item.hasMergeConflicts && mergeConflictFilterOn) {
                filteredItems.remove(item)
            }
            notifyItemChanged(position)
            updateMergeConflict()
        }
    }

    override fun onRenderTargetText(holder: ReviewHolder, item: ReviewListItem, editable: Boolean): CharSequence {
        return renderTargetText(holder, item, editable)
    }

    override fun onRenderTargetText(holder: ReviewHolder, item: ReviewListItem): CharSequence {
        return renderTargetText(holder, item)
    }

    override fun onAddMissingVerses(holder: ReviewHolder) {
        addMissingVerses(holder)
    }

    override fun onRenderHelps(item: ReviewListItem) {
        if (item.resourcesOpened) {
            renderHelpsListener?.onRenderHelps(item)
        }
    }

    /**
     * if missing verses were found during render, then add them
     */
    private fun addMissingVerses(holder: ReviewHolder) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ReviewListItem
        if (item.hasMissingVerses && !item.isComplete) {
            Log.i(TAG, "Adding Missing verses to: " + item.targetText)
            if (item.targetText.isNotEmpty()) {
                val translation = applyChangedText(item.renderedTargetText, item)
                Log.i(TAG, "Added Missing verses: $translation")
                item.hasMissingVerses = false
                item.renderedTargetText = null // force re-rendering of target text

                holder.itemView.post { notifyItemChanged(position) }
            }
        }
    }

    /**
     * check if we have a selected search item in this chunk
     */
    private fun checkForSelectedSearchItem(item: ReviewListItem, position: Int, target: Boolean): Int {
        var selectPosition = -1
        if (item.hasSearchText && position == searchPosition) {
            if (searchSubPositionItems < 0) { // if we haven't counted items yet
                findSearchItemInChunkAndPreselect(item, target)
                Log.i(TAG, "Re-rendering, Found search items in chunk $position: $searchSubPositionItems")
            } else if (searchSubPositionItems > 0) { // if we have counted items then find the number selected
                val searchSubPos = 0
                val results = getMatchItemN(item, searchText, searchSubPos, target)
                if (results.foundLocation >= 0) {
                    Log.i(TAG, "Highlight at position: $position : ${results.foundLocation}")
                    selectPosition = results.foundLocation
                } else {
                    Log.i(TAG, "Highlight failed for position: $position; chunk position: $searchSubPos; chunk count: $searchSubPositionItems")
                }
                checkIfAtSearchLimits()
            }
        }
        return selectPosition
    }

    /**
     * highlight the current selected search text item at position
     */
    private fun selectCurrentSearchItem(position: Int, selectPosition: Int, view: TextView) {
        if (selectPosition >= 0) {
            val layout: Layout? = view.layout
            if (layout != null) {
                val lineNumberForLocation = layout.getLineForOffset(selectPosition)
                val baseline = layout.getLineBaseline(lineNumberForLocation)
                val ascent = layout.getLineAscent(lineNumberForLocation)

                val verticalOffset = baseline + ascent
                Log.i(TAG, "set position for $selectPosition, scroll to y=$verticalOffset")

                val hand = Handler(Looper.getMainLooper())
                hand.post {
                    Log.i(TAG, "selectCurrentSearchItem position= $position, offset=${-verticalOffset}")
                    onSetSelectedPosition(position, -verticalOffset)
                }
            } else {
                Logger.e(TAG, "cannot get layout for position: $position")
            }
        }
    }

    /**
     * mark item as not done
     */
    private fun reOpenItem(item: ListItem) {
        val opened = when {
            item.isChapterReference -> item.target.reopenChapterReference(item.chapterSlug)
            item.isChapterTitle -> item.target.reopenChapterTitle(item.chapterSlug)
            item.isProjectTitle -> item.target.openProjectTitle()
            else -> item.target.reopenFrame(item.chapterSlug, item.chunkSlug)
        }

        if (opened) {
            (item as ReviewListItem).renderedTargetText = null
            item.isComplete = false
            triggerNotifyItemChanged(filteredItems.indexOf(item))
        }
    }

    /**
     * create a new footnote at selected position in target text.
     */
    private fun createFootnoteAtSelection(holder: ReviewHolder, item: ReviewListItem) {
        val editText = holder.getEditText(item.isEditing) ?: return
        var endPos = editText.selectionEnd
        if (endPos < 0) {
            endPos = 0
        }
        val insertPos = endPos
        editFootnote("", holder, item, insertPos, insertPos)
    }

    /**
     * edit contents of footnote at specified position
     */
    private fun editFootnote(
        initialNote: CharSequence,
        holder: ReviewHolder,
        item: ReviewListItem,
        footnotePos: Int,
        footnoteEndPos: Int
    ) {
        val editText = holder.getEditText(item.isEditing) ?: return
        val original = editText.text

        val inflater = LayoutInflater.from(context)
        val footnoteBinding = FragmentFootnotePromptBinding.inflate(inflater)

        footnoteBinding.footnoteText.setText(initialNote)

        // pop up note prompt
        AlertDialog.Builder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.title_add_footnote)
            .setPositiveButton(R.string.label_ok) { dialog, _ ->
                val footnote = footnoteBinding.footnoteText.text
                val validated = verifyAndReplaceFootnote(
                    footnote,
                    original,
                    footnotePos,
                    footnoteEndPos,
                    holder,
                    item,
                    editText
                )
                if (validated) {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.title_cancel) { dialog, _ -> dialog.dismiss() }
            .setView(footnoteBinding.root)
            .show()
    }

    /**
     * insert footnote into EditText or remove footnote from EditText
     */
    private fun verifyAndReplaceFootnote(
        footnote: CharSequence?,
        original: CharSequence,
        insertPos: Int,
        insertEndPos: Int,
        holder: ReviewHolder,
        item: ReviewListItem,
        editText: EditText
    ): Boolean {
        // sanity checks
        if (footnote.isNullOrEmpty()) {
            warnDialog(R.string.title_footnote_invalid, R.string.footnote_message_empty)
            return false
        }

        placeFootnote(footnote, original, insertPos, insertEndPos, holder, item, editText)
        return true
    }

    /**
     * display warning dialog
     */
    private fun warnDialog(titleID: Int, messageID: Int) {
        AlertDialog.Builder(context, R.style.AppTheme_Dialog)
            .setTitle(titleID)
            .setMessage(messageID)
            .setPositiveButton(R.string.dismiss, null)
            .show()
    }

    /**
     * insert footnote into EditText or remove footnote from EditText
     */
    private fun placeFootnote(
        footnote: CharSequence?,
        original: CharSequence,
        start: Int,
        end: Int,
        holder: ReviewHolder,
        item: ReviewListItem,
        editText: EditText
    ) {
        var footnotecode: CharSequence = ""
        var actualFootnote = footnote
        if (actualFootnote != null) {
            // sanity checks
            if (actualFootnote.isEmpty()) {
                actualFootnote = context.resources.getString(R.string.footnote_label)
            }

            val footnoteSpannable = USFMNoteSpan.generateFootnote(actualFootnote)
            footnotecode = footnoteSpannable.machineReadable
        }

        val newText = TextUtils.concat(
            original.subSequence(0, start),
            footnotecode,
            original.subSequence(end, original.length)
        )
        editText.setText(newText)

        item.renderedTargetText = newText
        item.targetText = Translator.compileTranslation(editText.text) // get XML for footnote
        item.target.applyFrameTranslation(item.ft, item.targetText) // save change

        // generate spannable again adding
        if (item.isComplete || item.isEditing) {
            item.renderedTargetText = this.renderTargetText(holder, item, true)
        } else {
            item.renderedTargetText = renderTargetText(
                item.targetText,
                item.targetTranslationFormat,
                item.ft,
                holder,
                item
            )
        }
        editText.setText(item.renderedTargetText)
        editText.setSelection(editText.length(), editText.length())
    }

    /**
     * save changed text to item,  first see if it needs to be compiled
     */
    private fun applyChangedText(s: CharSequence?, item: ReviewListItem): String? {
        val translation: String = when (s) {
            null -> return null
            is Editable -> Translator.compileTranslation(s)
            is SpannedString -> Translator.compileTranslationSpanned(s)
            else -> s.toString()
        }

        applyNewCompiledText(translation, item)
        return translation
    }

    /**
     * save new text to item
     */
    private fun applyNewCompiledText(translation: String, item: ListItem) {
        val cleanTranslation = translation.replace("\\s*\\R\\s*".toRegex(), "\n")

        item.targetText = cleanTranslation
        when {
            item.isChapterReference -> item.target.applyChapterReferenceTranslation(item.ct, cleanTranslation)
            item.isChapterTitle -> item.target.applyChapterTitleTranslation(item.ct, cleanTranslation)
            item.isProjectTitle -> {
                try {
                    item.target.applyProjectTitleTranslation(cleanTranslation)
                } catch (e: IOException) {
                    Logger.e(ReviewModeAdapter::class.java.name, "Failed to save the project title translation", e)
                }
            }
            item.isChunk -> item.target.applyFrameTranslation(item.ft, cleanTranslation)
        }
    }

    /**
     * restore the text from previous commit for fragment
     */
    private fun undoTextInTarget(holder: ReviewHolder, item: ReviewListItem) {
        holder.binding.undoButton?.visibility = View.INVISIBLE
        holder.binding.redoButton?.visibility = View.INVISIBLE

        val history = item.fileHistory
        val thread = object : ThreadableUI(context) {
            var commit: RevCommit? = null

            override fun onStop() {}

            override fun run() {
                // commit changes before viewing history
                if (history != null) {
                    if (history.isAtHead) {
                        if (!item.target.isClean) {
                            try {
                                item.target.commitSync()
                                history.loadCommits()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    // get previous
                    commit = history.previous()
                }
            }

            override fun onPostExecute() {
                if (history != null) {
                    if (commit != null) {
                        var text: String? = null
                        try {
                            text = history.read(commit)
                        } catch (e: IllegalStateException) {
                            Logger.w(TAG, "Undo is past end of history for specific file", e)
                            text = "" // graceful recovery
                        } catch (e: Exception) {
                            Logger.w(TAG, "Undo Read Exception", e)
                        }

                        // save and update ui
                        if (text != null) {
                            // TRICKY: prevent history from getting rolled back soon after the user views it
                            restartAutoCommitTimer()
                            applyChangedText(text, item)

                            onClickListener?.closeKeyboard()
                            item.hasMergeConflicts = MergeConflictsHandler.isMergeConflicted(text)
                            triggerNotifyDataSetChanged()
                            updateMergeConflict()

                            holder.binding.targetEditableBody?.let {
                                holder.removeTextChangeListener()
                                it.setText(item.renderedTargetText)
                                holder.attachTextChangeListener()
                            }
                        }
                    }

                    if (holder.binding.redoButton != null && holder.binding.undoButton != null) {
                        holder.binding.redoButton?.visibility = if (history.hasNext()) View.VISIBLE else View.GONE
                        holder.binding.undoButton?.visibility = if (history.hasPrevious()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
        thread.start()
    }

    /**
     * restore the text from later commit for fragment
     */
    private fun redoTextInTarget(holder: ReviewHolder, item: ReviewListItem) {
        holder.binding.undoButton?.visibility = View.INVISIBLE
        holder.binding.redoButton?.visibility = View.INVISIBLE

        val history = item.fileHistory
        val thread = object : ThreadableUI(context) {
            var commit: RevCommit? = null

            override fun onStop() {}

            override fun run() {
                if (history != null) {
                    commit = history.next()
                }
            }

            override fun onPostExecute() {
                if (history != null) {
                    if (commit != null) {
                        var text: String? = null
                        try {
                            text = history.read(commit)
                        } catch (e: IllegalStateException) {
                            Logger.w(TAG, "Redo is past end of history for specific file", e)
                            text = "" // graceful recovery
                        } catch (e: Exception) {
                            Logger.w(TAG, "Redo Read Exception", e)
                        }

                        // save and update ui
                        if (text != null) {
                            // TRICKY: prevent history from getting rolled back soon after the user views it
                            restartAutoCommitTimer()
                            applyChangedText(text, item)

                            onClickListener?.closeKeyboard()
                            item.hasMergeConflicts = MergeConflictsHandler.isMergeConflicted(text)
                            triggerNotifyDataSetChanged()
                            updateMergeConflict()

                            holder.binding.targetEditableBody?.let {
                                holder.removeTextChangeListener()
                                it.setText(item.renderedTargetText)
                                holder.attachTextChangeListener()
                            }
                        }
                    }

                    if (holder.binding.redoButton != null && holder.binding.undoButton != null) {
                        holder.binding.redoButton?.visibility = if (history.hasNext()) View.VISIBLE else View.GONE
                        holder.binding.undoButton?.visibility = if (history.hasPrevious()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
        thread.start()
    }

    /**
     * Performs some validation, and commits changes if ready.
     *
     * @throws IllegalStateException If there is an error with the chunk
     */
    private fun markChunkCompleted(item: ListItem, format: TranslationFormat) {
        // Check for empty translation.
        if (item.targetText.isEmpty()) {
            throw java.lang.IllegalStateException(context.getString(R.string.translate_first))
        }

        var lowVerse = -1
        var highVerse = 999999999
        val range = RenderingProvider.getVerseRange(item.targetText, item.targetTranslationFormat)
        if (range.isNotEmpty()) {
            lowVerse = range[0]
            highVerse = lowVerse
            if (range.size > 1) {
                highVerse = range[1]
            }
        }

        // Check for contiguous verse numbers.
        var matcher = if (format == TranslationFormat.USFM) {
            USFM_CONSECUTIVE_VERSE_MARKERS.matcher(item.targetText)
        } else {
            CONSECUTIVE_VERSE_MARKERS.matcher(item.targetText)
        }
        if (matcher.find()) {
            throw java.lang.IllegalStateException(context.getString(R.string.consecutive_verse_markers))
        }

        // check for invalid verse markers
        var error = 0
        matcher = if (format == TranslationFormat.USFM) {
            USFM_VERSE_MARKER.matcher(item.targetText)
        } else {
            VERSE_MARKER.matcher(item.targetText)
        }
        val sourceVerseRange = RenderingProvider.getVerseRange(item.sourceText, item.sourceTranslationFormat)
        if (sourceVerseRange.isNotEmpty()) {
            val min = sourceVerseRange[0]
            var max = min
            if (sourceVerseRange.size == 2) max = sourceVerseRange[1]
            while (matcher.find()) {
                val verseStr = matcher.group(1)
                var verse = -1
                if (verseStr != null) {
                    try {
                        verse = verseStr.toInt()
                    } catch (ignored: Exception) {}
                }
                if (verse < min || verse > max) {
                    error = R.string.outofrange_verse_marker
                    break
                }
            }
        }
        if (error > 0) {
            throw java.lang.IllegalStateException(context.getString(error))
        }

        // Check for out-of-order verse markers.
        matcher = if (format == TranslationFormat.USFM) {
            USFM_VERSE_MARKER.matcher(item.targetText)
        } else {
            VERSE_MARKER.matcher(item.targetText)
        }
        var lastVerseSeen = 0
        while (matcher.find()) {
            val verseStr = matcher.group(1)
            var currentVerse = -1
            if (verseStr != null) {
                try {
                    currentVerse = verseStr.toInt()
                } catch (ignored: Exception) {}
            }
            if (currentVerse <= lastVerseSeen) {
                error = if (currentVerse == lastVerseSeen) {
                    R.string.duplicate_verse_marker
                } else {
                    R.string.outoforder_verse_markers
                }
                break
            } else if (currentVerse < lowVerse || currentVerse > highVerse) {
                error = R.string.outofrange_verse_marker
                break
            } else {
                lastVerseSeen = currentVerse
            }
        }
        if (error > 0) {
            throw java.lang.IllegalStateException(context.getString(error))
        }

        // Everything looks good so far.
        val success = when {
            item.isChapterReference -> item.target.finishChapterReference(item.chapterSlug)
            item.isChapterTitle -> item.target.finishChapterTitle(item.chapterSlug)
            item.isProjectTitle -> item.target.closeProjectTitle()
            else -> item.target.finishFrame(item.chapterSlug, item.chunkSlug)
        }

        if (!success) {
            throw java.lang.IllegalStateException(context.getString(R.string.failed_to_commit_chunk))
        } else {
            item.isComplete = true
        }

        (item as ReviewListItem).isEditing = false
        item.renderedTargetText = null
    }

    private fun renderTargetText(holder: ReviewHolder, item: ReviewListItem): CharSequence {
        return renderTargetText(
            item.targetText,
            item.targetTranslationFormat,
            item.ft,
            holder,
            item
        )
    }

    /**
     * generate spannable for target text.  Will add click listener for notes and verses if they
     * are supported
     */
    @SuppressLint("SetTextI18n")
    private fun renderTargetText(
        text: String?,
        format: TranslationFormat,
        frameTranslation: FrameTranslation,
        holder: ReviewHolder?,
        item: ReviewListItem
    ): CharSequence {
        val renderingGroup = RenderingGroup()
        val enableSearch = searchText != null && searchSubject != null && searchSubject == SearchSubject.TARGET

        if (Clickables.isClickableFormat(format)) {
            val verseClickListener = object : Span.OnClickListener {
                override fun onClick(view: View, span: Span, start: Int, end: Int) {
                    itemActionListener?.onShowToast(R.string.long_click_to_drag)
                }

                override fun onLongClick(view: View, span: Span, start: Int, end: Int) {
                    toggleDisableItems(true, item)

                    val dragData = ClipData.newPlainText(
                        "${item.chapterSlug}-${item.chunkSlug}",
                        span.machineReadable
                    )
                    val pin = span as VerseSpan

                    // create drag shadow
                    val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                    val markerBinding = FragmentVerseMarkerBinding.inflate(inflater)

                    if (pin.endVerseNumber > 0) {
                        markerBinding.verse.text = "${pin.startVerseNumber}-${pin.endVerseNumber}"
                    } else {
                        markerBinding.verse.text = "${pin.startVerseNumber}"
                    }
                    val shadow = ViewUtil.convertToBitmap(markerBinding.root)
                    val myShadow = CustomDragShadowBuilder.fromBitmap(context, shadow)

                    val spanRange = intArrayOf(start, end)
                    @Suppress("DEPRECATION")
                    view.startDrag(
                        dragData,  // the data to be dragged
                        myShadow,  // the drag shadow builder
                        spanRange, // local state data
                        0          // flags
                    )

                    view.setOnDragListener(object : View.OnDragListener {
                        private var hasEntered = false

                        override fun onDrag(v: View, e: DragEvent): Boolean {
                            val editText = v as EditText
                            when (e.action) {
                                DragEvent.ACTION_DRAG_STARTED -> {
                                    // delete old span
                                    val localSpanRange = e.localState as? IntArray
                                    if (localSpanRange != null && localSpanRange.size >= 2) {
                                        val input = editText.text
                                        if (localSpanRange[0] < input.length && localSpanRange[1] < input.length) {
                                            val out = TextUtils.concat(
                                                input.subSequence(0, localSpanRange[0]),
                                                input.subSequence(localSpanRange[1], input.length)
                                            )
                                            editText.setText(out)
                                        }
                                    }
                                }
                                DragEvent.ACTION_DROP -> {
                                    var offset = editText.getOffsetForPosition(e.x, e.y)
                                    var currentText: CharSequence = editText.text
                                    offset = closestSpotForVerseMarker(offset, currentText)

                                    currentText = if (offset >= 0) {
                                        // insert the verse at the offset
                                        TextUtils.concat(
                                            currentText.subSequence(0, offset),
                                            pin.toCharSequence(context),
                                            currentText.subSequence(offset, currentText.length)
                                        )
                                    } else {
                                        // place the verse back at the beginning
                                        TextUtils.concat(pin.toCharSequence(context), currentText)
                                    }

                                    val noHighlightText = resetHighlightColor(currentText)
                                    editText.setText(noHighlightText)

                                    val translation = Translator.compileTranslation(editText.text)
                                    item.target.applyFrameTranslation(frameTranslation, translation)
                                    item.targetText = translation
                                    item.renderedTargetText = renderTargetText(
                                        translation,
                                        item.targetTranslationFormat,
                                        frameTranslation,
                                        holder,
                                        item
                                    )
                                }
                                DragEvent.ACTION_DRAG_ENDED -> {
                                    toggleDisableItems(false, null)
                                    v.setOnDragListener(null)
                                    editText.setSelection(editText.selectionEnd)
                                    // reset verse if dragged off the view
                                    if (!hasEntered) {
                                        // place the verse back at the beginning
                                        var currentText: CharSequence = editText.text
                                        currentText = TextUtils.concat(pin.toCharSequence(context), currentText)
                                        editText.setText(currentText)
                                        val translation = Translator.compileTranslation(editText.text)
                                        item.target.applyFrameTranslation(frameTranslation, translation)
                                        item.renderedTargetText = renderTargetText(
                                            translation,
                                            item.targetTranslationFormat,
                                            frameTranslation,
                                            holder,
                                            item
                                        )
                                    }
                                }
                                DragEvent.ACTION_DRAG_ENTERED -> {
                                    hasEntered = true
                                }
                                DragEvent.ACTION_DRAG_EXITED -> {
                                    hasEntered = false
                                    editText.setSelection(editText.selectionEnd)
                                    val noHighlightText = resetHighlightColor(editText.text)
                                    editText.setText(noHighlightText)
                                }
                                DragEvent.ACTION_DRAG_LOCATION -> {
                                    val offset = editText.getOffsetForPosition(e.x, e.y)
                                    if (offset >= 0 && offset < editText.text.length - 1) {
                                        val txt = editText.text
                                        val str = highlightWordAt(offset, txt)
                                        editText.setText(str)
                                    } else {
                                        editText.setSelection(editText.selectionEnd)
                                    }
                                }
                            }
                            return true
                        }
                    })
                }
            }

            val noteClickListener = object : Span.OnClickListener {
                override fun onClick(view: View, span: Span, start: Int, end: Int) {
                    if (span is NoteSpan) {
                        holder?.let {
                            showFootnote(it, item, span, start, end, true)
                        }
                    }
                }

                override fun onLongClick(view: View, span: Span, start: Int, end: Int) {}
            }

            val renderer = renderingProvider.setupRenderingGroup(
                format,
                renderingGroup,
                verseClickListener,
                noteClickListener,
                true
            ) as ClickableRenderingEngine

            val verseRange = RenderingProvider.getVerseRange(
                item.sourceText,
                item.sourceTranslationFormat
            )
            renderer.setPopulateVerseMarkers(verseRange)
        } else {
            renderingGroup.addEngine(DefaultRenderer(null))
        }

        if (enableSearch) {
            renderingGroup.setSearchString(searchText, HIGHLIGHT_COLOR)
        }

        if (!text.isNullOrBlank()) {
            renderingGroup.init(text)
            val results = renderingGroup.start()
            item.hasMissingVerses = renderingGroup.isAddedMissingVerse
            return results ?: ""
        } else {
            return ""
        }
    }

    /**
     * Find the closest position to drop verse marker. Weighted toward beginning of word.
     */
    private fun closestSpotForVerseMarker(offset: Int, text: CharSequence): Int {
        var currentOffset = offset
        if (currentOffset <= 0) {
            return 0
        }

        if (currentOffset >= text.length) {
            currentOffset = text.length - 1
        }

        while (currentOffset > 0 && isWhitespace(text[currentOffset])) {
            currentOffset--
        }

        while (currentOffset > 0 && !isWhitespace(text[currentOffset])) {
            currentOffset--
        }

        while (currentOffset > 0 && isFootNote(text, currentOffset)) {
            currentOffset--
        }

        return if (currentOffset > 0) currentOffset + 1 else currentOffset
    }

    private fun isFootNote(text: CharSequence, offset: Int): Boolean {
        val pattern = Pattern.compile(USFMNoteSpan.PATTERN)
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            if (offset >= matcher.start() && offset < matcher.end()) {
                return true
            }
        }
        return false
    }

    private fun isWhitespace(c: Char): Boolean {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r'
    }

    private fun resetHighlightColor(text: CharSequence): SpannableString {
        val noHighlightText = SpannableString(text)
        val background = BackgroundColorSpan(Color.TRANSPARENT)
        val foreground = ForegroundColorSpan(
            ColorUtil.getColor(context, R.color.dark_primary_text)
        )

        noHighlightText.setSpan(
            background,
            0,
            text.length,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE
        )
        noHighlightText.setSpan(
            foreground,
            0,
            text.length,
            0
        )
        return noHighlightText
    }

    /**
     * Highlights one word based on the given position (index) of the original string.
     */
    private fun highlightWordAt(position: Int, text: CharSequence): SpannableString {
        val start = closestSpotForVerseMarker(position, text)
        var end = start + 1
        // move end position toward the end of word (if currently not)
        while (end < text.length && !isWhitespace(text[end])) {
            end++
        }
        val str = resetHighlightColor(text)
        val bgColor = BackgroundColorSpan(
            ColorUtil.getColor(context, R.color.highlight_background_color)
        )
        str.setSpan(bgColor, start, end, 0)
        val fgColor = ForegroundColorSpan(
            ColorUtil.getColor(context, R.color.highlighted_foreground_color)
        )
        str.setSpan(fgColor, start, end, 0)
        return str
    }

    /**
     * display selected footnote in dialog.
     */
    private fun showFootnote(
        holder: ReviewHolder,
        item: ReviewListItem,
        span: NoteSpan,
        start: Int,
        end: Int,
        editable: Boolean
    ) {
        val marker = span.passage
        var title: CharSequence = context.resources.getText(R.string.title_footnote)
        if (marker.toString().isNotEmpty()) {
            title = "$title: $marker"
        }
        val message = span.notes

        if (editable && !item.isComplete) {
            AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dismiss, null)
                .setNeutralButton(R.string.edit) { _, _ -> editFootnote(span.notes, holder, item, start, end) }
                .setNegativeButton(R.string.label_delete) { _, _ -> deleteFootnote(span.notes, holder, item, start, end) }
                .show()

        } else {
            AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dismiss, null)
                .show()
        }
    }

    /**
     * prompt to confirm removal of specific footnote at position
     */
    private fun deleteFootnote(
        note: CharSequence,
        holder: ReviewHolder,
        item: ReviewListItem,
        start: Int,
        end: Int
    ) {
        val editText = holder.getEditText(item.isEditing) ?: return
        val original = editText.text

        AlertDialog.Builder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.footnote_confirm_delete)
            .setMessage(note)
            .setPositiveButton(R.string.label_delete) { _, _ ->
                placeFootnote(null, original, start, end, holder, item, editText)
            }
            .setNegativeButton(R.string.title_cancel, null)
            .show()
    }

    /**
     * generate spannable for source text.  Will add click listener for notes if supported
     */
    private fun renderTargetText(
        holder: ReviewHolder,
        item: ReviewListItem,
        editable: Boolean
    ): CharSequence {
        val renderingGroup = RenderingGroup()
        var enableSearch = searchText != null && searchSubject != null
        if (editable) {
            // make sure we are searching target
            enableSearch = enableSearch && searchSubject == SearchSubject.TARGET
        }
        if (Clickables.isClickableFormat(item.targetTranslationFormat)) {
            val noteClickListener = object : Span.OnClickListener {
                override fun onClick(view: View, span: Span, start: Int, end: Int) {
                    if (span is NoteSpan) {
                        showFootnote(holder, item, span, start, end, editable)
                    }
                }

                override fun onLongClick(view: View, span: Span, start: Int, end: Int) {}
            }

            renderingProvider.setupRenderingGroup(
                item.targetTranslationFormat,
                renderingGroup,
                null,
                noteClickListener,
                false
            )

            if (editable) {
                if (!item.isComplete) {
                    renderingGroup.setVersesEnabled(false)
                    renderingGroup.setParagraphsEnabled(false)
                }
            }
        } else {
            renderingGroup.addEngine(DefaultRenderer(null))
        }

        if (enableSearch) {
            renderingGroup.setSearchString(searchText, HIGHLIGHT_COLOR)
        }

        renderingGroup.init(item.targetText)
        val results = renderingGroup.start()
        item.hasMissingVerses = renderingGroup.isAddedMissingVerse
        return results ?: ""
    }

    override fun getItemCount(): Int {
        return filteredItems.size
    }

    /**
     * show or hide the merge conflict icon
     */
    private fun showMergeConflictIcon(
        showMergeConflict: Boolean,
        mergeConflictFilterMode: Boolean
    ) {
        val mergeConflictFilterEnabled = showMergeConflict && mergeConflictFilterMode
        if (showMergeConflict != haveMergeConflict || mergeConflictFilterEnabled != mergeConflictFilterOn) {
            val hand = Handler(Looper.getMainLooper())
            hand.post {
                onClickListener?.onEnableMergeConflict(showMergeConflict, mergeConflictFilterEnabled)
            }
        }
        haveMergeConflict = showMergeConflict
        mergeConflictFilterOn = mergeConflictFilterEnabled
    }

    override fun getSections(): Array<Any> {
        return filteredChapters.toTypedArray()
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        // not used
        return sectionIndex
    }

    override fun getSectionForPosition(position: Int): Int {
        if (position in 0 until filteredItems.size) {
            val item = filteredItems[position]
            return filteredChapters.indexOf(item.chapterSlug)
        }
        return -1
    }

    override fun onSourceFootnoteClick(item: ReviewListItem, span: NoteSpan, start: Int, end: Int) {
        val position = filteredItems.indexOf(item)
        if (onClickListener == null) return
        val holder = onClickListener?.getVisibleViewHolder(position) as? ReviewHolder ?: return
        showFootnote(holder, item, span, start, end, false)
    }

    /**
     * for returning multiple values in the text search results
     */
    private data class MatchResults(
        val foundLocation: Int,
        val numberFound: Int,
        val needRender: Boolean
    )

    /**
     * move to next (forward/previous) search item.
     */
    override fun onMoveSearch(next: Boolean) {
        Log.i(TAG, "onMoveSearch position $searchPosition forward=$next")

        val foundPos = findNextMatchChunk(next)
        if (foundPos >= 0) {
            Log.i(TAG, "onMoveSearch foundPos=$foundPos")
            searchPosition = foundPos
            searchSubPositionItems = -1

            onSearching(doingSearch = false, numberOfChunkMatches = numberOfChunkMatches, atEnd = false, atStart = false)

            val item = getItem(searchPosition)
            if (item != null) {
                findSearchItemInChunkAndPreselect(item, searchingTarget)
            }

            Log.i(TAG, "onMoveSearch position=$foundPos")
            onClickListener?.onSetSelectedPosition(foundPos, 0) // coarse scrolling
        } else { // not found, clear last selection
            Log.i(TAG, "onMoveSearch at limit = $searchPosition")
            showAtLimit(next)
            if (next) {
                searchPosition++
            } else {
                searchPosition--
            }
        }
    }

    /**
     * check if current highlight is at either limit (forward or back)
     */
    private fun checkIfAtSearchLimits() {
        checkIfAtSearchLimit(true)
        checkIfAtSearchLimit(false)
    }

    /**
     * check if current highlight is at limit
     */
    private fun checkIfAtSearchLimit(forward: Boolean) {
        val nextPos = findNextMatchChunk(forward)
        if (nextPos < 0) {
            showAtLimit(forward)
        }
    }

    /**
     * indicate that we are at limit
     */
    private fun showAtLimit(forward: Boolean) {
        if (forward) {
            onSearching(doingSearch = false, numberOfChunkMatches = numberOfChunkMatches, atEnd = true, atStart = numberOfChunkMatches == 0)
        } else {
            onSearching(doingSearch = false, numberOfChunkMatches = numberOfChunkMatches, atEnd = numberOfChunkMatches == 0, atStart = true)
        }
    }

    /**
     * get next match item
     */
    private fun findNextMatchChunk(forward: Boolean): Int {
        var foundPos = -1
        if (forward) {
            val start = Math.max(searchPosition, -1)
            for (i in start + 1 until filteredItems.size) {
                val item = getItem(i)
                if (item?.hasSearchText == true) {
                    foundPos = i
                    break
                }
            }
        } else { // previous
            val start = Math.min(searchPosition, filteredItems.size)
            for (i in start - 1 downTo 0) {
                val item = getItem(i)
                if (item?.hasSearchText == true) {
                    foundPos = i
                    break
                }
            }
        }
        return foundPos
    }

    /**
     * gets the number of string matches within chunk and selects next item
     */
    private fun findSearchItemInChunkAndPreselect(item: ReviewListItem, target: Boolean) {
        val results = getMatchItemN(item, searchText, 1000, target) // get item count
        searchSubPositionItems = results.numberFound
        val searchSubPosition = 0
        if (results.needRender) {
            searchSubPositionItems = -1 // this will flag to get count after render completes
        } else {
            if (results.numberFound <= 0) {
                item.hasSearchText = false
            }
            checkIfAtSearchLimits()
        }
        item.selectItemNum = searchSubPosition
    }

    /**
     * search text to find the nth item (matchNumb) of the search string
     */
    private fun getMatchItemN(
        item: ReviewListItem,
        match: CharSequence?,
        matchNumb: Int,
        target: Boolean
    ): MatchResults {
        val matcher = match?.toString() ?: ""
        val length = matcher.length

        val text = if (searchingTarget) item.renderedTargetText else item.renderedSourceText
        val needRender = text == null

        val matcherEmpty = matcher.isEmpty()
        if (matcherEmpty || needRender || matchNumb < 0 || target != searchingTarget) {
            return MatchResults(-1, -1, needRender)
        }

        Log.i(TAG, "getMatchItemN() Search started: $matcher")

        var searchStartLocation = 0
        var count = 0
        var pos: Int
        val textLowerCase = text.toString().lowercase()

        while (true) {
            pos = textLowerCase.indexOf(matcher, searchStartLocation)
            if (pos < 0) { // not found
                break
            }
            searchStartLocation = pos + length
            if (++count > matchNumb) {
                return MatchResults(pos, count, false)
            }
        }

        // failed, return number of items
        return MatchResults(-1, count, false)
    }

    /**
     * technically no longer a filter but now a search that flags items containing search string
     */
    override fun filter(constraint: CharSequence?, subject: SearchSubject?, initialPosition: Int) {
        if (constraint != null) {
            searchText = constraint.toString().lowercase().trim()
            searchSubject = subject
            searchingTarget = subject == SearchSubject.TARGET || subject == SearchSubject.BOTH

            searchItems(initialPosition)
        } else {
            searchText = ""
            searchSubject = null

            for (item in filteredItems) {
                val reviewItem = item as ReviewListItem
                // Item will be re-rendered with default text (without highlights)
                if (reviewItem.hasSearchText) {
                    reviewItem.hasSearchText = false
                    reviewItem.renderedSourceText = null
                    reviewItem.renderedTargetText = null
                }
            }
            triggerNotifyDataSetChanged()
        }
    }

    /**
     * notify listener of search state changes
     */
    private fun onSearching(
        doingSearch: Boolean,
        numberOfChunkMatches: Int,
        atEnd: Boolean,
        atStart: Boolean
    ) {
        onClickListener?.onSearching(doingSearch, numberOfChunkMatches, atEnd, atStart)
        this.numberOfChunkMatches = numberOfChunkMatches
    }

    /**
     * Sets the position where the list should start when first built
     */
    override fun updateListStartPosition(startPosition: Int) {
        super.startPosition = startPosition
        searchPosition = startPosition
    }

    override fun hasFilter(): Boolean {
        return true
    }

    /**
     * enable/disable merge conflict filter in adapter
     */
    override fun setMergeConflictFilter(enableFilter: Boolean, forceMergeConflict: Boolean) {
        // If items are not initialized, don't apply merge filter
        if (items.isEmpty()) return

        if (forceMergeConflict) {
            // initialize merge conflict flag to true
            haveMergeConflict = true
        }
        // update display and status flags
        showMergeConflictIcon(haveMergeConflict, enableFilter)

        if (!haveMergeConflict || !enableFilter) {
            // if no merge conflict or filter off, then remove filter
            filteredItems.clear()
            filteredItems.addAll(items)
            filteredChapters.clear()
            filteredChapters.addAll(chapters)

            if (mergeConflictFilterOn) {
                mergeConflictFilterOn = false
                triggerNotifyDataSetChanged()
            }
            return
        }

        mergeConflictFilterOn = true

        val filterConstraint: CharSequence = "true" // will filter if string is not null
        showMergeConflictIcon(true, true)

        val filter = getMergeConflictFilter()
        filter.filter(filterConstraint)
    }

    private fun getMergeConflictFilter(): MergeConflictFilter {
        val filter = MergeConflictFilter(items)
        filter.setListener(object : MergeConflictFilter.OnMatchListener {
            override fun onMatch(item: ListItem) {
                if (!filteredChapters.contains(item.chapterSlug)) {
                    filteredChapters.add(item.chapterSlug)
                }
            }

            override fun onFinished(
                constraint: CharSequence,
                results: ArrayList<ListItem>
            ) {
                filteredItems.clear()
                filteredItems.addAll(results)
                updateMergeConflict()
                triggerNotifyDataSetChanged()
                checkForConflictSummary(filteredItems.size, items.size)
            }
        })
        return filter
    }

    override fun doCheckForMergeConflict() {
        val conflictCount = conflictsCount
        val mergeConflictFound = conflictCount > 0
        val hand = Handler(Looper.getMainLooper())
        hand.post {
            val doMergeFiltering = mergeConflictFound && mergeConflictFilterOn
            val conflictCountChanged = conflictCount != filteredItems.size
            val needToUpdateFilter = doMergeFiltering != mergeConflictFilterOn || conflictCountChanged

            checkForConflictSummary(conflictCount, items.size)

            filter(searchText, searchSubject ?: SearchSubject.TARGET, searchPosition) // update search filter

            showMergeConflictIcon(mergeConflictFound, mergeConflictFilterOn)
            if (needToUpdateFilter) {
                setMergeConflictFilter(mergeConflictFilterOn, false)
            }
        }
    }

    /**
     * check if we are supposed to pop up summary
     */
    protected fun checkForConflictSummary(conflictCount: Int, itemCount: Int) {
        if (showMergeSummary && itemCount > 0 && conflictCount > 0) {
            showMergeSummary = false // we just show the merge summary once

            val hand = Handler(Looper.getMainLooper())
            hand.post {
                val message = context.getString(R.string.merge_summary, conflictCount)
                mergeConflictSummaryDisplayed = true

                // pop up merge conflict summary
                AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                    .setTitle(R.string.merge_complete_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.label_close) { _, _ -> mergeConflictSummaryDisplayed = false }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    /**
     * returns true if merge conflict summary dialog is being displayed.
     */
    override fun isMergeConflictSummaryDisplayed(): Boolean {
        return mergeConflictSummaryDisplayed
    }

    fun setOnRenderHelpsListener(listener: OnRenderHelpsListener?) {
        this.renderHelpsListener = listener
    }

    fun setOnItemActionListener(listener: OnShowToastListener?) {
        this.itemActionListener = listener
    }

    override fun onNotifyItemChanged(position: Int) {
        notifyItemChanged(position)
    }

    override fun onCreateRemovableTabLayout(tag: String, title: String): View? {
        return onClickListener?.onCreateRemovableTabLayout(tag, title)
    }

    override fun onApplyLanguageTypefaceToTab(layout: TabLayout, values: ContentValues, title: String) {
        onClickListener?.onApplyLanguageTypefaceToTab(layout, values, title)
    }

    private fun searchItems(initialPosition: Int) {
        val matcher = searchText.toString()
        val matcherEmpty = matcher.isEmpty()

        if (matcher.isEmpty() && chunkSearchMatchesCounter == 0) {
            // TRICKY: don't run search if query is empty and there are not already matches
            return
        }

        Log.i(TAG, "filter(): Search started: $matcher")

        onSearching(doingSearch = true, numberOfChunkMatches = 0, atEnd = true, atStart = true)

        chunkSearchMatchesCounter = 0
        for (item in filteredItems) {
            val reviewItem = item as ReviewListItem
            var match = false

            if (!matcherEmpty) {
                if (searchingTarget) {
                    var foundMatch = reviewItem.targetText.lowercase().contains(matcher)
                    if (foundMatch) { // if matched, it could be in markup, so we
                        // double-check by rendering and searching that
                        val text = renderTargetText(
                            reviewItem.targetText,
                            reviewItem.targetTranslationFormat,
                            reviewItem.ft,
                            null,
                            reviewItem
                        )
                        foundMatch = text.toString().lowercase().contains(matcher)
                    }
                    match = foundMatch
                } else {
                    var foundMatch: Boolean
                    if (reviewItem.renderedSourceText != null) {
                        foundMatch = reviewItem.renderedSourceText.toString().lowercase().contains(matcher)
                    } else {
                        foundMatch = reviewItem.sourceText.lowercase().contains(matcher)
                        if (foundMatch) { // if match, it could be in markup, so we
                            // double check by rendering and searching that
                            val text = onRenderSourceText(reviewItem)
                            foundMatch = text.toString().lowercase().contains(matcher)
                        }
                    }
                    match = foundMatch
                }
            }

            if (reviewItem.hasSearchText && !match) { // check for search match cleared
                reviewItem.renderedTargetText = null  // re-render target
                reviewItem.renderedSourceText = null  // re-render source
                triggerNotifyItemChanged(reviewItem)
            }

            reviewItem.hasSearchText = match
            if (match) {
                reviewItem.renderedTargetText = null  // re-render target
                reviewItem.renderedSourceText = null  // re-render source
                chunkSearchMatchesCounter++
                triggerNotifyItemChanged(reviewItem)
            }
        }

        searchPosition = initialPosition
        layoutBuildNumber++ // force redraw of displayed cards
        val zeroItemsFound = chunkSearchMatchesCounter <= 0
        onSearching(doingSearch = false, numberOfChunkMatches = chunkSearchMatchesCounter, atEnd = zeroItemsFound, atStart = zeroItemsFound)
        if (!zeroItemsFound) {
            checkIfAtSearchLimits()
        }
    }

    /**
     * Disable/Enable items
     */
    private fun toggleDisableItems(disable: Boolean, itemToExclude: ListItem?) {
        for (i in filteredItems) {
            if (itemToExclude === i) continue
            i.isDisabled = disable
        }
        triggerNotifyDataSetChanged()
    }
}