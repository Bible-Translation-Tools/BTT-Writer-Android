package com.door43.data

import java.io.InputStream

interface AssetsProvider {
    fun open(path: String): InputStream
}