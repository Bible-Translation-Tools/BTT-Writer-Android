package org.unfoldingword.door43client;

/**
 * A utility to get progress updates during long operations
 */
public interface OnProgressListener {
    /**
     *
     * @param tag used to identify what progress event is occurring
     * @param max the total number of items being processed
     * @param complete the number of items that have been successfully processed
     * @return cancels the operation if false is returned
     */
    boolean onProgress(String tag, long max, long complete);
}
