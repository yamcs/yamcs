package org.yamcs;

/**
 * Allows wrapping any checked exception without overloading signatures.
 */
@SuppressWarnings("serial")
public class UncheckedYamcsException extends RuntimeException {

    public UncheckedYamcsException(Throwable t) {
        super(t);
    }
}
