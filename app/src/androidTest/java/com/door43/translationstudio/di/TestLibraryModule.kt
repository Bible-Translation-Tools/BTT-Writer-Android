package com.door43.translationstudio.di

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.di.LibraryModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.mockk.spyk
import org.unfoldingword.door43client.Door43Client
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LibraryModule::class]
)
class TestLibraryModule {
    @Provides
    @Singleton
    fun provideLibrary(
        @ApplicationContext context: Context,
        directoryProvider: IDirectoryProvider,
    ): Door43Client {
        directoryProvider.deployDefaultLibrary()
        return spyk(Door43Client(context, directoryProvider))
    }
}