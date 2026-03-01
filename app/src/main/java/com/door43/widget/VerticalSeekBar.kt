package com.door43.widget

import android.content.Context
import android.graphics.Canvas
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

open class VerticalSeekBar : AppCompatSeekBar {

    private var internalListener: OnSeekBarChangeListener? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        this.internalListener = l
    }

    override fun onDraw(c: Canvas) {
        c.rotate(-90f)
        c.translate(-height.toFloat(), 0f)

        super.onDraw(c)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                internalListener?.onStartTrackingTouch(this)
            }
            MotionEvent.ACTION_MOVE -> {
                progress = max - (max * event.y / height).toInt()
                onSizeChanged(width, height, 0, 0)
                internalListener?.onProgressChanged(
                    this,
                    max - (max * event.y / height).toInt(),
                    true
                )
            }
            MotionEvent.ACTION_UP -> {
                internalListener?.onStopTrackingTouch(this)
            }
            MotionEvent.ACTION_CANCEL -> {}
        }
        return true
    }

    @Synchronized
    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        onSizeChanged(width, height, 0, 0)
    }

    override fun onSaveInstanceState(): Parcelable? {
        // Store the actual progress (not the internal inverted progress), to allow restoring as
        // HorizontalScrollBar, which is not inverted. Do this by temporarily removing the inversion
        // prior to saving the instance state.
        super.setProgress(max - progress)
        val result = super.onSaveInstanceState()
        super.setProgress(max - progress)
        return result
    }

    override fun onRestoreInstanceState(instanceState: Parcelable) {
        super.onRestoreInstanceState(instanceState)

        // Since the instance state is saved without being inverted, restore the inverted internal
        // format on restore.
        super.setProgress(max - progress)
    }
}