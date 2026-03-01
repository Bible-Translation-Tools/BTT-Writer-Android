package com.door43.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import it.moondroid.seekbarhint.library.R

/**
 * 7/1/2016
 * modified SeekBarHint to work with VerticalSeekBar
 */
class VerticalSeekBarHint : VerticalSeekBar, SeekBar.OnSeekBarChangeListener {

    private var popupLayout = 0
    private var popupWidth = 0
    var popupStyle = POPUP_FIXED

    private lateinit var popup: PopupWindow
    private lateinit var popupTextView: TextView
    private var xLocationOffset = 0
    private var yLocationOffset = 0
    private val seekbarRectangle = Rect()

    private var internalListener: OnSeekBarChangeListener? = null
    private var externalListener: OnSeekBarChangeListener? = null

    private var progressChangeListener: OnSeekBarHintProgressChangeListener? = null

    fun interface OnSeekBarHintProgressChangeListener {
        fun onHintTextChanged(seekBarHint: VerticalSeekBarHint, progress: Int): String?
    }

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        setOnSeekBarChangeListener(this)

        context.withStyledAttributes(
            attrs,
            R.styleable.SeekBarHint
        ) {
            popupLayout = getResourceId(
                R.styleable.SeekBarHint_popupLayout,
                R.layout.popup
            )
            popupWidth = getDimension(
                R.styleable.SeekBarHint_popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT.toFloat()
            ).toInt()
            yLocationOffset = getDimension(
                R.styleable.SeekBarHint_yOffset,
                0f
            ).toInt()
            xLocationOffset = getDimension(
                R.styleable.SeekBarHint_xOffset,
                0f
            ).toInt()
            popupStyle = getInt(
                R.styleable.SeekBarHint_popupStyle,
                POPUP_FIXED
            )

        }
        initHintPopup()
    }

    private fun initHintPopup() {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val undoView = inflater.inflate(popupLayout, null)
        popupTextView = undoView.findViewById<View>(R.id.text) as TextView

        initPopupText()

        this.getGlobalVisibleRect(seekbarRectangle)
        // Log.d(TAG,"initHintPopup: Rect=" + mSeekbarRectangle)

        popup = PopupWindow(undoView, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, false)
        popup.animationStyle = R.style.fade_animation
    }

    private fun initPopupText() {
        var popupText: String? = null
        if (progressChangeListener != null) {
            popupText = progressChangeListener!!.onHintTextChanged(this, progress)
        }
        if (popupText == null) {
            popupText = progress.toString()
        }
        popupTextView.text = popupText
        // Log.d(TAG,"initPopupText: popupText=" + popupText)
    }

    private fun showPopup() {
        getMeasurements()
        initPopupText()

        if (popupStyle == POPUP_FOLLOW) {
            val xPosition = getXPosition()
            val yPosition = getYPosition(this)
            // Log.d(TAG,"showPopup: show Hint at =" + xPosition + "," + yPosition)
            popup.showAtLocation(this, Gravity.LEFT or Gravity.BOTTOM, xPosition, yPosition)
        }
        if (popupStyle == POPUP_FIXED) {
            val xPosition = getXPosition()
            val yPosition = 0
            // Log.d(TAG,"showPopup: show Hint at =" + xPosition + "," + yPosition)
            popup.showAtLocation(this, Gravity.LEFT or Gravity.CENTER, xPosition, yPosition)
        }
    }

    private fun getMeasurements() {
        // ZERO-ALLOCATION FIX: Reuse the existing rect to avoid allocating objects during drag updates
        this.getGlobalVisibleRect(seekbarRectangle)
        // Log.d(TAG,"getMeasurements: Rect=" + mSeekbarRectangle)
    }

    private fun getXPosition(): Int {
        val textWidth = popupWidth
        val textCenter = textWidth / 2.0f
        val x = (this.x + textCenter + xLocationOffset + this.width).toInt()
        // Log.d(TAG,"mXLocationOffset: " + mXLocationOffset)
        // Log.d(TAG,"getWidth(): " + this.getWidth())
        // Log.d(TAG,"getXPosition: " + x)
        return x
    }

    private fun getYPosition(seekBar: SeekBar): Int {
        val y = seekbarRectangle.top + yLocationOffset + getYOffset(seekBar).toInt()
        // Log.d(TAG,"mYLocationOffset: " + mYLocationOffset)
        // Log.d(TAG,"getYPosition: " + y)
        return y
    }

    private fun hidePopup() {
        if (popup.isShowing) {
            popup.dismiss()
        }
    }

    fun setHintView(view: View?) {
        // TODO
        // initHintPopup()
    }

    override fun setOnSeekBarChangeListener(l: OnSeekBarChangeListener?) {
        if (internalListener == null) {
            internalListener = l
            super.setOnSeekBarChangeListener(l)
        } else {
            externalListener = l
        }
    }

    fun setOnProgressChangeListener(l: OnSeekBarHintProgressChangeListener?) {
        progressChangeListener = l
    }

    override fun onProgressChanged(seekBar: SeekBar, progress: Int, b: Boolean) {
        var popupText: String? = null

        if (progressChangeListener != null) {
            popupText = progressChangeListener!!.onHintTextChanged(this, progress)
        }

        if (externalListener != null) {
            externalListener!!.onProgressChanged(seekBar, progress, b)
        }

        if (popupText == null) {
            popupText = this.progress.toString()
        }
        popupTextView.text = popupText
        // Log.d(TAG,"onProgressChanged: popupText=" + popupText)

        if (popupStyle == POPUP_FOLLOW) {
            getMeasurements()
            val x = getXPosition()
            val y = getYPosition(seekBar)
            popup.update(x, y, -1, -1)
            // Logger.i(TAG,"onProgressChanged: new Hint =" + x + "," + y)
        }
    }

    private fun limitProgress(seekBar: SeekBar, progress: Int): Int {
        var safeProgress = progress
        val max = seekBar.max
        if (safeProgress > max) {
            safeProgress = max
        }
        if (safeProgress < 0) {
            safeProgress = 0
        }
        return safeProgress
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) {
        if (externalListener != null) {
            externalListener!!.onStartTrackingTouch(seekBar)
        }

        showPopup()
    }

    override fun onStopTrackingTouch(seekBar: SeekBar) {
        if (externalListener != null) {
            externalListener!!.onStopTrackingTouch(seekBar)
        }

        hidePopup()
    }

    private fun getYOffset(seekBar: SeekBar): Float {
        val progress = limitProgress(seekBar, seekBar.progress).toFloat()
        // Log.d(TAG,"getYOffset: progress=" + progress)
        val seekBarMax = seekBar.max
        // Log.d(TAG,"getYOffset: seekBarMax=" + seekBarMax)
        val seekBarHeight = seekBar.height
        val seekBarThumbOffset = seekBar.thumbOffset
        // Log.d(TAG,"getYOffset: seekBarThumbOffset=" + seekBarThumbOffset)
        val maxScale = (seekBarHeight - 2 * seekBarThumbOffset).toFloat()
        val position = progress * maxScale / seekBarMax
        // Log.d(TAG,"getYOffset: position=" + position)
        val offset = seekBarThumbOffset.toFloat()
        // Log.d(TAG,"getYOffset: offset=" + offset)

        val height = popup.height
        val center = height / 2

        val newY = position + offset + center
        // Log.d(TAG,"getYOffset: newY=" + newY)
        return newY
    }

    companion object {
        val TAG: String = VerticalSeekBarHint::class.java.simpleName
        const val POPUP_FIXED = 1
        const val POPUP_FOLLOW = 0
    }
}