package com.door43.data

import android.content.res.AssetManager
import java.io.InputStream

interface AssetsProvider {
    val manager: AssetManager
    fun open(path: String): InputStream
    fun list(path: String): Array<String>?
}