package org.yamcs.alarms;

import org.yamcs.YamcsException;

/**
 * Used by AlarmServer to indicate that a specified alarm instance does not have the expected sequence number.
 */
@SuppressWarnings("serial")
public class AlarmSequenceException extends YamcsException {

    public AlarmSequenceException(int expectedId, int actualId) {
        super(String.format("Alarm sequence number does not match active alarm."
                + " Was: %s, expected %s", actualId, expectedId));
    }
}
