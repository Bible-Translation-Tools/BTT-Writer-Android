package com.door43.translationstudio.ui.draft

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.door43.translationstudio.R
import com.door43.translationstudio.core.RenderingProvider
import com.door43.translationstudio.core.TranslationFormat
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentDraftListItemBinding
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.translationstudio.ui.spannables.NoteSpan
import com.door43.translationstudio.ui.spannables.Span
import com.door43.util.sortNumerically
import com.door43.widget.ViewUtil
import org.json.JSONException
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.resourcecontainer.ResourceContainer

/**
 * Created by blm on 1/10/2016.
 */
class DraftAdapter(
    private val typography: Typography,
    private val renderingProvider: RenderingProvider
) :
    RecyclerView.Adapter<DraftAdapter.ViewHolder>() {

    private var draftTranslation: ResourceContainer? = null
    private var sourceLanguage: SourceLanguage? = null
    private var renderedDraftBody = arrayOf<CharSequence?>()
    private val chapters = arrayListOf<String>()
    private val layoutBuildNumber = 0

    fun setData(draftTranslation: ResourceContainer, sourceLanguage: SourceLanguage?) {
        this.draftTranslation = draftTranslation
        this.sourceLanguage = sourceLanguage

        val chapters = draftTranslation.chapters()
        chapters.sortNumerically()

        this.chapters.clear()
        this.chapters.addAll(chapters)

        renderedDraftBody = arrayOfNulls(this.chapters.size)
        notifyDataSetChanged()
    }

    fun getFocusedFrameId(position: Int): String? {
        return null
    }

    fun getFocusedChapterId(position: Int): String? {
        return if (position >= 0 && position < chapters.size) {
            chapters[position]
        } else {
            null
        }
    }

    fun getItemPosition(chapterId: String, frameId: String?): Int {
        for (i in chapters.indices) {
            if (chapters[i] == chapterId) {
                return i
            }
        }
        return -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FragmentDraftListItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.binding.root.context
        val cardMargin = context.resources.getDimensionPixelSize(R.dimen.card_margin)
        val stackedCardMargin =
            context.resources.getDimensionPixelSize(R.dimen.stacked_card_margin)

        val sourceParams = holder.binding.sourceTranslationCard.layoutParams as FrameLayout.LayoutParams
        sourceParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin)
        holder.binding.sourceTranslationCard.layoutParams = sourceParams
        (holder.binding.sourceTranslationCard.parent as View).requestLayout()
        (holder.binding.sourceTranslationCard.parent as View).invalidate()

        val chapterSlug = chapters[position]

        // render the draft chapter body
        if (renderedDraftBody[position] == null) {
            var chapterBody: String? = ""
            val chunks = draftTranslation?.chunks(chapterSlug) ?: arrayOf()
            chunks.sortNumerically()

            for (chunk in chunks) {
                chapterBody += draftTranslation!!.readChunk(chapterSlug, chunk)
            }
            // String chapterBody = getChapterBody(mDraftTranslation, chapterSlug.getId());/
            var bodyFormat: TranslationFormat? =
                null // mLibrary.getChapterBodyFormat(mDraftTranslation, chapterSlug.getId());
            try {
                bodyFormat =
                    TranslationFormat.parse(draftTranslation?.info?.getString("content_mime_type"))
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            val sourceRendering = RenderingGroup()
            if (Clickables.isClickableFormat(bodyFormat)) {
                // TODO: add click listeners
                val noteClickListener: Span.OnClickListener = object : Span.OnClickListener {
                    override fun onClick(view: View, span: Span, start: Int, end: Int) {
                        if (span is NoteSpan) {
                            AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                                .setTitle(R.string.title_footnote)
                                .setMessage(span.notes)
                                .setPositiveButton(R.string.dismiss, null)
                                .show()
                        }
                    }

                    override fun onLongClick(view: View, span: Span, start: Int, end: Int) {
                    }
                }
                val renderer = renderingProvider.setupRenderingGroup(
                    bodyFormat,
                    sourceRendering,
                    null,
                    noteClickListener,
                    true
                )

                // In read mode (and only in read mode), pull leading major section headings out for
                // display above chapter headings.
                renderer.setSuppressLeadingMajorSectionHeadings(true)
                val heading = renderer.getLeadingMajorSectionHeading(chapterBody)
                holder.binding.sourceTranslationHeading.text = heading
                holder.binding.sourceTranslationHeading.visibility =
                    if (heading.isNotEmpty()) View.VISIBLE else View.GONE
            } else {
                sourceRendering.addEngine(renderingProvider.createDefaultRenderer())
            }
            sourceRendering.init(chapterBody)
            renderedDraftBody[position] = sourceRendering.start()
        }

        holder.binding.sourceTranslationBody.text = renderedDraftBody[position]
        ViewUtil.makeLinksClickable(holder.binding.sourceTranslationBody)
        var chapterTitle: String? = null //chapterSlug.title;
        chapterTitle = draftTranslation?.readChunk(chapterSlug, "title")
        if (chapterTitle == null) {
            chapterTitle = draftTranslation?.readChunk("front", "title") + " " + chapterSlug.toInt()
        }
        holder.binding.sourceTranslationTitle.text = chapterTitle

        // set up fonts
        if (holder.layoutBuildNumber != layoutBuildNumber) {
            holder.layoutBuildNumber = layoutBuildNumber
            sourceLanguage?.let { source ->
                typography.formatTitle(
                    TranslationType.SOURCE,
                    holder.binding.sourceTranslationHeading,
                    source.slug,
                    source.direction
                )
                typography.formatTitle(
                    TranslationType.SOURCE,
                    holder.binding.sourceTranslationTitle,
                    source.slug,
                    source.direction
                )
                typography.format(
                    TranslationType.SOURCE,
                    holder.binding.sourceTranslationBody,
                    source.slug,
                    source.direction
                )
            }
        }
    }

    override fun getItemCount(): Int {
        return chapters.size
    }

    class ViewHolder(
        val binding: FragmentDraftListItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        var layoutBuildNumber: Int = -1
    }
}
