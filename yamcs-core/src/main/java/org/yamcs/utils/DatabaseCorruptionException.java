package org.yamcs.utils;

/**
 * used to signal inconsistencies found in the database
 * @author nm
 *
 */
public class DatabaseCorruptionException extends RuntimeException {
    public DatabaseCorruptionException(String message) {
        super(message);
    }
    
    public DatabaseCorruptionException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseCorruptionException(Throwable cause) {
        super(cause);
    }
}
