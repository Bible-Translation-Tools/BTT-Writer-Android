package com.door43.translationstudio.di

import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import io.mockk.spyk
import org.bibletranslationtools.resourcecatalog.ResourceCatalogClient
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val testDataModule = module {
    singleOf(::TestAssetsProvider).bind<AssetsProvider>()
    single {
        val directoryProvider: IDirectoryProvider = get()
        directoryProvider.deployDefaultLibrary()
        spyk(ResourceCatalogClient(
            directoryProvider.databaseFile,
            directoryProvider.containersDir
        ))
    }
}