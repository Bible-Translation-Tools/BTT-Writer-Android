package com.door43.translationstudio.core

interface ResourceProvider {
    fun getString(resourceId: Int): String
    fun getStringArray(resourceId: Int): List<String>
}