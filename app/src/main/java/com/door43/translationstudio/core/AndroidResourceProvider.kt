package com.door43.translationstudio.core

import android.content.Context

class AndroidResourceProvider(private val context: Context) : ResourceProvider {
    
    override fun getString(resourceId: Int): String {
        return context.getString(resourceId)
    }

    override fun getStringArray(resourceId: Int): List<String> {
        return context.resources.getStringArray(resourceId).toList()
    }
}