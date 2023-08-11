package com.door43.translationstudio.ui.spannables;

import android.text.SpannableStringBuilder;

public class USFMParagraphSpan extends ParagraphSpan {

    public static final String PATTERN = "\\\\p[^a-z]";

    private SpannableStringBuilder mSpannable;

    public USFMParagraphSpan() {
        super("\n", "\\p ");
    }

    /**
     * Generates the spannable.
     * This provides caching so we can look up the span in the text later
     * @return
     */
    @Override
    public SpannableStringBuilder render() {
        if(mSpannable == null) {
            mSpannable = super.render();
        }
        return mSpannable;
    }
}
