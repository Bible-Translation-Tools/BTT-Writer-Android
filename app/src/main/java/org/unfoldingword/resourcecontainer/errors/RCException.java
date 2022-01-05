package org.unfoldingword.resourcecontainer.errors;

public abstract class RCException extends Exception {
    public RCException(String message) {
        super(message);
    }
}
