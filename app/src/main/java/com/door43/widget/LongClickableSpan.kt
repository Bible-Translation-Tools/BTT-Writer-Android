package com.door43.widget

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

/**
 * This class is the same as URLSpan except it does not underline the text
 */
abstract class LongClickableSpan : ClickableSpan() {
    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = false
    }

    abstract fun onLongClick(view: View)

    abstract override fun onClick(view: View)
}
