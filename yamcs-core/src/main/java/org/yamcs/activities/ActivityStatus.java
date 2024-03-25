package org.yamcs.activities;

public enum ActivityStatus {

    /**
     * An activity is running
     */
    RUNNING,

    /**
     * The activity was cancelled. It may or may not still be running (verify stop time).
     */
    CANCELLED,

    /**
     * The activity completed successfully
     */
    SUCCESSFUL,

    /**
     * An error occurred while running this activity
     */
    FAILED,
}
