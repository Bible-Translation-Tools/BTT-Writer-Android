package com.door43.di

import android.content.Context
import com.door43.data.AssetsProvider
import com.door43.translationstudio.MainAssetsProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AssetsModule {
    @Provides
    @Singleton
    fun provideAssetsProvider(@ApplicationContext context: Context): AssetsProvider {
        return MainAssetsProvider(context)
    }
}