package org.yamcs.activities;

@SuppressWarnings("serial")
public class CommandStackParseException extends RuntimeException {

    public CommandStackParseException(String message) {
        super(message);
    }

    public CommandStackParseException(Throwable t) {
        super(t);
    }

    public CommandStackParseException(String message, Throwable t) {
        super(message, t);
    }
}
