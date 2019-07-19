package org.yamcs;

/**
 * Thrown by a method that causes a user/system specified limit to be exceeded.
 * 
 * @author nm
 *
 */
@SuppressWarnings("serial")
public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String message) {
        super(message);
    }
}
