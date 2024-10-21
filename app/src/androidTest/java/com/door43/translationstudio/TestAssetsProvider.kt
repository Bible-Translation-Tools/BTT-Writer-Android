package com.door43.translationstudio

import androidx.test.platform.app.InstrumentationRegistry
import com.door43.data.AssetsProvider
import java.io.InputStream
import javax.inject.Inject

class TestAssetsProvider @Inject constructor() : AssetsProvider {
    override fun open(path: String): InputStream {
        return InstrumentationRegistry.getInstrumentation().context.assets.open(path)
    }
}