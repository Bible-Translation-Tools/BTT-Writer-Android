package com.door43.translationstudio.di

//import android.content.Context
//import com.door43.data.IPreferenceRepository
//import com.door43.di.PreferenceRepoModule
//import dagger.Module
//import dagger.Provides
//import dagger.hilt.android.qualifiers.ApplicationContext
//import dagger.hilt.components.SingletonComponent
//import dagger.hilt.testing.TestInstallIn
//import javax.inject.Singleton
//
//@Module
//@TestInstallIn(
//    components = [SingletonComponent::class],
//    replaces = [PreferenceRepoModule::class]
//)
//class TestPreferenceRepoModule {
//    @Provides
//    @Singleton
//    fun providePreferenceRepository(
//        @ApplicationContext context: Context
//    ): IPreferenceRepository {
//        return TestPreferenceRepository(context)
//    }
//}