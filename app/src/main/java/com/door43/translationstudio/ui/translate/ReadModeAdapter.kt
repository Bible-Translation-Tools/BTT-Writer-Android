package com.door43.translationstudio.ui.translate

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.door43.data.AssetsProvider
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentReadListItemBinding
import com.door43.translationstudio.format
import com.door43.translationstudio.formatTitle
import com.door43.translationstudio.rendering.ClickableRenderingEngine
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.rendering.RenderingProvider
import com.door43.translationstudio.ui.spannables.NoteSpan
import com.door43.translationstudio.ui.spannables.Span
import com.door43.widget.ViewUtil
import com.google.android.material.tabs.TabLayout

/**
 * Created by joel on 9/9/2015.
 */
class ReadModeAdapter(
    typography: Typography,
    renderingProvider: RenderingProvider,
    assetsProvider: AssetsProvider
) : ViewModeAdapter<ReadModeAdapter.ViewHolder>(), OnReadModeListener {

    private var renderedTargetBody: Array<CharSequence?> = emptyArray()
    private var renderedSourceBody: Array<CharSequence?> = emptyArray()
    private var targetStateOpen: BooleanArray = BooleanArray(0)

    /**
     * Reference to the list of all items (chunks)
     */
    private val chunks = ArrayList<ListItem>()

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
        listItems: List<ListItem>,
        startingChapter: String,
        startingChunk: String
    ) {
        layoutBuildNumber++ // force resetting of fonts
        chunks.clear()
        chapters.clear()
        items.clear()

        startPosition = 0
        var foundStartingChapter = false

        for (item in listItems) {
            if (!foundStartingChapter && item.chapterSlug == startingChapter) {
                startPosition = items.size
                foundStartingChapter = true
            }
            if (!chapters.contains(item.chapterSlug)) {
                chapters.add(item.chapterSlug)
                items.add(createListItem(item))
            }
        }

        chunks.addAll(listItems)

        targetStateOpen = BooleanArray(chapters.size)
        renderedSourceBody = arrayOfNulls(chapters.size)
        renderedTargetBody = arrayOfNulls(chapters.size)

        triggerNotifyDataSetChanged()
        updateMergeConflict()
    }

    override fun createListItem(item: ListItem): ReadListItem {
        return item.toType(::ReadListItem)
    }

    /**
     * check all cards for merge conflicts to see if we should show warning. Runs as background task.
     */
    private fun updateMergeConflict() {
        doCheckForMergeConflict()
    }

    override val conflictsCount: Int
        get() {
            var count = 0
            for (item in chunks) {
                if (item.hasMergeConflicts) {
                    count++
                }
            }
            return count
        }

    override fun getFocusedChunkSlug(position: Int): String {
        return ""
    }

    override fun getFocusedChapterSlug(position: Int): String {
        return if (position in 0 until chapters.size) {
            chapters[position]
        } else {
            ""
        }
    }

    override fun getItemPosition(chapterSlug: String, chunkSlug: String): Int {
        return chapters.indexOf(chapterSlug)
    }

    override fun getItem(chapterSlug: String, chunkSlug: String): ListItem? {
        val position = getItemPosition(chapterSlug, chunkSlug)
        return if (position >= 0) items[position] else null
    }

    override fun onCreateManagedViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentReadListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding, typography, assetsProvider, this)
    }

    override fun markAllChunksDone() {}

    override fun onOpenTargetTranslationCard(holder: ViewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            openTargetTranslationCard(holder, position, false)
        }
    }

    override fun onCloseTargetTranslationCard(holder: ViewHolder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            closeTargetTranslationCard(holder, position, true)
        }
    }

    override fun onNewSourceTranslationTabClick() {
        onClickListener?.onNewSourceTranslationTabClick()
    }

    override fun onRenderSourceText(holder: ViewHolder): CharSequence {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return ""
        }
        val item = items[position] as ReadListItem

        val sourceChapterBody = item.sourceText
        val bodyFormat = TranslationFormat.parse(item.source.contentMimeType)
        val sourceRendering = RenderingGroup()

        if (Clickables.isClickableFormat(bodyFormat)) {
            val noteClickListener = object : Span.OnClickListener {
                override fun onClick(view: View, span: Span, start: Int, end: Int) {
                    if (span is NoteSpan) {
                        showFootnote(span, holder.binding.root.context)
                    }
                }

                override fun onLongClick(view: View, span: Span, start: Int, end: Int) {}
            }
            val renderer = renderingProvider.setupRenderingGroup(
                bodyFormat,
                sourceRendering,
                null,
                noteClickListener,
                true
            ) as ClickableRenderingEngine
            // In read mode (and only in read mode), pull leading major section headings out for
            // display above chapter headings.
            renderer.setSuppressLeadingMajorSectionHeadings(true)
            val heading = renderer.getLeadingMajorSectionHeading(sourceChapterBody)
            holder.binding.sourceTranslationHeading.setText(heading)
            holder.binding.sourceTranslationHeading.visibility = if (heading.isNotEmpty()) View.VISIBLE else View.GONE
        } else {
            sourceRendering.addEngine(renderingProvider.createDefaultRenderer())
        }
        sourceRendering.init(sourceChapterBody)
        renderedSourceBody[position] = sourceRendering.start()

        return renderedSourceBody[position] ?: ""
    }

    override fun onRenderTargetText(holder: ViewHolder): CharSequence {
        val position = holder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION) {
            return ""
        }
        val item = items[position] as ReadListItem

        val bodyFormat = item.target.format
        val chapterBody = item.targetText
        val targetRendering = RenderingGroup()

        if (Clickables.isClickableFormat(bodyFormat)) {
            val noteClickListener = object : Span.OnClickListener {
                override fun onClick(view: View, span: Span, start: Int, end: Int) {
                    if (span is NoteSpan) {
                        showFootnote(span, holder.binding.root.context)
                    }
                }

                override fun onLongClick(view: View, span: Span, start: Int, end: Int) {}
            }
            val renderer = renderingProvider.setupRenderingGroup(
                bodyFormat,
                targetRendering,
                null,
                noteClickListener,
                true
            ) as ClickableRenderingEngine
            renderer.setVersesEnabled(true)
        } else {
            targetRendering.addEngine(renderingProvider.createDefaultRenderer())
        }
        targetRendering.init(chapterBody)
        renderedTargetBody[position] = targetRendering.start()

        return renderedTargetBody[position] ?: ""
    }

    override fun onOpenTranslationMode(chapterSlug: String) {
        val args = Bundle()
        args.putBoolean(ChunkModeFragment.EXTRA_TARGET_OPEN, true)
        args.putString(Translator.EXTRA_CHAPTER_ID, chapterSlug)
        onClickListener?.openTranslationMode(TranslationViewMode.CHUNK, args)
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

    /**
     * get the chapter for the position, or null if not found
     */
    fun getChapterForPosition(position: Int): String {
        var pos = position
        if (pos < 0) {
            pos = 0
        } else if (pos >= chapters.size) {
            pos = chapters.size - 1
        }
        return chapters[pos]
    }

    override fun onBindManagedViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position] as ReadListItem
        val targetOpen = targetStateOpen[position]
        val chapterSlug = chapters[position]
        val renderedSourceText = renderedSourceBody[position]
        val renderedTargetText = renderedTargetBody[position]

        holder.bind(item, targetOpen, chapterSlug, renderedSourceText, renderedTargetText)
    }

    override fun getItemCount(): Int {
        return chapters.size
    }

    /**
     * Toggle the target translation card between front and back
     */
    fun toggleTargetTranslationCard(holder: ViewHolder, position: Int, swipeLeft: Boolean) {
        if (targetStateOpen[position]) {
            closeTargetTranslationCard(holder, position, !swipeLeft)
            return
        }
        openTargetTranslationCard(holder, position, !swipeLeft)
    }

    /**
     * Moves the target translation card to the back
     */
    fun closeTargetTranslationCard(holder: ViewHolder, position: Int, leftToRight: Boolean) {
        if (targetStateOpen[position]) {
            ViewUtil.animateSwapCards(
                holder.binding.targetTranslationCard,
                holder.binding.sourceTranslationCard,
                TOP_ELEVATION,
                BOTTOM_ELEVATION,
                leftToRight,
                object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}

                    override fun onAnimationEnd(animation: Animation) {
                        targetStateOpen[position] = false
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                }
            )

            // re-enable new tab button
            holder.binding.newTabButton.isEnabled = true
        }
    }

    /**
     * Moves the target translation to the top
     */
    fun openTargetTranslationCard(holder: ViewHolder, position: Int, leftToRight: Boolean) {
        if (!targetStateOpen[position]) {
            ViewUtil.animateSwapCards(
                holder.binding.sourceTranslationCard,
                holder.binding.targetTranslationCard,
                TOP_ELEVATION,
                BOTTOM_ELEVATION,
                leftToRight,
                object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {}

                    override fun onAnimationEnd(animation: Animation) {
                        targetStateOpen[position] = true
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                }
            )

            // disable new tab button so we don't accidentally open it
            holder.binding.newTabButton.isEnabled = false
        }
    }

    /**
     * display selected footnote in dialog.
     */
    private fun showFootnote(span: NoteSpan, context: Context) {
        val marker = span.passage
        var title: CharSequence = context.resources.getText(R.string.title_footnote)
        if (marker.toString().isNotEmpty()) {
            title = "$title: $marker"
        }
        val message = span.notes

        AlertDialog.Builder(context, R.style.AppTheme_Dialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.dismiss, null)
            .show()
    }

    override fun getSections(): Array<Any> {
        return chapters.toTypedArray()
    }

    override fun getPositionForSection(sectionIndex: Int): Int {
        // not used
        return sectionIndex
    }

    override fun getSectionForPosition(position: Int): Int {
        return position
    }

    override fun setResourcesOpened(status: Boolean) {}

    class ViewHolder(
        val binding: FragmentReadListItemBinding,
        private val typography: Typography,
        private val assetsProvider: AssetsProvider,
        private val readModeListener: OnReadModeListener?
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private val tabSelectedListener: TabLayout.OnTabSelectedListener
        private var chapterSlug: String? = null

        init {
            tabSelectedListener = object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val sourceTranslationId = tab.tag as String
                    readModeListener?.onSourceTranslationTabClick(sourceTranslationId)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {}

                override fun onTabReselected(tab: TabLayout.Tab) {}
            }

            itemView.post {
                binding.targetTranslationCard.setOnClickListener {
                    readModeListener?.onOpenTargetTranslationCard(this@ViewHolder)
                }

                binding.sourceTranslationCard.setOnClickListener {
                    readModeListener?.onCloseTargetTranslationCard(this@ViewHolder)
                }

                binding.newTabButton.setOnClickListener {
                    readModeListener?.onNewSourceTranslationTabClick()
                }

                val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (readModeListener != null && chapterSlug != null) {
                            readModeListener.onOpenTranslationMode(chapterSlug!!)
                        }
                        return true
                    }
                })

                binding.beginTranslatingButton.setOnTouchListener { _, event ->
                    detector.onTouchEvent(event)
                }
            }
        }

        fun bind(
            item: ReadListItem,
            isTargetOpen: Boolean,
            chapterSlug: String,
            renderedSourceText: CharSequence?,
            renderedTargetText: CharSequence?
        ) {
            val cardMargin = context.resources.getDimensionPixelSize(R.dimen.card_margin)
            val stackedCardMargin = context.resources.getDimensionPixelSize(R.dimen.stacked_card_margin)
            this.chapterSlug = chapterSlug

            if (isTargetOpen) {
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

            // render the source chapter body
            var sourceText = renderedSourceText
            if (sourceText == null) {
                sourceText = readModeListener?.onRenderSourceText(this) ?: ""
            }

            binding.sourceTranslationBody.setText(sourceText)
            ViewUtil.makeLinksClickable(binding.sourceTranslationBody)
            binding.sourceTranslationTitle.setText(item.chapterTitle)

            // render the target chapter body
            var targetText = renderedTargetText
            if (targetText == null) {
                targetText = readModeListener?.onRenderTargetText(this) ?: ""
            }

            // display begin translation button
            if (targetText.toString().trim().isEmpty()) {
                binding.beginTranslatingButton.visibility = View.VISIBLE
            } else {
                binding.beginTranslatingButton.visibility = View.GONE
            }

            binding.targetTranslationBody.setText(targetText)
            ViewUtil.makeLinksClickable(binding.targetTranslationBody)

            var targetCardTitle = ""

            // look for translated chapter title first
            val chapterTranslation = item.target.getChapterTranslation(chapterSlug)
            if (chapterTranslation != null) {
                targetCardTitle = chapterTranslation.title.trim()
            }

            // if no target chapter title translation, fall back to source chapter title
            if (targetCardTitle.isEmpty() && item.chapterTitle.trim().isNotEmpty()) {
                targetCardTitle = item.chapterTitle.trim()
            }

            if (targetCardTitle.isEmpty()) { // if no chapter titles, fall back to project title, try translated title first
                val projTrans = item.target.getProjectTranslation()
                if (projTrans.title.trim().isNotEmpty()) {
                    targetCardTitle = try {
                        "${projTrans.title.trim()} ${chapterSlug.toInt()}"
                    } catch (e: Exception) {
                        "${projTrans.title.trim()} $chapterSlug"
                    }
                }
            }

            if (targetCardTitle.isEmpty()) { // fall back to project source title
                targetCardTitle = item.source.readChunk("front", "title").trim()
                if (chapterSlug != "front") {
                    targetCardTitle += try {
                        " ${chapterSlug.toInt()}"
                    } catch (e: Exception) {
                        " $chapterSlug"
                    }
                }
            }

            binding.targetTranslationTitle.setText("$targetCardTitle - ${item.target.targetLanguage.name}")

            // load tabs
            val tabs = item.tabs
            renderSourceTabs(tabs, item.source.slug)

            // set up fonts
            binding.sourceTranslationHeading.formatTitle(
                typography,
                assetsProvider,
                TranslationType.SOURCE,
                item.source.language.slug,
                item.source.language.direction
            )
            binding.sourceTranslationTitle.formatTitle(
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
            binding.targetTranslationTitle.formatTitle(
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

            if (tabs.size >= ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS) {
                binding.newTabButton.visibility = View.GONE
            } else {
                binding.newTabButton.visibility = View.VISIBLE
            }
        }

        private fun renderSourceTabs(tabs: List<ContentValues>, sourceSlug: String) {
            binding.sourceTranslationTabs.removeOnTabSelectedListener(tabSelectedListener)
            binding.sourceTranslationTabs.removeAllTabs()

            for (values in tabs) {
                val tag = values.getAsString("tag")
                val title = values.getAsString("title")

                readModeListener?.let { listener ->
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

            // hook up listener
            binding.sourceTranslationTabs.addOnTabSelectedListener(tabSelectedListener)
        }
    }
}