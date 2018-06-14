package org.yamcs.xtceproc;

/**
 * Generic RuntimeException to be used when encountering an unexpected problem in the XTCE processing
 * @author nm
 *
 */
public class XtceProcException extends RuntimeException {
    public XtceProcException(String message) {
        super(message);
    }

    public XtceProcException(String message, Throwable cause) {
        super(message, cause);
    }
}
