package org.yamcs.activities;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.logging.Log;
import org.yamcs.security.User;
import org.yamcs.utils.ExceptionUtil;
import org.yamcs.utils.TimeEncoding;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Yamcs service for executing activities.
 */
public class ActivityService extends AbstractService {

    public static final String ACTIVITY_TYPE_MANUAL = "MANUAL";

    private String yamcsInstance;
    private Log log;

    private Map<String, ActivityExecutor> executors = new HashMap<>();
    private ConcurrentMap<UUID, OngoingActivity> ongoingActivities = new ConcurrentHashMap<>();
    private Set<ActivityListener> listeners = new CopyOnWriteArraySet<>();
    private Set<ActivityLogListener> logListeners = new CopyOnWriteArraySet<>();

    private ActivityDb activityDb;
    private ActivityLogDb activityLogDb;

    // Distinguish records with the same timestamp
    private AtomicInteger activitySeqSequence = new AtomicInteger();

    private ListeningExecutorService exec = listeningDecorator(Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("YamcsActivityService-worker").build()));

    public Spec getSpec() {
        var spec = new Spec();
        for (var executor : ServiceLoader.load(ActivityExecutor.class)) {
            var executorSpec = executor.getSpec();
            if (executorSpec != null) {
                spec.addOption(executorSpec.getName(), OptionType.MAP)
                        .withSpec(executorSpec)
                        .withApplySpecDefaults(true);
            }
        }

        return spec;
    }

    public void init(String yamcsInstance, YConfiguration config) throws InitException {
        this.yamcsInstance = yamcsInstance;
        log = new Log(getClass(), yamcsInstance);
        activityDb = new ActivityDb(yamcsInstance);
        activityLogDb = new ActivityLogDb(yamcsInstance);
        for (var executor : ServiceLoader.load(ActivityExecutor.class)) {
            var executorConfig = YConfiguration.emptyConfig();
            if (executor.getSpec() != null) {
                executorConfig = config.getConfig(executor.getSpec().getName());
            }

            executor.init(this, executorConfig);
            executors.put(executor.getActivityType(), executor);
        }
    }

    @Override
    protected void doStart() {
        // In case of an unclean shutdown, clean-up old activities without stop
        var unfinishedActivities = activityDb.getUnfinishedActivities();
        if (!unfinishedActivities.isEmpty()) {
            var systemUser = YamcsServer.getServer().getSecurityStore().getSystemUser();
            for (var activity : unfinishedActivities) {
                log.info("Force-cancel activity {}", activity.getId());
                activity.cancel(systemUser);
            }
            activityDb.updateAll(unfinishedActivities);
        }

        notifyStarted();
    }

    public String getYamcsInstance() {
        return yamcsInstance;
    }

    public Collection<ActivityExecutor> getExecutors() {
        return executors.values();
    }

    public ActivityExecutor getExecutor(String activity) {
        return executors.get(activity);
    }

    public void addActivityListener(ActivityListener listener) {
        listeners.add(listener);
    }

    public void removeActivityListener(ActivityListener listener) {
        listeners.remove(listener);
    }

    public void addActivityLogListener(ActivityLogListener listener) {
        logListeners.add(listener);
    }

    public void removeActivityLogListener(ActivityLogListener listener) {
        logListeners.remove(listener);
    }

    public Activity prepareActivity(String type, Map<String, Object> args, User user, String comment) {
        var executor = findExecutor(type);

        var activity = new Activity(
                UUID.randomUUID(),
                TimeEncoding.getWallclockTime(),
                activitySeqSequence.getAndIncrement(),
                type,
                args,
                user);
        activity.setComment(comment);

        if (executor == null) { // Manual activity
            activity.setDetail(YConfiguration.getString(args, "name"));
        } else {
            activity.setDetail(executor.describeActivity(args));
        }

        activityDb.insert(activity);
        return activity;
    }

    public void startActivity(Activity activity, User user) {
        log.info("Starting activity " + activity.getId() + " (" + activity.getType() + ")");
        var executor = findExecutor(activity.getType());

        var ongoingActivity = new OngoingActivity(activity);
        logServiceInfo(activity, "Starting activity");

        ActivityExecution execution = null;
        if (executor == null) {
            execution = null;
            ongoingActivity.workFuture = new CompletableFuture<>();
        } else {
            try {
                execution = executor.createExecution(activity, user);
                var fExecution = execution;
                ongoingActivity.workFuture = new FutureTask<>(() -> {
                    fExecution.call();
                    return null;
                });
                ongoingActivity.workFuture = exec.submit(fExecution);
            } catch (Throwable t) {
                execution = null;
                ongoingActivity.workFuture = CompletableFuture.failedFuture(t);
            }
        }

        var fExecution = execution;
        ongoingActivity.resultFuture = new CompletableFuture<>();

        if (ongoingActivity.workFuture instanceof ListenableFuture) {
            ((ListenableFuture<Void>) ongoingActivity.workFuture).addListener(() -> {
                onActivityFinished(ongoingActivity, fExecution);
            }, exec);
        } else {
            ((CompletableFuture<Void>) ongoingActivity.workFuture).whenCompleteAsync((res, err) -> {
                onActivityFinished(ongoingActivity, null);
            }, exec);
        }

        ongoingActivities.put(activity.getId(), ongoingActivity);
        listeners.forEach(l -> l.onActivityUpdated(activity));

    }

    private void onActivityFinished(OngoingActivity ongoingActivity, ActivityExecution execution) {
        var activity = ongoingActivity.getActivity();
        var loggedName = "Activity (" + activity.getId() + ")";

        // Set if there was a cancellation. Or in the case of a manual activity,
        // it is always set.
        var stopRequester = ongoingActivity.getStopRequester();

        try {
            ongoingActivity.workFuture.get();

            log.info("{} successful", loggedName);
            logServiceInfo(activity, "Activity successful");
            activity.complete(ongoingActivity.getStopRequester());
        } catch (CancellationException e) {
            log.info("{} cancel requested by {}", loggedName, stopRequester.getName());
            logServiceInfo(activity, "Cancel requested by " + stopRequester.getName());
            if (execution != null) {
                try {
                    execution.stop();
                } catch (Throwable t) {
                    log.error("Failed to stop activity execution", t);
                }
            }
            log.info("{} was cancelled by {}", loggedName, stopRequester.getName());
            logServiceInfo(activity, "Activity cancelled");
            activity.cancel(stopRequester);
        } catch (Exception e) {
            var cause = ExceptionUtil.unwind(e);
            if (cause instanceof ManualFailureException) {
                log.error("{} failed: {}", loggedName, cause.getMessage());
            } else {
                log.error("{} failed", loggedName, cause);
            }
            var failureReason = cause.getMessage();
            if (failureReason == null) {
                failureReason = cause.getClass().getSimpleName();
            }
            logServiceError(activity, "Activity failed: " + failureReason);
            activity.completeExceptionally(failureReason, ongoingActivity.getStopRequester());
        } finally {
            ongoingActivities.remove(activity.getId());
            activityDb.update(activity);
            listeners.forEach(l -> l.onActivityUpdated(activity));
        }
    }

    public Activity cancelActivity(UUID id, User user) {
        var ongoingActivity = ongoingActivities.get(id);
        if (ongoingActivity != null) {
            ongoingActivity.cancel(user);
            activityDb.update(ongoingActivity.getActivity()); // Persist CANCELLED status
            listeners.forEach(l -> l.onActivityUpdated(ongoingActivity.getActivity()));
        }
        return activityDb.getById(id);
    }

    public Activity completeManualActivity(UUID id, String failureReason, User user) {
        var ongoingActivity = ongoingActivities.get(id);
        if (ongoingActivity != null) {
            var activity = ongoingActivity.getActivity();
            if (!activity.getType().equals(ACTIVITY_TYPE_MANUAL)) {
                throw new IllegalArgumentException(
                        "Only manual activities can be completed. Did you mean to cancel?");
            }
            if (failureReason == null) {
                ongoingActivity.complete(user);
            } else {
                ongoingActivity.completeExceptionally(failureReason, user);
            }

            activityDb.update(ongoingActivity.getActivity());
            listeners.forEach(l -> l.onActivityUpdated(ongoingActivity.getActivity()));
            return ongoingActivity.getActivity();
        }
        return activityDb.getById(id);
    }

    public Activity getActivity(UUID id) {
        return activityDb.getById(id);
    }

    public boolean isStopRequested(Activity activity) {
        var ongoingActivity = ongoingActivities.get(activity.getId());
        if (ongoingActivity != null) {
            return ongoingActivity.getStopRequester() != null;
        }
        return false;
    }

    public List<Activity> getOngoingActivities() {
        return ongoingActivities.values().stream()
                .map(OngoingActivity::getActivity)
                .sorted()
                .collect(Collectors.toList());
    }

    private ActivityExecutor findExecutor(String activityType) {
        ActivityExecutor executor = null;
        if (!ACTIVITY_TYPE_MANUAL.equals(activityType)) {
            executor = executors.get(activityType);
            if (executor == null) {
                throw new IllegalArgumentException("Unexpected activity type '" + activityType + "'");
            }
        }
        return executor;
    }

    public void logServiceInfo(Activity activity, String message) {
        logMessage(activity, ActivityLog.SOURCE_SERVICE, ActivityLogLevel.INFO, message);
    }

    public void logServiceWarning(Activity activity, String message) {
        logMessage(activity, ActivityLog.SOURCE_SERVICE, ActivityLogLevel.WARNING, message);
    }

    public void logServiceError(Activity activity, String message) {
        logMessage(activity, ActivityLog.SOURCE_SERVICE, ActivityLogLevel.ERROR, message);
    }

    public void logActivityInfo(Activity activity, String message) {
        logMessage(activity, ActivityLog.SOURCE_ACTIVITY, ActivityLogLevel.INFO, message);
    }

    public void logActivityWarning(Activity activity, String message) {
        logMessage(activity, ActivityLog.SOURCE_ACTIVITY, ActivityLogLevel.WARNING, message);
    }

    public void logActivityError(Activity activity, String message) {
        logMessage(activity, ActivityLog.SOURCE_ACTIVITY, ActivityLogLevel.ERROR, message);
    }

    private void logMessage(Activity activity, String source, ActivityLogLevel level, String message) {
        var entry = new ActivityLog(
                TimeEncoding.getWallclockTime(),
                activity.getId(),
                source,
                level,
                message);
        activityLogDb.addLogEntry(entry);
        logListeners.forEach(l -> l.onLogRecord(activity, entry));
    }

    public ActivityDb getActivityDb() {
        return activityDb;
    }

    public ActivityLogDb getActivityLogDb() {
        return activityLogDb;
    }

    @Override
    protected void doStop() {
        try {
            exec.shutdownNow();
            exec.awaitTermination(10, TimeUnit.SECONDS);
            notifyStopped();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            notifyFailed(e);
        }
    }
}
