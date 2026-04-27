package com.door43.translationstudio.core

/**
 * Created by blm on 4/14/16.
 */
data class MissingNameItem(
    val description: String?,
    val invalidName: String?,
    val contents: String?
) {
    companion object {
        val TAG: String = MissingNameItem::class.java.simpleName
    }
}


