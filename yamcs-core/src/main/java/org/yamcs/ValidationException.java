package org.yamcs;

import org.yamcs.YamcsException;

/**
 * This exception indicates that an error has occurred while performing a validate operation.
 */
@SuppressWarnings("serial")
public class ValidationException extends YamcsException {

    public ValidationException(String message) {
        super(message);
    }
}
