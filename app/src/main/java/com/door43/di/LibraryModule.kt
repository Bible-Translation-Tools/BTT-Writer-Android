package com.door43.di

import android.content.Context
import com.door43.data.IDirectoryProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.unfoldingword.door43client.Door43Client
import java.io.IOException

@Module
@InstallIn(SingletonComponent::class)
class LibraryModule {

    @Provides
    fun provideLibrary(
        @ApplicationContext context: Context,
        directoryProvider: IDirectoryProvider
    ): Door43Client {
        return try {
            Door43Client(context, directoryProvider)
        } catch (e: IOException) {
            throw NullPointerException("Failed to initialize the door43 client")
        }
    }
}