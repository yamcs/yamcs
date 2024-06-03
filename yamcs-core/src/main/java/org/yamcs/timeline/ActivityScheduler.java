package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_START;
import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;
import static org.yamcs.timeline.TimelineItemDb.TABLE_NAME;

import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.activities.Activity;
import org.yamcs.activities.ActivityListener;
import org.yamcs.activities.ActivityService;
import org.yamcs.http.api.GpbWellKnownHelper;
import org.yamcs.http.api.StreamFactory;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

import com.google.common.util.concurrent.AbstractExecutionThreadService;

/**
 * Schedules activities in the timeline
 */
public class ActivityScheduler extends AbstractExecutionThreadService implements ItemListener, ActivityListener {

    private static final long CHECK_INTERVAL = 500;
    private static final TimelineActivity POISON = new TimelineActivity(UUID.randomUUID());
    private Log log;

    private String yamcsInstance;
    private ActivityService activityService;
    private TimelineItemDb timelineItemDb;

    // Contains all planned activities that have a calculated execution date
    private PriorityQueue<TimelineActivity> planned = new PriorityQueue<>();

    private ReentrantLock planningLock = new ReentrantLock();
    private Thread dispatcher;

    // Contains the activities that are ready to be provided to the ActivityService
    private BlockingQueue<TimelineActivity> forDispatch = new LinkedBlockingQueue<>(200);

    // Keep track of ongoing timeline activities, for the purpose of copying the activity result
    private ConcurrentMap<UUID, TimelineActivity> ongoingItemsByRunId = new ConcurrentHashMap<>();

    public Spec getSpec() {
        return new Spec();
    }

    public void init(TimelineService timelineService, YConfiguration config) {
        this.yamcsInstance = timelineService.getYamcsInstance();
        this.activityService = timelineService.getActivityService();
        this.timelineItemDb = timelineService.getTimelineItemDb();
        log = new Log(getClass(), yamcsInstance);
    }

    @Override
    protected void startUp() throws Exception {
        timelineItemDb.addItemListener(this);
        activityService.addActivityListener(this);
    }

    @Override
    protected void run() throws Exception {
        try {
            replan();
        } catch (Exception e) {
            log.error("Failed to perform initial planning", e);
            throw e;
        }

        var systemUser = YamcsServer.getServer().getSecurityStore().getSystemUser();

        TimelineActivity item;
        while ((item = forDispatch.take()) != POISON) {
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

    @Override
    protected void triggerShutdown() {
        timelineItemDb.removeItemListener(this);
        activityService.removeActivityListener(this);
        stopDispatcher();
        forDispatch.offer(POISON);
    }

    @Override
    public void onItemCreated(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            replan();
        }
    }

    @Override
    public void onItemUpdated(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            replan();
        }
    }

    @Override
    public void onItemDeleted(TimelineItem item) {
        if (item instanceof TimelineActivity) {
            replan();
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
            planningLock.lock();

            stopDispatcher();
            planned.clear();

            var sqlb = new SqlBuilder(TABLE_NAME);
            sqlb.whereColAfterOrEqual(CNAME_START, TimeEncoding.getWallclockTime());
            sqlb.where(CNAME_TYPE + " = '" + TimelineItemType.ACTIVITY.name() + "'");

            var latch = new CountDownLatch(1);
            StreamFactory.stream(yamcsInstance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    var activity = new TimelineActivity(TimelineItemType.ACTIVITY, tuple);
                    planned.add(activity);
                }

                @Override
                public void streamClosed(Stream stream) {
                    if (!planned.isEmpty()) {
                        log.info("Upcoming:");
                        for (var activity : planned) {
                            log.info("- " + activity);
                        }
                        startDispatcher();
                    }

                    latch.countDown();
                }
            });

            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            planningLock.unlock();
        }
    }

    private void startDispatcher() {
        // Dispatcher runs on a special thread, because then we can respond faster
        // to Guava service termination.
        dispatcher = new Thread(() -> {
            try {
                while (true) {
                    var nextActivity = planned.poll();
                    if (nextActivity != null) {
                        var now = TimeEncoding.getWallclockTime();
                        if (now < nextActivity.start) {
                            Thread.sleep(nextActivity.start - now);
                        }
                        if (!forDispatch.offer(nextActivity)) {
                            log.error("Failed to dispatch activity " + nextActivity + " (queue full)");
                        }
                    } else {
                        Thread.sleep(CHECK_INTERVAL);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        });
        dispatcher.start();
    }

    private void stopDispatcher() {
        var lDispatcher = dispatcher;
        if (lDispatcher != null) {
            lDispatcher.interrupt();
            dispatcher = null;
        }
    }
}
