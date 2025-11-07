package com.door43.widget

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.R
import com.google.android.material.snackbar.Snackbar

/**
 * This class provides utilities for views
 */
object ViewUtil {
    /**
     * Makes links in a textview clickable
     * includes support for long clicks
     * @param view
     */
    @JvmStatic
    fun makeLinksClickable(view: TextView) {
        val m = view.movementMethod
        if (m == null || m !is LongClickLinkMovementMethod) {
            if (view.linksClickable) {
                view.movementMethod = LongClickLinkMovementMethod.instance
            }
        }
    }

    /**
     * Sets the color of the snack bar text
     * @param snack
     * @param color
     */
    @JvmStatic
    fun setSnackBarTextColor(snack: Snackbar, color: Int) {
        val tv = snack.view.findViewById<TextView>(R.id.snackbar_text)
        tv.setTextColor(color)
    }

    /**
     * Provides a backwards compatible way to tint view drawables
     * @param view the view who's background drawable will be tinted
     * @param color the color that will be applied
     */
    @JvmStatic
    fun tintViewDrawable(view: View, color: Int) {
        val originalDrawable = view.background
        val wrappedDrawable = DrawableCompat.wrap(originalDrawable)
        DrawableCompat.setTintList(wrappedDrawable, ColorStateList.valueOf(color))
        view.background = wrappedDrawable
    }

    /**
     * Performs a stacked card animation that brings a bottom card to the front
     * In preparation two views should be stacked on top of each other with appropriate margin
     * so that the bottom card sticks out on the bottom and the right.
     *
     * @param topCard
     * @param bottomCard
     * @param topCardElevation
     * @param bottomCardElevation
     * @param leftToRight indicates which direction the animation of the top card should go.
     * @param listener
     */
    @JvmStatic
    fun animateSwapCards(
        topCard: View,
        bottomCard: View,
        topCardElevation: Int,
        bottomCardElevation: Int,
        leftToRight: Boolean,
        listener: Animation.AnimationListener?
    ) {
        val duration: Long = 400
        val xMargin = topCard.x - bottomCard.x
        val yMargin = topCard.y - bottomCard.y

        topCard.clearAnimation()
        bottomCard.clearAnimation()

        val topLayout = topCard.layoutParams
        val bottomLayout = bottomCard.layoutParams

        // bottom animation
        val upLeft: Animation = TranslateAnimation(0f, xMargin, 0f, yMargin)
        upLeft.duration = duration
        val bottomCardRight: Animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            .5f,
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            0f
        )
        bottomCardRight.duration = duration
        val bottomCardLeft: Animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            -.5f,
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            0f
        )
        bottomCardLeft.duration = duration

        val bottomOutSet = AnimationSet(false)
        if (leftToRight) {
            bottomOutSet.addAnimation(bottomCardLeft)
        } else {
            bottomOutSet.addAnimation(bottomCardRight)
        }
        val bottomInSet = AnimationSet(false)
        bottomInSet.startOffset = duration
        if (leftToRight) {
            bottomInSet.addAnimation(bottomCardRight)
            bottomInSet.addAnimation(upLeft)
        } else {
            bottomInSet.addAnimation(bottomCardLeft)
            bottomInSet.addAnimation(upLeft)
        }
        val bottomSet = AnimationSet(false)
        bottomSet.interpolator = LinearInterpolator()
        bottomSet.addAnimation(bottomOutSet)
        bottomSet.addAnimation(bottomInSet)
        bottomSet.setAnimationListener(listener)

        bottomOutSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
            }

            override fun onAnimationEnd(animation: Animation) {
                // elevation takes precedence for API 21+
                topCard.elevation = bottomCardElevation.toFloat()
                bottomCard.elevation = topCardElevation.toFloat()
                bottomCard.bringToFront()
                (bottomCard.parent as View).requestLayout()
                (bottomCard.parent as View).invalidate()
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })
        bottomInSet.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
            }

            override fun onAnimationEnd(animation: Animation) {
                bottomCard.clearAnimation()
                bottomCard.layoutParams = topLayout
                topCard.clearAnimation()
                topCard.layoutParams = bottomLayout
            }

            override fun onAnimationRepeat(animation: Animation) {
            }
        })

        // top animation
        val downRight: Animation = TranslateAnimation(0f, -xMargin, 0f, -yMargin)
        downRight.duration = duration
        val topCardRight: Animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            .5f,
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            0f
        )
        topCardRight.duration = duration
        val topCardLeft: Animation = TranslateAnimation(
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            -.5f,
            Animation.RELATIVE_TO_SELF,
            0f,
            Animation.RELATIVE_TO_SELF,
            0f
        )
        topCardLeft.duration = duration

        val topOutSet = AnimationSet(false)
        if (leftToRight) {
            topOutSet.addAnimation(topCardRight)
        } else {
            topOutSet.addAnimation(topCardLeft)
        }
        val topInSet = AnimationSet(false)
        topInSet.startOffset = duration
        if (leftToRight) {
            topInSet.addAnimation(topCardLeft)
            topInSet.addAnimation(downRight)
        } else {
            topInSet.addAnimation(topCardRight)
            topInSet.addAnimation(downRight)
        }

        val topSet = AnimationSet(false)
        topSet.interpolator = LinearInterpolator()
        topSet.addAnimation(topOutSet)
        topSet.addAnimation(topInSet)

        // start animations
        bottomCard.startAnimation(bottomSet)
        topCard.startAnimation(topSet)
    }

    /**
     * Forces a popup menu to display it's icons
     * @param popup
     */
    @JvmStatic
    fun forcePopupMenuIcons(popup: PopupMenu) {
        try {
            val fields = popup.javaClass.declaredFields
            for (field in fields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    val menuPopupHelper = field[popup]
                    val classPopupHelper = Class.forName(
                        menuPopupHelper
                            .javaClass.name
                    )
                    val setForceIcons = classPopupHelper.getMethod(
                        "setForceShowIcon", Boolean::class.javaPrimitiveType
                    )
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Converts a view to a bitmap
     * @param view
     * @return
     */
    @JvmStatic
    fun convertToBitmap(view: View): Bitmap {
        view.isDrawingCacheEnabled = true
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
        view.buildDrawingCache(true)
        return view.drawingCache
    }
}
