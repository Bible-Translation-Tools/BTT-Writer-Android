package com.door43.translationstudio.di

import com.door43.data.AssetsProvider
import com.door43.di.AssetsModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AssetsModule::class]
)
class TestAssetsModule {
    @Provides
    @Singleton
    fun provideAssetsProvider(): AssetsProvider {
        return TestAssetsProvider()
    }
}