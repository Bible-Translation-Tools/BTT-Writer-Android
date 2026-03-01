package com.door43.util

import android.content.Context

/**
 * Created by joel on 2/24/17.
 */
object ColorUtil {
    /**
     * Returns a color with backwards compatibility for deprecated methods
     * @param context
     * @return
     */
    fun getColor(context: Context, resourceId: Int): Int {
        return context.resources.getColor(resourceId, context.theme)
    }
}
