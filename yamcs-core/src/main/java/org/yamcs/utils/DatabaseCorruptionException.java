package org.yamcs.utils;

import org.yamcs.utils.DecodingException;

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

    public DatabaseCorruptionException(DecodingException e) {
        super(e);
    }
}
