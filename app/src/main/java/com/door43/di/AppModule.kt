package com.door43.di

import android.content.Context
import com.door43.data.IDirectoryProvider
import com.door43.data.ILanguageRequestRepository
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.repositories.LanguageRequestRepository
import com.door43.translationstudio.core.ArchiveImporter
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.Translator
import com.door43.usecases.BackupRC
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
    fun provideLanguageRequestRepository(
        @ApplicationContext context: Context,
        directoryProvider: IDirectoryProvider,
        library: Door43Client
    ): ILanguageRequestRepository {
        return LanguageRequestRepository(context, directoryProvider, library)
    }

    @Provides
    @Singleton
    fun provideProfile(
        pref: IPreferenceRepository,
        directoryProvider: IDirectoryProvider
    ) : Profile {
        return try {
            val profileString = pref.getDefaultPref<String>("profile")
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
        directoryProvider: IDirectoryProvider,
        prefRepository: IPreferenceRepository,
        archiveImporter: ArchiveImporter,
        backupRC: BackupRC,
        library: Door43Client
    ): Translator {
        return Translator(
            context,
            profile,
            prefRepository,
            directoryProvider,
            archiveImporter,
            backupRC,
            library
        )
    }
}