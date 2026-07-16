package org.yamcs.activities;

import java.util.Objects;

import org.yamcs.security.User;

/**
 * Exception that {@link ActivityExecution#run()} may throw to indicate that the activity ended because the underlying
 * work was cancelled or aborted, rather than because it failed.
 * <p>
 * The activity is marked {@link ActivityStatus#CANCELLED} instead of {@link ActivityStatus#FAILED}.
 */
@SuppressWarnings("serial")
public class ActivityCancelledException extends Exception {

    private final User cancelledBy;

    public ActivityCancelledException(String message, User cancelledBy) {
        super(message);
        this.cancelledBy = Objects.requireNonNull(cancelledBy);
    }

    public User getCancelledBy() {
        return cancelledBy;
    }
}
