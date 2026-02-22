package com.door43.translationstudio

import android.app.Application
import com.door43.di.appModule
import com.door43.translationstudio.di.testDataModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class KoinTestApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(Level.DEBUG)
            androidContext(this@KoinTestApplication)
            modules(appModule, testDataModule)
        }
    }
}
