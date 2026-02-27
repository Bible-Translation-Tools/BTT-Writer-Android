package com.door43.translationstudio.ui.translate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View.DragShadowBuilder

/**
 * Created by joel on 10/4/2015.
 */
class CustomDragShadowBuilder private constructor() : DragShadowBuilder() {
    private lateinit var shadow: Drawable

    override fun onDrawShadow(canvas: Canvas) {
        shadow.draw(canvas)
    }

    override fun onProvideShadowMetrics(shadowSize: Point, shadowTouchPoint: Point) {
        shadowSize.x = shadow.minimumWidth
        shadowSize.y = shadow.minimumHeight

        shadowTouchPoint.x = (shadowSize.x / 2)
        shadowTouchPoint.y = (shadowSize.y + 36)
    }

    companion object {
        fun fromResource(context: Context, drawableId: Int): DragShadowBuilder {
            val builder = CustomDragShadowBuilder()

            builder.shadow = context.resources.getDrawable(drawableId, null)

            builder.shadow.setBounds(
                0,
                0,
                builder.shadow.minimumWidth,
                builder.shadow.minimumHeight
            )

            return builder
        }

        fun fromBitmap(context: Context, bmp: Bitmap): DragShadowBuilder {
            val builder = CustomDragShadowBuilder()

            builder.shadow = BitmapDrawable(context.resources, bmp)
            builder.shadow.setBounds(
                0,
                0,
                builder.shadow.minimumWidth,
                builder.shadow.minimumHeight
            )

            return builder
        }
    }
}