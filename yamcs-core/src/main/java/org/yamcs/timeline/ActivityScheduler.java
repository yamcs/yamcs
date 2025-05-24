package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_START;
import static org.yamcs.timeline.TimelineItemDb.CNAME_STATUS;
import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;
import static org.yamcs.timeline.TimelineItemDb.TABLE_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.AbstractYamcsService;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.activities.Activity;
import org.yamcs.activities.ActivityListener;
import org.yamcs.activities.ActivityService;
import org.yamcs.http.api.GpbWellKnownHelper;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.StartCondition;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.time.TimeService;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.YarchDatabase;

/**
 * Schedules activities in the timeline
 */
public class ActivityScheduler extends AbstractYamcsService implements ItemListener, ActivityListener {
    private Log log;

    private String yamcsInstance;
    private ActivityService activityService;
    private TimelineItemDb timelineItemDb;
    private TimeService timeService;

    // Contains all planned activities that have a calculated execution date
    private PriorityQueue<TimelineActivity> planned = new PriorityQueue<>();

    // execute all the logic on this thread
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);

    // Keep track of ongoing timeline activities, for the purpose of copying the activity result
    private ConcurrentMap<UUID, TimelineActivity> ongoingActivitiesByRunId = new ConcurrentHashMap<>();

    // list of activities that depend on others to be able to start
    List<TimelineActivity> dependentActivities = new ArrayList<>();

    // list of activities that could be started but have the autoStart = false so they wait for manual start
    Map<String, TimelineActivity> readyToStart = new HashMap<>();

    // when retrieving activities to run, load the ones that are missionTime-schedulingMargin
    private long schedulingMargin = 10 * 1000;

    // Lock to handle debounce of many replan requests
    private final Object replanLock = new Object();
    private ScheduledFuture<?> pendingReplan;

    @Override
    public Spec getSpec() {
        return new Spec();
    }

    public void init(TimelineService timelineService, YConfiguration config) {
        this.yamcsInstance = timelineService.getYamcsInstance();
        this.activityService = YamcsServer.getServer().getInstance(yamcsInstance).getActivityService();
        this.timelineItemDb = timelineService.getTimelineItemDb();
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        log = new Log(getClass(), yamcsInstance);
    }

    @Override
    protected void doStart() {
        cleanupInitialState();
        timelineItemDb.addItemListener(this);
        activityService.addActivityListener(this);
        executor.submit(() -> {
            try {
                replan();
            } catch (Throwable t) {
                log.error("Uncaught exception", t);
            }
        });
        notifyStarted();
    }

    /**
     * Finds and aborts "ongoing" activities. These were started in a previous server run, and so we don't know what to
     * do with them, other than aborting.
     * <p>
     * Note: we leave READY, PLANNED and WAITING_ON_DEPENDENCY as-is. Some of these (depending on the scheduling margin)
     * may be picked up again by the replan method.
     */
    private void cleanupInitialState() {
        var toBeAborted = timelineItemDb.getOngoingItems();
        if (!toBeAborted.isEmpty()) {
            for (var item : toBeAborted) {
                item.setStatus(ExecutionStatus.ABORTED);
            }
            timelineItemDb.updateAll(toBeAborted);
        }
    }

    @Override
    protected void doStop() {
        timelineItemDb.removeItemListener(this);
        activityService.removeActivityListener(this);
        executor.shutdown();
        notifyStopped();
    }

    public CompletableFuture<Activity> startActivity(String id) {
        return CompletableFuture.supplyAsync(() -> {
            TimelineActivity activity = readyToStart.remove(id);

            // If it's not READY, it may be PLANNED
            if (activity == null) {
                var item = timelineItemDb.getItem(id);
                if (item instanceof TimelineActivity ta) {
                    // It may be PLANNED in the future, so remove to be sure
                    planned.remove(ta);

                    activity = ta;
                }
            }

            if (activity == null) {
                throw new IllegalArgumentException("No activity found with ID " + id);
            }

            return doStartActivity(activity);

        }, executor);
    }

    @Override
    public void onItemCreated(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            debounceReplan();
        }
    }

    @Override
    public void onItemUpdated(TimelineItem item) {
        if (item instanceof TimelineActivity activity) {
            // Remove from READY list if the activity was canceled
            // prior to execution
            if (activity.status == ExecutionStatus.CANCELED) {
                readyToStart.remove(activity.id);
            }
            debounceReplan();
        }
    }

    @Override
    public void onItemDeleted(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            debounceReplan();
        }
    }

    private void debounceReplan() {
        synchronized (replanLock) {
            if (pendingReplan != null && !pendingReplan.isDone()) {
                pendingReplan.cancel(false);
            }
            // Wait 100ms before running to let batch operations finish
            pendingReplan = executor.schedule(() -> {
                try {
                    replan();
                } catch (Throwable t) {
                    log.error("Uncaught exception", t);
                }
            }, 100, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onActivityUpdated(Activity activity) {
        var item = ongoingActivitiesByRunId.get(activity.getId());
        if (item != null && activity.isStopped()) {
            ongoingActivitiesByRunId.remove(activity.getId());

            switch (activity.getStatus()) {
            case SUCCESSFUL:
                item.setStatus(ExecutionStatus.SUCCEEDED);
                break;
            case CANCELLED:
                item.setStatus(ExecutionStatus.ABORTED);
                item.setFailureReason(activity.getFailureReason());
                break;
            case FAILED:
                item.setStatus(ExecutionStatus.FAILED);
                item.setFailureReason(activity.getFailureReason());
                break;
            default:
                throw new IllegalStateException("Unexpected terminal state " + activity.getStatus());
            }
            timelineItemDb.updateItem(item);
        }
    }

    private void replan() {
        try {
            planned.clear();
            dependentActivities.clear();
            var ydb = YarchDatabase.getInstance(yamcsInstance);
            var tblDef = ydb.getTable(TABLE_NAME);
            if (tblDef == null || tblDef.getColumnDefinition(CNAME_STATUS) == null) {
                return;
            }

            var sqlb = new SqlBuilder(TABLE_NAME);
            sqlb.whereColAfterOrEqual(CNAME_START, timeService.getMissionTime() - schedulingMargin);
            sqlb.where(CNAME_TYPE + " = '" + TimelineItemType.ACTIVITY.name() + "'");
            sqlb.whereColIn(CNAME_STATUS, TimelineActivity.FUTURE_STATES.stream()
                    .map(ExecutionStatus::name)
                    .toList());
            var result = ydb.execute(sqlb.toString(), sqlb.getQueryArguments().toArray());
            while (result.hasNext()) {
                var tuple = result.next();

                try {
                    var activity = new TimelineActivity(TimelineItemType.ACTIVITY, tuple);
                    if (activity.status == ExecutionStatus.READY) {
                        readyToStart.put(activity.getId(), activity);
                    } else if (activity.predecessors != null && !activity.predecessors.isEmpty()) {
                        dependentActivities.add(activity);
                    } else {
                        planned.add(activity);
                    }
                } catch (Exception e) {
                    log.warn("Unable to load activity from tuple {}: {}", tuple, e);
                }
            }
            if (!planned.isEmpty()) {
                log.info("Upcoming:");
                for (var activity : planned) {
                    log.info("- " + activity);
                }
                startActivities();
            }

        } catch (Exception e) {
            log.error("Error while planning: ", e);
        }
        startActivitiesDependentOnOthers();
    }

    private void startActivities() {
        TimelineActivity nextActivity;
        var now = timeService.getMissionTime();
        while ((nextActivity = planned.poll()) != null) {
            if (now < nextActivity.start) {
                planned.offer(nextActivity);
                executor.schedule(this::startActivities, nextActivity.start - now, TimeUnit.MILLISECONDS);
                break;
            } else {
                if (nextActivity.autoStart) {
                    doStartActivity(nextActivity);
                } else {
                    log.debug("Marking activity {} as ready", nextActivity.displayName());
                    nextActivity.setStatus(ExecutionStatus.READY);
                    timelineItemDb.updateItem(nextActivity);
                    readyToStart.put(nextActivity.getId(), nextActivity);
                }
            }
        }
    }

    private void startActivitiesDependentOnOthers() {
        var now = timeService.getMissionTime();
        long nextSchedule = -1;

        for (var activity : dependentActivities) {
            try {
                startActivityDependentOnOthers(now, activity);
            } catch (Exception e) {
                log.warn("Cannot start activity {}", e, e);
                continue;
            }
            if (activity.getStatus() == ExecutionStatus.PLANNED) {
                // we need to schedule another check at least at the start time to mark that it is waiting for a
                // dependency
                if (nextSchedule < 0 || nextSchedule > activity.getStart() - now) {
                    nextSchedule = activity.getStart() - now;
                }
            }
        }

        if (nextSchedule > 0) {
            log.debug("Scheduling startActivitiesDependentOnOthers in {} millis", nextSchedule);
            executor.schedule(this::startActivitiesDependentOnOthers, nextSchedule, TimeUnit.MILLISECONDS);
        }
    }

    private void startActivityDependentOnOthers(long now, TimelineActivity activity) {
        boolean start = true;
        boolean skip = false;
        String skipReason = null;

        for (var predecessor : activity.predecessors) {
            var predecessorItem = timelineItemDb.getItem(predecessor.itemId());
            if (predecessorItem == null) {
                log.warn("Item {} on which {} depends, not found", predecessor.itemId(), activity.displayName());
                continue;
            }
            if (predecessorItem instanceof TimelineActivity ta) {
                var predecessorStatus = ta.getStatus();
                switch (predecessorStatus) {
                case PLANNED:
                case READY:
                case WAITING_ON_DEPENDENCY:
                case WAITING_FOR_INPUT:
                case PAUSED:// intentional fall through
                    start = false;
                    skip = false;
                    break;
                case IN_PROGRESS:
                    start = (predecessor.startCondition() == StartCondition.ON_START);
                    skip = false;
                    break;
                case ABORTED:
                case FAILED:
                case CANCELED:
                case SKIPPED:// intentional fall through
                    if (predecessor.startCondition() == StartCondition.ON_SUCCESS) {
                        start = false;
                        skip = true;
                        skipReason = "Predecessor '" + ta.displayName() + "' is " + predecessorStatus
                                + " (while the start condition is ON_SUCCESS)";
                        break;
                    }
                    break;
                case SUCCEEDED:
                    if (predecessor.startCondition() == StartCondition.ON_FAILURE) {
                        start = false;
                        skip = true;
                        skipReason = "Predecessor " + ta.displayName() + " is " + predecessorStatus
                                + " (while the start condition is ON_FAILURE)";
                        break;
                    }
                    break;
                }
            } else { // Predecessor is an event
                if (predecessor.startCondition() == StartCondition.ON_START) {
                    // Check if started
                    if (predecessorItem.getStart() > now) {
                        start = false;
                        skip = false;
                        break;
                    }
                } else {
                    // Check if finished
                    if (predecessorItem.getStart() + predecessorItem.getDuration() > now) {
                        start = false;
                        skip = false;
                        break;
                    }
                }
            }
        }

        if (start) {
            if (activity.autoStart) {
                log.debug("Starting activity {}", activity.displayName());
                doStartActivity(activity);
            } else {
                if (activity.getStatus() != ExecutionStatus.READY) {
                    log.debug("Marking activity {} as ready", activity.displayName());
                    activity.setStatus(ExecutionStatus.READY);
                    timelineItemDb.updateItem(activity);
                }
            }
        } else if (skip) {
            log.debug("Marking activity {} as skipped", activity.displayName());
            activity.setStatus(ExecutionStatus.SKIPPED);
            activity.setFailureReason(skipReason);
            timelineItemDb.updateItem(activity);
        } else if (activity.getStart() <= now) {
            if (activity.getStatus() != ExecutionStatus.WAITING_ON_DEPENDENCY) {
                log.debug("Marking activity {} as waiting on dependency", activity.displayName());
                activity.setStatus(ExecutionStatus.WAITING_ON_DEPENDENCY);
                timelineItemDb.updateItem(activity);
            }
        }
    }

    private Activity doStartActivity(TimelineActivity item) {
        var systemUser = YamcsServer.getServer().getSecurityStore().getSystemUser();
        log.debug("Starting activity {}", item.displayName());
        Activity activity;
        var label = item.getName();
        var def = item.getActivityDefinition();
        if (def == null) {
            activity = activityService.prepareActivity(
                    ActivityService.ACTIVITY_TYPE_MANUAL,
                    Map.of("name", item.getName()),
                    systemUser, label);
        } else {
            var executorType = def.getType();
            var executorArgs = GpbWellKnownHelper.toJava(def.getArgs());
            activity = activityService.prepareActivity(executorType, executorArgs, systemUser, label);
        }
        ongoingActivitiesByRunId.put(activity.getId(), item);

        item.setStatus(ExecutionStatus.IN_PROGRESS);
        item.addRun(activity.getId());
        timelineItemDb.updateItem(item);

        activityService.startActivity(activity, systemUser);

        return activity;
    }
}
