package com.door43.di

import android.content.Context
import com.door43.translationstudio.DirectoryProvider
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.repositories.PreferenceRepository
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.json.JSONObject
import org.unfoldingword.door43client.Door43Client
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDirectoryProvider(
        @ApplicationContext context: Context
    ): IDirectoryProvider {
        return DirectoryProvider(context)
    }

    @Provides
    @Singleton
    fun providePreferenceRepository(
        @ApplicationContext context: Context
    ): IPreferenceRepository {
        return PreferenceRepository(context)
    }

    @Provides
    @Singleton
    fun provideLibrary(
        @ApplicationContext context: Context,
        directoryProvider: IDirectoryProvider
    ): Door43Client {
        return Door43Client(
            context,
            directoryProvider.databaseFile,
            directoryProvider.containersDir
        )
    }

    @Provides
    @Singleton
    fun provideProfile(
        pref: IPreferenceRepository,
        directoryProvider: IDirectoryProvider
    ) : Profile {
        return try {
            val profileString = pref.getDefaultPref("profile")
            Profile.fromJSON(pref, directoryProvider, profileString?.let { JSONObject(it) })
        } catch (e: Exception) {
            throw e
        }
    }

    @Provides
    @Singleton
    fun provideTranslator(
        @ApplicationContext context: Context,
        profile: Profile,
        directoryProvider: IDirectoryProvider
    ): Translator {
        return Translator(context, profile, directoryProvider.translationsDir)
    }
}