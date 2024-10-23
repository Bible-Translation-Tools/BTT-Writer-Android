package com.door43.di

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.DirectoryProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DirectoryModule {
    @Provides
    @Singleton
    fun provideDirectoryProvider(
        @ApplicationContext context: Context
    ): IDirectoryProvider {
        return DirectoryProvider(context)
    }
}