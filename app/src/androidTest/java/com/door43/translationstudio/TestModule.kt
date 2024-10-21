package com.door43.translationstudio

import com.door43.data.AssetsProvider
import com.door43.di.Development
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TestModule {
    @Development
    @Provides
    @Singleton
    fun provideAssetsProvider(): AssetsProvider {
        return TestAssetsProvider()
    }
}