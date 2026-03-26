package com.door43.widget

import android.content.res.ColorStateList
import android.view.View
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
     * Sets the color of the snack bar text
     * @param snack
     * @param color
     */
    fun setSnackBarTextColor(snack: Snackbar, color: Int) {
        val tv = snack.view.findViewById<TextView>(R.id.snackbar_text)
        tv.setTextColor(color)
    }

    /**
     * Provides a backwards compatible way to tint view drawables
     * @param view the view whose background drawable will be tinted
     * @param color the color that will be applied
     */
    fun tintViewDrawable(view: View, color: Int) {
        val originalDrawable = view.background
        val wrappedDrawable = DrawableCompat.wrap(originalDrawable)
        DrawableCompat.setTintList(wrappedDrawable, ColorStateList.valueOf(color))
        view.background = wrappedDrawable
    }

    /**
     * Forces a popup menu to display its icons
     * @param popup
     */
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
}
