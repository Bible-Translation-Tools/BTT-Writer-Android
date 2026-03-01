package com.door43.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Created by blm on 12/7/2015.
 * LinearLayout with drawn lines
 */
class LinedLinearLayout : LinearLayout {
    private var rect = Rect()
    private var paint = Paint()
    private var lineHeight = 0
    private var yOffset = -1
    private var firstLineY = -1
    private var editText: LinedEditText? = null

    // ZERO-ALLOCATION FIX: Buffer for view coordinates to avoid allocating IntArray inside onDraw
    private val locationBuffer = IntArray(2)

    constructor(context: Context) : super(context) {
        drawInit()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        drawInit()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        drawInit()
    }

    override fun onDraw(canvas: Canvas) {
        // val `var` = true // Removed unused boolean from original Java code to clean up warnings

        if (enableLines) {
            val currentEditText = editText
            if (currentEditText != null) {
                // get view position on screen without allocating a new array
                this.getLocationOnScreen(locationBuffer)
                // val viewX = locationBuffer[0]
                val viewY = locationBuffer[1]

                // ZERO-ALLOCATION FIX: Reuse the existing rect to get the clip bounds
                canvas.getClipBounds(rect)
                val bottom = rect.bottom

                val relativeY = currentEditText.yLocation - viewY
                val distBetweenLines = currentEditText.distanceBetweenLines
                val offset = distBetweenLines / LinedEditText.relativeOffset // offset so that text is above line
                var position = currentEditText.linePosition + relativeY + offset

                for (i in 0..99) {
                    if (position > bottom) {
                        break
                    }

                    canvas.drawLine(
                        rect.left.toFloat(),
                        position.toFloat(),
                        rect.right.toFloat(),
                        position.toFloat(),
                        paint
                    )

                    position += distBetweenLines
                }
            }
        }

        super.onDraw(canvas)
    }

    var enableLines: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private fun drawInit() {
        rect = Rect()
        paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.color = -0x3b1801 // 0xFFC4E7FF converted to 32-bit signed int
    }

    fun setEditText(editText: LinedEditText) {
        this.editText = editText
    }
}