package org.unfoldingword.door43client;

/**
 * A utility to receive log events from the module
 */
public interface OnLogListener {
    void onInfo(String message);
    void onWarning(String message);
    void onError(String message, Exception ex);
}
