package com.door43.translationstudio.ui.spannables

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.door43.translationstudio.R

class LinkSpan(
    val title: String,
    val address: String,
    val type: String
) : Span(title, address) {

    private var spannable: SpannableStringBuilder? = null

    /**
     * Changes the title of the link
     * @param newTitle the new title to be set
     */
    fun setTitle(newTitle: String) {
        setHumanReadable(newTitle)
    }

    override fun render(): SpannableStringBuilder {
        if (spannable == null) {
            val s = super.render()
            // apply custom styles
            context?.let { ctx ->
                s.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.accent)),
                    0,
                    s.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            spannable = s
        }
        return spannable!!
    }
}