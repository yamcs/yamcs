package org.yamcs.xtce;

import java.io.Serializable;

/**
 * From XTCE: Holds a time to stop checking and optional time to start checking and whether window is relative to
 * command release or last verifier.
 * 
 */
public class CheckWindow implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum TimeWindowIsRelativeToType {
        COMMAND_RELEASE, LAST_VERIFIER;

        static public TimeWindowIsRelativeToType fromXtce(String xtceAttr) {
            if ("timeLastVerifierPassed".equals(xtceAttr)) {
                return TimeWindowIsRelativeToType.LAST_VERIFIER;
            } else if ("commandRelease".equals(xtceAttr)) {
                return TimeWindowIsRelativeToType.COMMAND_RELEASE;
            } else {
                throw new IllegalArgumentException("Invalid value '" + xtceAttr + "' for timeWindowIsRelativeTo");
            }
        }

        static public TimeWindowIsRelativeToType fromXls(String xlsStr) {
            if ("LastVerifier".equals(xlsStr)) {
                return TimeWindowIsRelativeToType.LAST_VERIFIER;
            } else if ("CommandRelease".equals(xlsStr)) {
                return TimeWindowIsRelativeToType.COMMAND_RELEASE;
            } else {
                throw new IllegalArgumentException("Invalid value '" + xlsStr + "' for timeWindowIsRelativeTo");
            }
        }

        public String toXtce() {
            if (this == COMMAND_RELEASE) {
                return "commandRelease";
            } else {
                return "timeLastVerifierPassed";
            }
        }
    };

    final private long timeToStartChecking; // time to start checking in milliseconds (if -1 - it means not defined)
    final private long timeToStopChecking; // time to stop checking in milliseconds

    final private TimeWindowIsRelativeToType timeWindowIsRelativeTo;

    public CheckWindow(long timeToStartChecking, long timeToStopChecking,
            TimeWindowIsRelativeToType timeWindowIsRelativeTo) {
        if (timeToStopChecking < timeToStartChecking) {
            throw new IllegalArgumentException(
                    "timeToStopChecking has to be greater or equal than timeToStartChecking");
        }

        if (timeToStopChecking <= 0) {
            throw new IllegalArgumentException(
                    "timeToStopChecking has to be strictly greater than 0");
        }

        this.timeToStartChecking = timeToStartChecking;
        this.timeToStopChecking = timeToStopChecking;
        this.timeWindowIsRelativeTo = timeWindowIsRelativeTo;
    }

    public long getTimeToStartChecking() {
        return timeToStartChecking;
    }

    public long getTimeToStopChecking() {
        return timeToStopChecking;
    }

    public TimeWindowIsRelativeToType getTimeWindowIsRelativeTo() {
        return timeWindowIsRelativeTo;
    }

    public boolean hasStart() {
        return timeToStartChecking != -1;
    }

    public String toString() {
        return timeWindowIsRelativeTo + "[" + timeToStartChecking + "," + timeToStopChecking + "]";
    }
}
