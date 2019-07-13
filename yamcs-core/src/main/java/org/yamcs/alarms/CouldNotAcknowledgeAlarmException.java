package org.yamcs.alarms;

import org.yamcs.YamcsException;

/**
 * Used by AlarmServer to indicate when the acknowledge on an alarm did not work.
 */
public class CouldNotAcknowledgeAlarmException extends YamcsException {

    private static final long serialVersionUID = 1L;

    public CouldNotAcknowledgeAlarmException(String message) {
        super(message);
    }

    public CouldNotAcknowledgeAlarmException(Throwable t) {
        super(t);
    }
}
