package com.door43

interface OnProgressListener {
    /**
     * Publish the progress on an operation between 0 and max
     * @param progress
     * @return
     */
    fun onProgress(progress: Float, message: String?)

    /**
     * Identifies the current task as not quantifiable
     * @return
     */
    fun onIndeterminate()
}