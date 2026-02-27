package com.door43.translationstudio.ui.translate

import android.content.ContentValues
import android.view.View
import com.google.android.material.tabs.TabLayout

interface OnViewModeListener {
    fun onSourceRemoveButtonClicked(sourceTranslationId: String)
    fun onSourceTranslationTabClick(sourceTranslationId: String)
    fun onNewSourceTranslationTabClick()
    fun onCreateRemovableTabLayout(tag: String, title: String): View?
    fun onApplyLanguageTypefaceToTab(layout: TabLayout, values: ContentValues, title: String)
}
