package com.door43.translationstudio.ui.spannables

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.SpannedString
import android.view.View
import android.widget.TextView
import com.door43.widget.LongClickableSpan

abstract class Span {
    var humanReadable: CharSequence = ""
        protected set

    var machineReadable: CharSequence = ""
        protected set

    var isClickable: Boolean = true
    var onClickListener: OnClickListener? = null
    var extras: Bundle? = null

    protected var context: Context? = null

    /**
     * Creates a new empty span.
     * This is useful for classes that extends this class because they may need to perform
     * some processing before fully initializing.
     * You should manually call init() if using this constructor
     */
    constructor() {
        init("", "")
    }

    /**
     * Creates a new span
     * @param humanReadable the human-readable title of the span
     * @param machineReadable the machine-readable definition of the span
     */
    internal constructor(humanReadable: CharSequence, machineReadable: CharSequence) {
        init(humanReadable, machineReadable)
    }

    /**
     * Initializes the span
     * @param humanReadable
     * @param machineReadable
     */
    protected fun init(humanReadable: CharSequence, machineReadable: CharSequence) {
        this.humanReadable = humanReadable
        this.machineReadable = machineReadable
    }

    protected fun setHumanReadable(text: String) {
        this.humanReadable = text
    }

    /**
     * Generates the span and hooks up the click listener.
     */
    open fun render(): SpannableStringBuilder {
        val spannable = if (humanReadable.toString().isNotEmpty()) {
            SpannableStringBuilder(humanReadable)
        } else {
            SpannableStringBuilder(machineReadable)
        }

        if (spannable.isNotEmpty()) {
            spannable.setSpan(
                SpannedString(machineReadable),
                0,
                spannable.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            if (isClickable) {
                val clickSpan = object : LongClickableSpan() {
                    override fun onLongClick(view: View) {
                        onClickListener?.let {
                            val tv = view as TextView
                            val s = tv.text as Spanned
                            val start = s.getSpanStart(this)
                            val end = s.getSpanEnd(this)
                            it.onLongClick(view, this@Span, start, end)
                        }
                    }

                    override fun onClick(view: View) {
                        onClickListener?.let {
                            val tv = view as TextView
                            val s = tv.text as Spanned
                            val start = s.getSpanStart(this)
                            val end = s.getSpanEnd(this)
                            it.onClick(view, this@Span, start, end)
                        }
                    }
                }
                spannable.setSpan(
                    clickSpan,
                    0,
                    spannable.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }

    /**
     * Returns the span as a CharSequence
     */
    fun toCharSequence(context: Context): CharSequence {
        this.context = context
        return render()
    }

    /**
     * Custom click listener when span is clicked
     */
    interface OnClickListener {
        fun onClick(view: View, span: Span, start: Int, end: Int)
        fun onLongClick(view: View, span: Span, start: Int, end: Int)
    }
}