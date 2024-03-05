package org.yamcs.activities;

import java.time.Instant;
import java.util.concurrent.Callable;

import org.yamcs.logging.Log;

public abstract class ActivityExecution implements Callable<Void> {

    protected Log log;
    protected String yamcsInstance;
    protected ActivityService activityService;
    protected ActivityExecutor executor;
    protected Activity activity;

    private Instant start;
    private Instant stop;

    public ActivityExecution(ActivityService activityService, ActivityExecutor executor, Activity activity) {
        log = new Log(getClass(), activityService.getYamcsInstance());
        log.setContext(activity.getId().toString());

        this.yamcsInstance = activityService.getYamcsInstance();
        this.activityService = activityService;
        this.executor = executor;
        this.activity = activity;
    }

    @Override
    public Void call() throws Exception {
        start = Instant.now();
        try {
            return run();
        } finally {
            stop = Instant.now();
            stop();
        }
    }

    public abstract Void run() throws Exception;

    /**
     * Called when an activity stop is requested.
     * <p>
     * Implementations are expected to finish in a timely manner.
     */
    public abstract void stop() throws Exception;

    public Instant getStart() {
        return start;
    }

    public Instant getStop() {
        return stop;
    }

    public void logServiceInfo(String message) {
        log.info(message);
        activityService.logServiceInfo(activity, message);
    }

    public void logServiceWarning(String message) {
        log.warn(message);
        activityService.logServiceWarning(activity, message);
    }

    public void logServiceError(String message) {
        log.error(message);
        activityService.logServiceError(activity, message);
    }

    public void logActivityInfo(String message) {
        log.info(message);
        activityService.logActivityInfo(activity, message);
    }

    public void logActivityWarning(String message) {
        log.warn(message);
        activityService.logActivityWarning(activity, message);
    }

    public void logActivityError(String message) {
        log.error(message);
        activityService.logActivityError(activity, message);
    }
}
