package org.yamcs.activities;

@SuppressWarnings("serial")
public class StackParseException extends RuntimeException {

    public StackParseException(String message) {
        super(message);
    }

    public StackParseException(Throwable t) {
        super(t);
    }

    public StackParseException(String message, Throwable t) {
        super(message, t);
    }
}
