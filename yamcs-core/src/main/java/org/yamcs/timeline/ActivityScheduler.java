package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_START;
import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;
import static org.yamcs.timeline.TimelineItemDb.CNAME_STATUS;
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
import org.yamcs.protobuf.ActivityDependencyCondition;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.time.TimeService;
import org.yamcs.utils.parser.ParseException;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.StreamSqlException;

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
    long schedulingMargin = 10 * 1000;

    public Spec getSpec() {
        return new Spec();
    }

    public void init(TimelineService timelineService, YConfiguration config) {
        this.yamcsInstance = timelineService.getYamcsInstance();
        this.activityService = timelineService.getActivityService();
        this.timelineItemDb = timelineService.getTimelineItemDb();
        this.timeService = YamcsServer.getTimeService(yamcsInstance);
        log = new Log(getClass(), yamcsInstance);
    }

    @Override
    protected void doStart() {
        timelineItemDb.addItemListener(this);
        activityService.addActivityListener(this);
        executor.submit(this::replan);
        notifyStarted();
    }

    @Override
    protected void doStop() {
        timelineItemDb.removeItemListener(this);
        activityService.removeActivityListener(this);
        executor.shutdown();
        notifyStopped();
    }

    /**
     * starts an activity that is in the READY state
     * 
     * @param id
     * @return
     */
    public CompletableFuture<Activity> startActivity(String id) {
        return CompletableFuture.supplyAsync(() -> {
            TimelineActivity activity = readyToStart.remove(id);

            if (activity == null) {
                throw new IllegalArgumentException("No READY activity found with ID " + id);
            }

            return doStartActivity(activity);

        }, executor);
    }

    @Override
    public void onItemCreated(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            executor.submit(this::replan);
        }
    }

    @Override
    public void onItemUpdated(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            executor.submit(this::replan);
        }
    }

    @Override
    public void onItemDeleted(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            executor.submit(this::replan);
        }
    }

    @Override
    public void onActivityUpdated(Activity activity) {

        var item = ongoingActivitiesByRunId.get(activity.getId());
        if (item != null && activity.isStopped()) {
            ongoingActivitiesByRunId.remove(activity.getId());

            switch (activity.getStatus()) {
            case SUCCESSFUL:
                item.setStatus(ExecutionStatus.COMPLETED);
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
            
            sqlb.where(String.format("%s IN ('%s', '%s', '%s')", CNAME_STATUS, ExecutionStatus.PLANNED.name(),
                    ExecutionStatus.READY.name(), ExecutionStatus.WAITING_ON_DEPENDENCY.name()));



            var result = ydb.execute(sqlb.toString(), sqlb.getQueryArguments());
            while (result.hasNext()) {
                var tuple = result.next();

                try {
                    var activity = new TimelineActivity(TimelineItemType.ACTIVITY, tuple);
                    if (activity.status == ExecutionStatus.READY) {
                        readyToStart.put(activity.getId(), activity);
                    } else if (activity.dependsOn != null && !activity.dependsOn.isEmpty()) {
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
            e.printStackTrace();
            log.warn("Error retrieving activities: ", e);
        }
        startActivitiesDependentOnOthers();
    }

    private void startActivities() {
        TimelineActivity nextActivity;
        var now = timeService.getMissionTime();
        while ((nextActivity = planned.poll()) != null) {
            if (now < nextActivity.start) {
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
            boolean start = true;
            boolean skip = false;
            String skipReason = null;

            outerloop: for (var dependency : activity.dependsOn) {
                var item = timelineItemDb.getItem(dependency.id());
                if (item == null) {
                    log.warn("Activity {} on which {} depends, not found", dependency.id(), activity.displayName());
                    continue;
                }
                if (item instanceof TimelineActivity ta) {
                    ExecutionStatus status = ta.getStatus();
                    switch (status) {
                    case PLANNED:
                    case READY:
                    case WAITING_ON_DEPENDENCY:
                    case WAITING_FOR_INPUT:
                    case PAUSED:
                    case IN_PROGRESS: // intentional fall through
                        start = false;
                        skip = false;
                        break outerloop;
                    case ABORTED:
                    case FAILED:
                    case CANCELED:
                    case SKIPPED:// intentional fall through
                        if (dependency.condition() == ActivityDependencyCondition.START_ON_SUCCESS) {
                            start = false;
                            skip = true;
                            skipReason = "Activity " + ta.displayName() + " is " + status
                                    + " (while the dependency condition is START_ON_SUCCESS)";
                            break outerloop;
                        }
                        break;
                    case COMPLETED:
                        if (dependency.condition() == ActivityDependencyCondition.START_ON_FAILURE) {
                            start = false;
                            skip = true;
                            skipReason = "Activity " + ta.displayName() + " is " + status
                                    + " (while the dependency condition is START_ON_FAILURE)";
                            break outerloop;
                        }
                        break;
                    }
                } else {
                    // for dependencies which are not activities, check their end
                    if (item.getStart() + item.getDuration() > now) {
                        start = false;
                        skip = false;
                        break outerloop;
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
            } else if (nextSchedule < 0 || nextSchedule > activity.getStart() - now) {
                nextSchedule = activity.getStart() - now;
            }
        }

        if (nextSchedule > 0) {
            log.debug("Scheduling startActivitiesDependentOnOthers in {} millis", nextSchedule);
            executor.schedule(this::startActivitiesDependentOnOthers, nextSchedule, TimeUnit.MILLISECONDS);
        }
    }

    private Activity doStartActivity(TimelineActivity item) {
        var systemUser = YamcsServer.getServer().getSecurityStore().getSystemUser();
        log.debug("Starting activity {}", item.displayName());
        Activity activity;
        var def = item.getActivityDefinition();
        if (def == null) {
            activity = activityService.prepareActivity(
                    ActivityService.ACTIVITY_TYPE_MANUAL,
                    Map.of("name", item.getName()),
                    systemUser, null);
        } else {
            var executorType = def.getType();
            var executorArgs = GpbWellKnownHelper.toJava(def.getArgs());
            activity = activityService.prepareActivity(executorType, executorArgs, systemUser, null);
        }
        ongoingActivitiesByRunId.put(activity.getId(), item);

        item.setStatus(ExecutionStatus.IN_PROGRESS);
        item.addRun(activity.getId());
        timelineItemDb.updateItem(item);

        activityService.startActivity(activity, systemUser);

        return activity;
    }

}
