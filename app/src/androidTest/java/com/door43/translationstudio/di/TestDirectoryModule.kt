package com.door43.translationstudio.di

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.di.DirectoryModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DirectoryModule::class]
)
object TestDirectoryModule {
    @Provides
    @Singleton
    fun provideDirectoryProvider(
        @ApplicationContext context: Context
    ): IDirectoryProvider {
        return TestDirectoryProvider(context)
    }
}