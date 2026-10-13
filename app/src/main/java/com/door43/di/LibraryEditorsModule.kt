package com.door43.di

import com.door43.translationstudio.ui.devtools.library.LibraryTableEditor
import com.door43.translationstudio.ui.devtools.library.TargetLanguageEditor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * Registers editors for library tables available in Developer Tools -> Edit Library.
 * To support a new table: add a LibraryTable entry, implement LibraryTableEditor, bind it here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryEditorsModule {

    @Binds
    @IntoSet
    abstract fun bindTargetLanguageEditor(editor: TargetLanguageEditor): LibraryTableEditor
}
