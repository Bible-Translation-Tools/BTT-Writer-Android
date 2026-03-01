package com.door43.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import it.moondroid.seekbarhint.library.SeekBarHint

/**
 * This class provides a seekbar that is reversed. e.g. operates from right to left
 */
class SeekbarHintReversed : SeekBarHint {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    // TODO: 10/4/16 we should turn this into a generic seek bar that allows vertical and horizontal orientation and also allow switching direction of seek.
//
//    override fun onDraw(canvas: Canvas) {
//        val px = this.width / 2.0f
//        val py = this.height / 2.0f
//
//        canvas.scale(-1f, 1f, px, py)
//
//        super.onDraw(canvas)
//    }
//
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        event.setLocation(this.width - event.x, event.y)
//
//        return super.onTouchEvent(event)
//    }
}