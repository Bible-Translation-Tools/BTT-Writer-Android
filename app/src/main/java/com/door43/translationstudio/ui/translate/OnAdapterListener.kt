package com.door43.translationstudio.ui.translate;

import android.content.ContentValues;
import android.view.View;

import com.google.android.material.tabs.TabLayout;

public interface OnAdapterListener {
    void onSourceTranslationTabClick(String sourceTranslationId);
    void onNewSourceTranslationTabClick();
    View onCreateRemovableTabLayout(String tag, String title);
    void onApplyLanguageTypefaceToTab(TabLayout layout, ContentValues values, String title);
}
