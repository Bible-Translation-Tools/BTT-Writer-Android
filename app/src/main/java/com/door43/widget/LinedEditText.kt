package com.door43.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

/**
 * Created by blm on 11/25/15.
 */
class LinedEditText(context: Context, attrs: AttributeSet) : AppCompatEditText(context, attrs) {
    private val rect = Rect()
    private val paint = Paint()

    init {
        paint.style = Paint.Style.STROKE
        paint.color = -0x3b1801 // 0xFFC4E7FF converted to 32-bit signed int
    }

    override fun onDraw(canvas: Canvas) {
        val parent = linedParent

        if (parent != null) {
            // if on top of LinedLinearLayout, draw lines on it
            parent.setEditText(this)
        } else if (enableLines) {
            // if not paired, draw line on edittext if enabled
            val count = lineCount
            val r = rect

            // ZERO-ALLOCATION FIX: Reuse the existing rect to get the clip bounds
            canvas.getClipBounds(r)
            val bottom = r.bottom

            val lineHeight = lineHeight
            val offset = lineHeight / relativeOffset // offset so that text is above line

            var position = 0

            for (i in 0..99) {  // 100 is just here for a sanity limit to max number of lines
                if (i < count) {
                    position = getLineBounds(i, r) + offset
                } else {
                    // keep drawing below last text line
                    position += lineHeight
                }

                if (position > bottom) {
                    // done when we have filled the view
                    break
                }

                canvas.drawLine(
                    r.left.toFloat(),
                    position.toFloat(),
                    r.right.toFloat(),
                    position.toFloat(),
                    paint
                )
            }
        }

        super.onDraw(canvas)
    }

    val yLocation: Int
        get() {
            // get view position on screen
            val l = IntArray(2)
            this.getLocationOnScreen(l)
            // val viewX = l[0]
            return l[1] // viewY
        }

    val distanceBetweenLines: Int
        get() = lineHeight

    val linePosition: Int
        get() {
            // val offset = lineHeight / relativeOffset // offset so that text is above line
            val r = rect
            return getLineBounds(0, r)
        }

    var enableLines: Boolean = false
        get() = linedParent?.enableLines ?: field
        set(value) {
            linedParent?.enableLines = value
            field = value
            invalidate()
        }

    private val linedParent: LinedLinearLayout?
        get() {
            var parent = this.parent

            for (i in 0..1) { // maximum levels
                if (parent == null) {
                    break
                }

                val paired = parent is LinedLinearLayout

                if (paired) {
                    return parent
                }

                parent = parent.parent // try moving up
            }
            return null
        }

    companion object {
        var relativeOffset = 8
    }
}