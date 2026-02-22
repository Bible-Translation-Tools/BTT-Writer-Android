package com.door43.translationstudio.di

import android.content.Context
import com.door43.data.AssetsProvider
import com.door43.data.IDirectoryProvider
import io.mockk.spyk
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.unfoldingword.door43client.Door43Client

val testDataModule = module {
    singleOf(::TestAssetsProvider).bind<AssetsProvider>()
    single {
        val context: Context = get()
        val directoryProvider: IDirectoryProvider = get()
        directoryProvider.deployDefaultLibrary()
        spyk(Door43Client(context, directoryProvider))
    }
}