package com.door43.translationstudio.core

import kotlinx.coroutines.CoroutineScope

interface ComponentScope {
    val coroutineScope: CoroutineScope
}