package org.yamcs.mdb;

/**
 * Generic RuntimeException to be used when encountering an unexpected problem in the XTCE processing
 *
 */
public class XtceProcessingException extends RuntimeException {
    public XtceProcessingException(String message) {
        super(message);
    }

    public XtceProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
