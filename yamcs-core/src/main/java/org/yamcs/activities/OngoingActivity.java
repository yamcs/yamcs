package org.yamcs.activities;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.yamcs.security.User;

public class OngoingActivity {

    private final Activity activity;

    // Underlying work. This can be cancelled, after which the resultFuture triggers
    Future<Void> workFuture;

    // Future that updates state following completion of the work
    CompletableFuture<Void> resultFuture;

    private User stopRequester;

    public OngoingActivity(Activity activity) {
        this.activity = activity;
    }

    public Activity getActivity() {
        return activity;
    }

    public CompletableFuture<Void> onResult() {
        return resultFuture;
    }

    /**
     * If a stop has been requested, this returns the first user that asked to cancel.
     */
    public User getStopRequester() {
        return stopRequester;
    }

    /**
     * Request a stop of this activity. This method does not block.
     */
    public void cancel(User user) {
        activity.setStatus(ActivityStatus.CANCELLED);
        if (stopRequester == null) {
            stopRequester = user;
        }
        workFuture.cancel(true);
    }

    public void complete(User user) {
        verifyManualActivity();
        stopRequester = user;
        ((CompletableFuture<Void>) workFuture).complete(null);
    }

    public void completeExceptionally(String failureReason, User user) {
        verifyManualActivity();
        stopRequester = user;
        ((CompletableFuture<Void>) workFuture).completeExceptionally(new ManualFailureException(failureReason));
    }

    private void verifyManualActivity() {
        if (!ActivityService.ACTIVITY_TYPE_MANUAL.equals(activity.getType())) {
            throw new UnsupportedOperationException("Cannot complete a non-manual activity");
        }
    }
}
