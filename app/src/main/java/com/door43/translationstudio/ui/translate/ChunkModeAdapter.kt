package com.door43.translationstudio.ui.translate

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.door43.data.AssetsProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Frame
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentChunkListItemBinding
import com.door43.translationstudio.format
import com.door43.translationstudio.formatSub
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.DefaultRenderer
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.VerseDisplay
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.rendering.adapter.NoteClickListener
import com.door43.translationstudio.rendering.model.TextNode
import com.door43.translationstudio.ui.textadapters.SpannableAdapter
import com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.Companion.MAX_SOURCE_ITEMS
import com.door43.widget.ViewUtil
import com.google.android.material.tabs.TabLayout
import java.io.IOException

/**
 * Created by joel on 9/9/2015.
 */
class ChunkModeAdapter(
    typography: Typography,
    renderingProvider: RenderingProvider,
    assetsProvider: AssetsProvider
) : ViewModeAdapter<ChunkModeAdapter.ViewHolder>(), OnChunkModeListener {

    companion object {
        private const val BOTTOM_ELEVATION = 2f
        private const val TOP_ELEVATION = 3f
    }

    init {
        this.typography = typography
        this.renderingProvider = renderingProvider
        this.assetsProvider = assetsProvider
    }

    override fun initializeListItems(
        listItems: List<ListItemOld>,
        startingChapter: String?,
        startingChunk: String?
    ) {
        super.initializeListItems(listItems, startingChapter, startingChunk)
        triggerNotifyDataSetChanged()
        updateMergeConflict()
    }

    override fun createListItem(item: ListItemOld): ChunkListItemOld {
        return item.toType(::ChunkListItemOld)
    }

    /**
     * Check all cards for merge conflicts to see if we should show warning.
     * Runs as background task.
     */
    private fun updateMergeConflict() {
        doCheckForMergeConflict()
    }

    override fun getFocusedChunkSlug(position: Int): String {
        return if (position in 0 until filteredItems.size) {
            filteredItems[position].chunkSlug
        } else {
            ""
        }
    }

    override fun getFocusedChapterSlug(position: Int): String {
        return if (position > 0 && position < filteredItems.size) {
            filteredItems[position].chapterSlug
        } else {
            ""
        }
    }

    override fun onCreateManagedViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentChunkListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding, typography, assetsProvider, this)
    }

    override fun onBindManagedViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredItems[position] as ChunkListItemOld
        holder.bind(item)
    }

    override fun onCheckForPromptToEditDoneTargetCard(holder: ViewHolder): Boolean {
        return checkForPromptToEditDoneTargetCard(holder)
    }

    override fun onOpenTargetTranslationCard(holder: ViewHolder) {
        openTargetTranslationCard(holder, false)
    }

    override fun onCloseTargetTranslationCard(holder: ViewHolder) {
        closeTargetTranslationCard(holder, true)
    }

    override fun onEditTarget(target: EditText, position: Int) {
        val item = filteredItems[position]
        editTarget(target, item)
    }

    override fun onCreateRemovableTabLayout(tag: String, title: String): View? {
        return onClickListener?.onCreateRemovableTabLayout(tag, title)
    }

    override fun onApplyLanguageTypefaceToTab(layout: TabLayout, values: ContentValues, title: String) {
        onClickListener?.onApplyLanguageTypefaceToTab(layout, values, title)
    }

    override fun onSourceTranslationTabClick(sourceId: String) {
        onClickListener?.onSourceTranslationTabClick(sourceId)
    }

    override fun onNewSourceTranslationTabClick() {
        onClickListener?.onNewSourceTranslationTabClick()
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int, itemPosition: Int) {
        val item = filteredItems[itemPosition] as ChunkListItemOld
        val translation = Translator.compileTranslation(s as Editable)

        if (item.isProjectTitle) {
            try {
                item.target.applyProjectTitleTranslation(translation)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else if (item.isChapterTitle) {
            item.target.applyChapterTitleTranslation(
                item.target.getChapterTranslation(item.chapterSlug),
                translation
            )
        } else if (item.isChapterReference) {
            item.target.applyChapterReferenceTranslation(
                item.target.getChapterTranslation(item.chapterSlug),
                translation
            )
        } else {
            item.target.applyFrameTranslation(
                item.target.getFrameTranslation(
                    item.chapterSlug,
                    item.chunkSlug,
                    item.targetTranslationFormat
                ),
                translation
            )
        }

        item.renderedTargetNodes = renderText(
            translation,
            item.targetTranslationFormat
        )
    }

    override fun onConflictButtonClicked(position: Int) {
        val item = filteredItems[position] as ChunkListItemOld
        val args = Bundle()
        args.putBoolean(ChunkModeFragment.EXTRA_TARGET_OPEN, true)
        args.putString(Translator.EXTRA_CHAPTER_ID, item.chapterSlug)
        args.putString(Translator.EXTRA_FRAME_ID, item.chunkSlug)

        onClickListener?.openTranslationMode(TranslationViewMode.REVIEW, args)
    }

    override fun onRenderNodes(text: String, format: TranslationFormat): List<TextNode> {
        return renderText(text, format)
    }

    /**
     * Renders the chapter title card
     * begin edit of target card
     *
     * @param target target edit text
     */
    private fun editTarget(target: EditText, item: ListItemOld) {
        // flag that chunk is open for edit
        if (item.isChapterReference) {
            item.target.reopenChapterReference(item.chapterSlug)
        } else if (item.isChapterTitle) {
            item.target.reopenChapterTitle(item.chapterSlug)
        } else if (item.isProjectTitle) {
            item.target.openProjectTitle()
        } else {
            item.target.reopenFrame(item.chapterSlug, item.chunkSlug)
        }

        // set focus on edit text
        target.requestFocus()
        onClickListener?.showKeyboard(target)
    }

    /**
     * prompt to edit chunk that is marked done
     *
     * @param holder chunk view holder
     */
    private fun checkForPromptToEditDoneTargetCard(holder: ViewHolder): Boolean {
        // if page is already in front, and they are tapping on it, then see if they want to open for edit
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return false
        }
        val item = filteredItems[position] as ChunkListItemOld

        if (item.isComplete) {
            promptToEditDoneChunk(holder, item)
            return true
        }

        return false
    }

    /**
     * prompt to edit chunk that is marked done
     *
     * @param holder chunk view holder
     * @param item list item
     */
    fun promptToEditDoneChunk(holder: ViewHolder, item: ListItemOld) {
        AlertDialog.Builder(context, R.style.AppTheme_Dialog)
            .setTitle(R.string.chunk_done_title)
            .setMessage(R.string.chunk_done_prompt)
            .setPositiveButton(R.string.edit) { _, _ ->
                holder.binding.targetTranslationBody.isEnabled = true
                holder.binding.targetTranslationBody.isFocusable = true
                holder.binding.targetTranslationBody.isFocusableInTouchMode = true
                holder.binding.targetTranslationBody.enableLines = true

                item.isComplete = false
                editTarget(holder.binding.targetTranslationBody, item)
            }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }

    private fun renderText(text: String, format: TranslationFormat): List<TextNode> {
        val renderingGroup = RenderingGroup()
        if (Clickables.isClickableFormat(format)) {
            val renderer = renderingProvider.setupRenderingGroup(
                format,
                renderingGroup,
                verseDisplay = VerseDisplay.NUMBER,   // chunk mode never uses draggable pins
                target = true
            )
            renderer.setVersesEnabled(false)
            renderer.setParagraphsEnabled(false)
        } else {
            renderingGroup.addEngine(DefaultRenderer())
        }
        renderingGroup.init(text)
        val renderNodes = renderingGroup.start()
        return emptyList()//RenderNodeConverter.renderNodesToTextNodes(renderNodes)
    }

    override fun getItemCount(): Int {
        return filteredItems.size
    }

    /**
     * removes text selection from the target card
     *
     * @param holder chunk view holder
     */
    fun clearSelectionFromTarget(holder: ViewHolder) {
        holder.binding.targetTranslationBody.clearFocus()
    }

    /**
     * Toggle the target translation card between front and back
     *
     * @param holder holder
     * @param swipeLeft true if moving left to right
     */
    fun toggleTargetTranslationCard(holder: ViewHolder, swipeLeft: Boolean) {
        closeTargetTranslationCard(holder, !swipeLeft)
        openTargetTranslationCard(holder, !swipeLeft)
        holder.enableClicksIfChunkIsDone()
    }

    /**
     * Moves the target translation card to the back
     *
     * @param holder chunk view holder
     * @param leftToRight true if moving left to right
     */
    fun closeTargetTranslationCard(holder: ViewHolder, leftToRight: Boolean) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ChunkListItemOld

        if (item.isTargetCardOpen) {
            clearSelectionFromTarget(holder)

            ViewUtil.animateSwapCards(
                holder.binding.targetTranslationCard,
                holder.binding.sourceTranslationCard,
                TOP_ELEVATION, BOTTOM_ELEVATION,
                leftToRight,
                object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}

                    override fun onAnimationEnd(animation: Animation) {
                        item.isTargetCardOpen = false
                        onClickListener?.closeKeyboard()
                        holder.setCardStatus(item.isComplete, true)
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                }
            )
            onClickListener?.closeKeyboard()
            // re-enable new tab button
            holder.binding.newTabButton.isEnabled = true
        }
    }

    /**
     * Moves the target translation to the top
     *
     * @param holder chunk view holder
     * @param leftToRight true if moving left to right
     */
    fun openTargetTranslationCard(holder: ViewHolder, leftToRight: Boolean) {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return
        }
        val item = filteredItems[position] as ChunkListItemOld

        if (!item.isTargetCardOpen) {
            ViewUtil.animateSwapCards(
                holder.binding.sourceTranslationCard,
                holder.binding.targetTranslationCard,
                TOP_ELEVATION, BOTTOM_ELEVATION,
                leftToRight,
                object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {
                        holder.setCardStatus(item.isComplete, false)
                    }

                    override fun onAnimationEnd(animation: Animation) {
                        item.isTargetCardOpen = true
                        onClickListener?.closeKeyboard()
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                }
            )
            onClickListener?.closeKeyboard()
            // disable new tab button so we don't accidentally open it
            holder.binding.newTabButton.isEnabled = false
        }
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

    override fun markAllChunksDone() {}

    override fun setResourcesOpened(status: Boolean) {}

    class ViewHolder(
        val binding: FragmentChunkListItemBinding,
        private val typography: Typography,
        private val assetsProvider: AssetsProvider,
        private val chunkModeListener: OnChunkModeListener?
    ) : RecyclerView.ViewHolder(binding.root) {

        var textWatcher: TextWatcher
        private val context: Context = binding.root.context
        private val tabSelectedListener: TabLayout.OnTabSelectedListener

        init {
            textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    chunkModeListener?.let {
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            it.onTextChanged(s, start, before, count, position)
                        }
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            }

            tabSelectedListener = object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val sourceTranslationId = tab.tag as String
                    chunkModeListener?.onSourceTranslationTabClick(sourceTranslationId)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {}
            }

            itemView.post {
                binding.targetTranslationCard.setOnTouchListener { _, event ->
                    // for touches on card other than edit area
                    if (MotionEvent.ACTION_UP == event.action) {
                        return@setOnTouchListener chunkModeListener?.onCheckForPromptToEditDoneTargetCard(this@ViewHolder) ?: false
                    }
                    false
                }

                //for touches on edit area
                binding.targetTranslationBody.setOnTouchListener { _, event ->
                    if (MotionEvent.ACTION_UP == event.action) {
                        return@setOnTouchListener chunkModeListener?.onCheckForPromptToEditDoneTargetCard(this@ViewHolder) ?: false
                    }
                    false
                }

                binding.targetTranslationCard.setOnClickListener {
                    chunkModeListener?.let { listener ->
                        listener.onOpenTargetTranslationCard(this@ViewHolder)

                        // Accept clicks anywhere on card as if they were on the text box --
                        // but only if the text is actually editable (i.e., not yet done).
                        if (binding.targetTranslationBody.isEnabled) {
                            val position = bindingAdapterPosition
                            if (position != RecyclerView.NO_POSITION) {
                                listener.onEditTarget(binding.targetTranslationBody, position)
                            }
                        } else {
                            // if marked as done (disabled for edit), enable to allow capture of click events, but do not make it focusable so they can't edit
                            enableClicksIfChunkIsDone()
                        }
                    }
                }

                binding.sourceTranslationCard.setOnClickListener {
                    chunkModeListener?.onCloseTargetTranslationCard(this@ViewHolder)
                }

                binding.newTabButton.setOnClickListener {
                    chunkModeListener?.onNewSourceTranslationTabClick()
                }

                binding.conflictButton.setOnClickListener {
                    chunkModeListener?.let { listener ->
                        val position = bindingAdapterPosition
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onConflictButtonClicked(position)
                        }
                    }
                }
            }
        }

        fun bind(item: ChunkListItemOld) {
            val cardMargin = context.resources.getDimensionPixelSize(R.dimen.card_margin)
            val stackedCardMargin = context.resources.getDimensionPixelSize(R.dimen.stacked_card_margin)
            if (item.isTargetCardOpen) {
                // target on top
                binding.sourceTranslationCard.cardElevation = BOTTOM_ELEVATION
                binding.targetTranslationCard.cardElevation = TOP_ELEVATION
                binding.targetTranslationCard.bringToFront()
                val targetParams = binding.targetTranslationCard.layoutParams as FrameLayout.LayoutParams
                targetParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin)
                binding.targetTranslationCard.layoutParams = targetParams
                val sourceParams = binding.sourceTranslationCard.layoutParams as FrameLayout.LayoutParams
                sourceParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin)
                binding.sourceTranslationCard.layoutParams = sourceParams
                (binding.targetTranslationCard.parent as View).requestLayout()
                (binding.targetTranslationCard.parent as View).invalidate()

                // disable new tab button so we don't accidentally open it
                binding.newTabButton.isEnabled = false
            } else {
                // source on top
                binding.targetTranslationCard.cardElevation = BOTTOM_ELEVATION
                binding.sourceTranslationCard.cardElevation = TOP_ELEVATION
                binding.sourceTranslationCard.bringToFront()
                val sourceParams = binding.sourceTranslationCard.layoutParams as FrameLayout.LayoutParams
                sourceParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin)
                binding.sourceTranslationCard.layoutParams = sourceParams
                val targetParams = binding.targetTranslationCard.layoutParams as FrameLayout.LayoutParams
                targetParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin)
                binding.targetTranslationCard.layoutParams = targetParams
                (binding.sourceTranslationCard.parent as View).requestLayout()
                (binding.sourceTranslationCard.parent as View).invalidate()

                // re-enable new tab button
                binding.newTabButton.isEnabled = true
            }

            // load tabs
            val tabs = item.tabs
            renderSourceTabs(tabs, item.source.slug)

            renderChunk(item)

            // set up fonts
            binding.sourceTranslationTitle.formatSub(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                item.source.language.slug,
                item.source.language.direction
            )
            binding.sourceTranslationBody.format(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                item.source.language.slug,
                item.source.language.direction
            )
            binding.targetTranslationTitle.formatSub(
                typography,
                assetsProvider,
                TranslationType.TARGET,
                item.target.targetLanguage.slug,
                item.target.targetLanguage.direction
            )
            binding.targetTranslationBody.format(
                typography,
                assetsProvider,
                TranslationType.TARGET,
                item.target.targetLanguage.slug,
                item.target.targetLanguage.direction
            )

            //////
            // set up card UI for merge conflicts
            if (item.hasMergeConflicts) {
                binding.conflictButton.visibility = View.VISIBLE
                binding.conflictFrame.visibility = View.VISIBLE
                binding.targetTranslationBody.visibility = View.GONE
            } else {
                binding.conflictFrame.visibility = View.GONE
                binding.targetTranslationBody.visibility = View.VISIBLE
            }

            ViewUtil.makeLinksClickable(binding.sourceTranslationBody)

            if (tabs.size >= MAX_SOURCE_ITEMS) {
                binding.newTabButton.visibility = View.GONE
            } else {
                binding.newTabButton.visibility = View.VISIBLE
            }
        }

        /**
         * if chunk that is marked done, then enable click event
         */
        fun enableClicksIfChunkIsDone() {
            if (!binding.targetTranslationBody.isEnabled) {
                binding.targetTranslationBody.isEnabled = true
                binding.targetTranslationBody.isFocusable = false
            }
        }

        /**
         * Renders the frame cards
         *
         * @param item chunk list item
         */
        private fun renderChunk(item: ChunkListItemOld) {
            removeTextChangeListener()

            // Source
            if (item.renderedSourceNodes == null && chunkModeListener != null) {
                item.renderedSourceNodes = chunkModeListener.onRenderNodes(
                    item.sourceText, item.sourceTranslationFormat
                )
            }
            @Suppress("DEPRECATION")
            binding.sourceTranslationBody.setText(
                SpannableAdapter.convert(
                    item.renderedSourceNodes ?: emptyList(),
                    context = context,
                    noteClickListener = NoteClickListener { _, marker, _, _ ->
                        showFootnoteDialog(marker)
                    }
                )
            )

            // Target
            if (item.renderedTargetNodes == null && chunkModeListener != null) {
                item.renderedTargetNodes = chunkModeListener.onRenderNodes(
                    item.targetText, item.targetTranslationFormat
                )
            }
            @Suppress("DEPRECATION")
            binding.targetTranslationBody.setText(
                TextUtils.concat(
                    SpannableAdapter.convert(
                        item.renderedTargetNodes ?: emptyList(),
                        context = context,
                        noteClickListener = NoteClickListener { _, marker, _, _ ->
                            showFootnoteDialog(marker)
                        }
                    ),
                    "\n"
                )
            )

            // render source title
            if (item.isProjectTitle) {
                binding.sourceTranslationTitle.text = ""
            } else if (item.isChapter) {
                binding.sourceTranslationTitle.text = item.source.project.name.trim()
            } else {
                // TODO: we should read the title from a cache instead of doing file io again
                var title = item.source.readChunk(item.chapterSlug, "title").trim()
                if (title.isEmpty()) {
                    title = try {
                        "${item.source.project.name.trim()} ${item.chapterSlug.toInt()}"
                    } catch (e: Exception) {
                        "${item.source.project.name.trim()} ${item.chapterSlug}"
                    }
                }
                val verseSpan = Frame.parseVerseTitle(item.sourceText, item.sourceTranslationFormat)
                if (verseSpan.isEmpty()) {
                    title += try {
                        ":${item.chunkSlug.toInt()}"
                    } catch (e: Exception) {
                        ":${item.chunkSlug}"
                    }
                } else {
                    title += ":$verseSpan"
                }
                binding.sourceTranslationTitle.text = title
            }

            // render target title
            binding.targetTranslationTitle.text = item.targetTitle

            // indicate complete
            setCardStatus(item.isComplete, true)

            attachTextChangeListener()
        }

        fun attachTextChangeListener() {
            binding.targetTranslationBody.removeTextChangedListener(textWatcher)
            binding.targetTranslationBody.addTextChangedListener(textWatcher)
        }

        fun removeTextChangeListener() {
            binding.targetTranslationBody.removeTextChangedListener(textWatcher)
        }

        fun setCardStatus(finished: Boolean, closed: Boolean) {
            if (closed) {
                binding.targetTranslationBody.enableLines = false
                if (finished) {
                    binding.targetTranslationInnerCard.setBackgroundResource(R.color.card_background_color)
                } else {
                    binding.targetTranslationInnerCard.setBackgroundResource(R.drawable.paper_repeating)
                }
            } else {
                binding.targetTranslationBody.enableLines = true
                binding.targetTranslationInnerCard.setBackgroundResource(R.color.card_background_color)
            }
        }

        private fun showFootnoteDialog(marker: TextNode.NoteMarker) {
            AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(R.string.title_footnote)
                .setMessage(marker.notes)
                .setPositiveButton(R.string.dismiss, null)
                .show()
        }

        private fun renderSourceTabs(tabs: List<ContentValues>, sourceSlug: String) {
            binding.sourceTranslationTabs.removeOnTabSelectedListener(tabSelectedListener)
            binding.sourceTranslationTabs.removeAllTabs()

            for (values in tabs) {
                val tag = values.getAsString("tag")
                val title = values.getAsString("title")

                chunkModeListener?.let { listener ->
                    val tabLayout = listener.onCreateRemovableTabLayout(tag, title)

                    if (tabLayout != null) {
                        val tab = binding.sourceTranslationTabs.newTab()
                        tab.tag = tag
                        tab.customView = tabLayout
                        binding.sourceTranslationTabs.addTab(tab)
                    }

                    listener.onApplyLanguageTypefaceToTab(binding.sourceTranslationTabs, values, title)
                }
            }

            // select correct tab
            for (i in 0 until binding.sourceTranslationTabs.tabCount) {
                val tab = binding.sourceTranslationTabs.getTabAt(i)
                if (sourceSlug == tab?.tag) {
                    tab.select()
                    break
                }
            }

            binding.sourceTranslationTabs.addOnTabSelectedListener(tabSelectedListener)
        }
    }
}