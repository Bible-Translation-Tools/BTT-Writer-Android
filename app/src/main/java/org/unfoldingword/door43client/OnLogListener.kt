package org.unfoldingword.door43client

/**
 * A utility to receive log events from the module
 */
interface OnLogListener {
    fun onInfo(message: String)
    fun onWarning(message: String)
    fun onError(message: String, ex: Exception)
}
