package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_START;
import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;
import static org.yamcs.timeline.TimelineItemDb.CNAME_STATUS;
import static org.yamcs.timeline.TimelineItemDb.TABLE_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
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
    private ConcurrentMap<UUID, TimelineActivity> ongoingItemsByRunId = new ConcurrentHashMap<>();

    // list of activities that depend on others to be able to start
    List<TimelineActivity> dependentActivities = new ArrayList<>();

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
        System.out.println(" activity scheduler started");
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
        var item = ongoingItemsByRunId.get(activity.getId());
        if (item != null && activity.isStopped()) {
            ongoingItemsByRunId.remove(activity.getId());

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

            var sqlb = new SqlBuilder(TABLE_NAME);
            sqlb.whereColAfterOrEqual(CNAME_START, timeService.getMissionTime() - schedulingMargin);
            sqlb.where(CNAME_TYPE + " = '" + TimelineItemType.ACTIVITY.name() + "'");
            sqlb.where(CNAME_STATUS + " = '" + ExecutionStatus.PLANNED.name() + "'");
            var ydb = YarchDatabase.getInstance(yamcsInstance);

            var result = ydb.execute(sqlb.toString(), sqlb.getQueryArguments());
            System.out.println("in replan result: " + result);
            while (result.hasNext()) {
                var tuple = result.next();
                System.out.println("tuple: " + tuple);
                try {
                    var activity = new TimelineActivity(TimelineItemType.ACTIVITY, tuple);
                    if (activity.dependsOn != null && !activity.dependsOn.isEmpty()) {
                        dependentActivities.add(activity);
                    } else {
                        planned.add(activity);
                    }
                } catch (Exception e) {
                    log.warn("Unable to load active alarm from tuple {}: {}", tuple, e);
                }
            }
            if (!planned.isEmpty()) {
                log.info("Upcoming:");
                for (var activity : planned) {
                    log.info("- " + activity);
                }
                startActivities();
            }

        } catch (ParseException | StreamSqlException e) {
            log.warn("Error retrieving activities: ", e);
        }
        startActivitiesDependentOnOthers();
    }

    private void startActivitiesDependentOnOthers() {
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
                    case IN_PROGRESS: // intentional fall through
                        start = false;
                        skip = false;
                        break outerloop;
                    case ABORTED: // intentional fall through
                    case FAILED:
                    case SKIPPED:
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
                    var now = timeService.getMissionTime();
                    if (item.getStart() + item.getDuration() > now) {
                        start = false;
                        skip = false;
                        break outerloop;
                    }
                }
            }
            if (start) {
                log.debug("Starting activity {}", activity.displayName());
                startActivity(activity);
            } else if (skip) {
                activity.setStatus(ExecutionStatus.SKIPPED);
                activity.setFailureReason(skipReason);
                timelineItemDb.updateItem(activity);
            }
        }
    }

    private void startActivities() {
        TimelineActivity nextActivity;
        while ((nextActivity = planned.poll()) != null) {
            var now = timeService.getMissionTime();
            if (now < nextActivity.start) {
                executor.schedule(this::startActivities, nextActivity.start - now, TimeUnit.MILLISECONDS);
                break;
            } else {
                startActivity(nextActivity);
            }
        }
    }

    private void startActivity(TimelineActivity item) {
        var systemUser = YamcsServer.getServer().getSecurityStore().getSystemUser();

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
        ongoingItemsByRunId.put(activity.getId(), item);

        item.setStatus(ExecutionStatus.IN_PROGRESS);
        item.addRun(activity.getId());
        timelineItemDb.updateItem(item);

        activityService.startActivity(activity, systemUser);
    }

}
