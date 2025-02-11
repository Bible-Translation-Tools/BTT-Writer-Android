package com.door43

fun interface OnProgressListener {
    /**
     * Publish the progress
     * @param progress Current value. When progress set to less than 0, it will be indeterminate
     * @param max Max value
     * @param message Message to display
     * @return
     */
    fun onProgress(progress: Int, max: Int, message: String?)
}