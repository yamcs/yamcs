package org.yamcs.tctm;

/**
 * Generic exception to throw for problems encountered during TC or TM processing
 * 
 * @author nm
 *
 */
public class TcTmException extends Exception {
    public TcTmException() {
        super();
    }

    public TcTmException(String message) {
        super(message);
    }

    public TcTmException(String message, Throwable cause) {
        super(message, cause);
    }
}
