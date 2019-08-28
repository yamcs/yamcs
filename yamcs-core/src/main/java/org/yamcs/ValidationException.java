package org.yamcs;

import org.yamcs.Spec.ValidationContext;

/**
 * This exception indicates that an error has occurred while performing a validate operation.
 */
@SuppressWarnings("serial")
public class ValidationException extends YamcsException {

    private ValidationContext ctx;

    public ValidationException(ValidationContext ctx, String message) {
        super(message);
        this.ctx = ctx;
    }

    public ValidationContext getContext() {
        return ctx;
    }
}
