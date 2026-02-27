package com.door43.translationstudio.ui.spannables

import android.text.SpannableStringBuilder

class USFMParagraphSpan : ParagraphSpan("\n", "\\p ") {

    private var spannable: SpannableStringBuilder? = null

    companion object {
        const val PATTERN = "\\\\p\\W?"
    }

    /**
     * Generates the spannable.
     * This provides caching so we can look up the span in the text later
     */
    override fun render(): SpannableStringBuilder {
        if (spannable == null) {
            spannable = super.render()
        }
        return spannable!!
    }
}