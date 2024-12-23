package com.door43.translationstudio

import android.content.Context
import android.content.res.AssetManager
import com.door43.data.AssetsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream


class MainAssetsProvider(
    @ApplicationContext private val context: Context
) : AssetsProvider {
    override val manager: AssetManager = context.assets

    override fun open(path: String): InputStream {
        return context.assets.open(path)
    }

    override fun list(path: String): Array<String>? {
        return context.assets.list(path)
    }
}