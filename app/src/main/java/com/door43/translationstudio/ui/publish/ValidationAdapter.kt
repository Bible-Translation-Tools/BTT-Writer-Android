package com.door43.translationstudio.ui.publish

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.FragmentPublishValidationListItemBinding
import com.door43.translationstudio.rendering.Clickables
import com.door43.translationstudio.rendering.DefaultRenderer
import com.door43.translationstudio.rendering.RenderingGroup
import com.door43.widget.ViewUtil

/**
 * Created by joel on 9/20/2015.
 */
class ValidationAdapter(
    private val typography: Typography
) : RecyclerView.Adapter<ValidationAdapter.ViewHolder>() {

    private val validations = arrayListOf<ValidationItem>()
    private var renderedText = arrayOfNulls<CharSequence>(validations.size)
    private var listener: OnClickListener? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = FragmentPublishValidationListItemBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val context = holder.binding.root.context

        if (position == itemCount - 1) {
            holder.binding.nextLayout.visibility = View.VISIBLE
            holder.binding.cardContainer.visibility = View.GONE

            // next button
            holder.binding.nextButton.setOnClickListener {
                listener?.onClickNext()
            }
        } else {
            holder.binding.nextLayout.visibility = View.GONE
            holder.binding.cardContainer.visibility = View.VISIBLE

            // validation item
            val item = validations[position]

            // margin
            val p = holder.binding.cardContainer.layoutParams as MarginLayoutParams
            val stackedCardMargin =
                context.resources.getDimensionPixelSize(R.dimen.stacked_card_margin)
            if (item.isFrame) {
                p.setMargins(stackedCardMargin, 0, 0, 0)
            } else {
                p.setMargins(0, 0, 0, 0)
            }
            holder.binding.cardContainer.requestLayout()

            // title
            holder.binding.title.text = item.title
            typography.format(
                TranslationType.TARGET,
                holder.binding.title,
                item.titleLanguage.slug,
                item.titleLanguage.direction
            )

            // icon
            if (item.isValid) {
                if (item.isRange) {
                    holder.binding.icon.setBackgroundResource(R.drawable.ic_done_all_black_24dp)
                } else {
                    holder.binding.icon.setBackgroundResource(R.drawable.ic_done_black_24dp)
                }
                ViewUtil.tintViewDrawable(
                    holder.binding.icon,
                    context.resources.getColor(R.color.completed)
                )
            } else {
                holder.binding.icon.setBackgroundResource(R.drawable.ic_report_black_24dp)
                ViewUtil.tintViewDrawable(holder.binding.icon, context.resources.getColor(R.color.warning))
            }

            // stack
            if (item.isRange) {
                holder.binding.stackedCard.visibility = View.VISIBLE
            } else {
                holder.binding.stackedCard.visibility = View.GONE
            }

            // body
            if (item.isFrame && !item.isValid) {
                holder.binding.icon.visibility = View.GONE
                holder.binding.reviewButton.visibility = View.VISIBLE
                holder.binding.body.visibility = View.VISIBLE
                if (renderedText[position] == null) {
                    val renderingGroup = RenderingGroup()
                    val format = item.bodyFormat
                    if (Clickables.isClickableFormat(format)) {
                        Clickables.setupRenderingGroup(format, renderingGroup, null, null, true)
                    } else {
                        renderingGroup.addEngine(DefaultRenderer(null))
                    }
                    renderingGroup.init(item.body)
                    renderedText[position] = renderingGroup.start()
                }
                holder.binding.body.text = renderedText[position]
                typography.formatSub(
                    TranslationType.TARGET,
                    holder.binding.body,
                    item.bodyLanguage.slug,
                    item.bodyLanguage.direction
                )
            } else {
                holder.binding.body.visibility = View.GONE
                holder.binding.reviewButton.visibility = View.GONE
                holder.binding.icon.visibility = View.VISIBLE
            }

            holder.binding.reviewButton.setOnClickListener {
                listener?.onClickReview(item.targetTranslationId, item.chapterId, item.frameId)
            }
        }
    }

    override fun getItemCount(): Int {
        return if (validations.size > 0) {
            // leave room for the next button
            validations.size + 1
        } else {
            0
        }
    }

    fun setValidations(validations: List<ValidationItem>) {
        this.validations.clear()
        this.validations.addAll(validations)
        renderedText = arrayOfNulls(validations.size)
        notifyDataSetChanged()
    }

    fun setOnClickListener(listener: OnClickListener?) {
        this.listener = listener
    }

    class ViewHolder(
        val binding: FragmentPublishValidationListItemBinding
    ) : RecyclerView.ViewHolder(binding.root)

    interface OnClickListener {
        fun onClickReview(targetTranslationId: String?, chapterId: String?, frameId: String?)
        fun onClickNext()
    }
}
