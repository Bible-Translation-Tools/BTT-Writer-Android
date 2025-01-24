package com.door43.widget

import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.view.MotionEvent
import android.widget.TextView
import androidx.core.text.getSpans
import kotlin.math.roundToInt

class LongClickLinkMovementMethod private constructor(): LinkMovementMethod() {
    private var lastClickTime = 0L
    private var mLongPressRunnable: Runnable? = null

    override fun onTouchEvent(
        widget: TextView,
        buffer: Spannable,
        event: MotionEvent
    ): Boolean {
        val action = event.action

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_DOWN) {
            var x = event.x
            var y = event.y

            x -= widget.totalPaddingLeft
            y -= widget.totalPaddingTop

            x += widget.scrollX
            y += widget.scrollY

            val link = getClosestSpan(widget, buffer, x, y)

            if (link != null) {
                if (mLongPressRunnable != null) {
                    widget.removeCallbacks(mLongPressRunnable)
                }

                mLongPressRunnable = Runnable {
                    link.onLongClick(widget)
                }

                if (action == MotionEvent.ACTION_UP) {
                    if (System.currentTimeMillis() - lastClickTime < LONG_CLICK_DELAY) {
                        link.onClick(widget)
                    } else {
                        link.onLongClick(widget)
                    }
                } else {
                    Selection.setSelection(
                        buffer,
                        buffer.getSpanStart(link),
                        buffer.getSpanEnd(link)
                    )
                    lastClickTime = System.currentTimeMillis()
                    widget.postDelayed(mLongPressRunnable, LONG_CLICK_DELAY.toLong())
                }
                return true
            }
        }

        return super.onTouchEvent(widget, buffer, event)
    }

    private fun getClosestSpan(
        widget: TextView,
        content: Spannable,
        x: Float,
        y: Float
    ): LongClickableSpan? {
        val layout = widget.layout
        val line = layout.getLineForVertical(y.roundToInt())
        val offset = layout.getOffsetForHorizontal(line, x)

        val spans = content.getSpans<LongClickableSpan>(offset, offset)

        if (spans.size == 1) return spans[0]

        return spans.singleOrNull { span ->
            val start = content.getSpanStart(span)
            val end = content.getSpanEnd(span)
            val spanLine = layout.getLineForOffset(start)
            val left = layout.getPrimaryHorizontal(start)
            val right = layout.getPrimaryHorizontal(end)
            val top = layout.getLineTop(spanLine)
            val bottom = layout.getLineBottom(spanLine)

            x in left..right && y.toInt() in top..bottom
        }
    }

    companion object {
        private const val LONG_CLICK_DELAY = 400

        val instance: MovementMethod
            get() {
                if (sInstance == null) sInstance = LongClickLinkMovementMethod()
                return sInstance!!
            }

        private var sInstance: LongClickLinkMovementMethod? = null
    }
}