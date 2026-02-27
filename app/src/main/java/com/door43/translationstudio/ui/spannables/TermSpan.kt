package com.door43.translationstudio.ui.spannables

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.door43.translationstudio.R

class TermSpan(
    val termId: String,
    text: String
) : Span(text, text) {

    private var spannable: SpannableStringBuilder? = null

    companion object {
        const val PATTERN = "<keyterm>(((?!</keyterm>).)*)</keyterm>"
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