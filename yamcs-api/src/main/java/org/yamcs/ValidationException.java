package org.yamcs;

/**
 * This exception indicates that an error has occurred while performing a validate operation.
 */
@SuppressWarnings("serial")
public class ValidationException extends YamcsException {

    public ValidationException(String message) {
        super(message);
    }
}
