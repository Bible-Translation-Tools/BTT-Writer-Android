package com.door43.di

import android.content.Context
import com.door43.data.IPreferenceRepository
import com.door43.repositories.PreferenceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class PreferenceRepoModule {
    @Provides
    @Singleton
    fun providePreferenceRepository(
        @ApplicationContext context: Context
    ): IPreferenceRepository {
        return PreferenceRepository(context)
    }
}