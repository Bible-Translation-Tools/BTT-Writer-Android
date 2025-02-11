package com.door43.translationstudio.di

import android.content.res.AssetManager
import androidx.test.platform.app.InstrumentationRegistry
import com.door43.data.AssetsProvider
import java.io.InputStream
import javax.inject.Inject

class TestAssetsProvider @Inject constructor() : AssetsProvider {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    override val manager: AssetManager = instrumentation.context.assets

    override fun open(path: String): InputStream {
        return instrumentation.context.assets.open(path)
    }

    override fun list(path: String): Array<String>? {
        return instrumentation.context.assets.list(path)
    }
}