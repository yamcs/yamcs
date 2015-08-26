package org.yamcs.alarms;

/**
 * Used by AlarmServer to indicate when the acknowledge on an alarm did not work.
 */
public class CouldNotAcknowledgeAlarmException extends Exception {
    
    private static final long serialVersionUID = 1L;

    public CouldNotAcknowledgeAlarmException(String message) {
        super(message);
    }
}
