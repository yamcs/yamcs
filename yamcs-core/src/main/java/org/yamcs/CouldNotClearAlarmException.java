package org.yamcs;

/**
 * Used by AlarmServer to indicate when the clear on an alarm did not work.
 */
public class CouldNotClearAlarmException extends Exception {
    
    private static final long serialVersionUID = 1L;

    public CouldNotClearAlarmException(String message) {
        super(message);
    }
}
