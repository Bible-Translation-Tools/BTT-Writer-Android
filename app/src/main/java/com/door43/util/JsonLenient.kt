package com.door43.util

import kotlinx.serialization.json.Json

val JsonLenient = Json {
    isLenient = true
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
}