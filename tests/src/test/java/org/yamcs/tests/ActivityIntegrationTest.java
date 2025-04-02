package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.yamcs.client.utils.WellKnownTypes.TIMESTAMP_MAX;
import static org.yamcs.client.utils.WellKnownTypes.TIMESTAMP_MIN;
import static org.yamcs.client.utils.WellKnownTypes.toTimestamp;
import static org.yamcs.client.utils.WellKnownTypes.toInstant;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YamcsServer;
import org.yamcs.client.Page;
import org.yamcs.client.activity.ActivityClient;
import org.yamcs.client.timeline.TimelineClient;
import org.yamcs.protobuf.ActivityDependency;
import org.yamcs.protobuf.ActivityDependencyCondition;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.TimelineItem;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.protobuf.activities.ActivityStatus;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.util.Durations;

public class ActivityIntegrationTest extends AbstractIntegrationTest {
    private TimelineClient timelineClient;
    private ActivityClient activityClient;

    private TimeService timeService;
    private Duration onesec = Duration.ofSeconds(1);
    private Duration fivesec = Duration.ofSeconds(5);

    @BeforeEach
    public void prepareTests() throws Exception {
        timelineClient = yamcsClient.createTimelineClient(yamcsInstance);
        activityClient = yamcsClient.createActivityClient(yamcsInstance);

        for (TimelineItem item : timelineClient.getItems(null, null, null).get()) {
            timelineClient.deleteItem(item.getId()).get();
        }
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Test
    public void testActivityDependencyInvalid1() throws Exception {
        verifyEmpty();
        var start = TimeEncoding.toJavaInstant(timeService.getMissionTime());

        var activity1 = a("activity1", start, onesec);
        activity1 = timelineClient.addItem(activity1).get();

        var dep = ActivityDependency.newBuilder().setCondition(ActivityDependencyCondition.START_ON_SUCCESS)
                .setId(activity1.getId()).build();

        TimelineItem activity2 = a("activity2", start, onesec, Arrays.asList(dep));
        // one cannot specify both dependence and start time
        Exception exception = assertThrows(ExecutionException.class, () -> timelineClient.addItem(activity2).get());
        assertTrue(exception.getMessage()
                .contains("Cannot specify start when relative time or dependsOn are also specified"));
    }

    @Test
    public void testActivityDependencyInvalid2() throws Exception {
        verifyEmpty();
        var id = UUID.randomUUID().toString();
        var dep = ActivityDependency.newBuilder().setCondition(ActivityDependencyCondition.START_ON_SUCCESS)
                .setId(id).build();

        TimelineItem activity2 = a("activity2", null, onesec, Arrays.asList(dep));
        // depends on does not exist
        Exception exception = assertThrows(ExecutionException.class, () -> timelineClient.addItem(activity2).get());
        assertTrue(exception.getMessage()
                .contains(id + " referenced as a dependency was not found"));

    }

    @Test
    public void testActivityDependency() throws Exception {
        verifyEmpty();
        var start = TimeEncoding.toJavaInstant(timeService.getMissionTime());

        // activity1 with autoStart = false
        TimelineItem activity1 = a("activity1", start, onesec);
        activity1 = timelineClient.addItem(activity1).get();


        // activity1 with autoStart = true
        TimelineItem activity2 = a("activity2", start.plusMillis(1), fivesec, null, true);
        activity2 = timelineClient.addItem(activity2).get();
        assertTrue(activity2.getAutoStart());

        // activity3 depending on activity1 and activity2
        var dep1 = ActivityDependency.newBuilder().setCondition(ActivityDependencyCondition.START_ON_SUCCESS)
                .setId(activity1.getId()).build();
        var dep2 = ActivityDependency.newBuilder().setCondition(ActivityDependencyCondition.START_ON_SUCCESS)
                .setId(activity2.getId()).build();

        TimelineItem activity3 = a("activity3", null, onesec, Arrays.asList(dep1, dep2));

        activity3 = timelineClient.addItem(activity3).get();
        assertEquals(2, activity3.getDependsOnCount());

        // the activity3 start should be set to the end of activity2
        assertEquals(toInstant(activity2.getStart()).plus(fivesec), toInstant(activity3.getStart()));

        Page<TimelineItem> page = timelineClient.getItems(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get();
        // check that we have three activities, first one should be READY, second one in progress and third one planned
        var it = page.iterator();
        var activity1r = it.next();
        assertEquals(activity1.getId(), activity1r.getId());
        assertEquals(ExecutionStatus.READY, activity1r.getStatus());

        var activity2r = it.next();
        assertEquals(activity2r.getId(), activity2r.getId());
        assertEquals(ExecutionStatus.IN_PROGRESS, activity2r.getStatus());

        var activity3r = it.next();
        assertEquals(activity3, activity3r);
        assertEquals(ExecutionStatus.PLANNED, activity3r.getStatus());

        // verify that activity 2 has been started automatically
        var ait = activityClient.getActivities(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get().iterator();
        var a2 = ait.next();
        assertEquals("activity2", a2.getDetail());
        assertFalse(ait.hasNext());


        // start activity1
        var activity1i = timelineClient.startActivity(activity1.getId()).get();
        assertEquals(ActivityStatus.RUNNING, activity1i.getStatus());


        ait = activityClient.getActivities(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get().iterator();
        var a1r = ait.next();
        assertEquals("activity1", a1r.getDetail());
        var a2r = ait.next();
        assertEquals("activity2", a2r.getDetail());
        assertFalse(ait.hasNext());


        // wait that 5 seconds have elapsed so that the start of activity3 kicks in and it should be waiting for
        // dependency
        Thread.sleep(5000);
        it = timelineClient.getItems(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get().iterator();
        var activity1r1 = it.next();
        assertEquals(activity1.getId(), activity1r1.getId());
        assertEquals(ExecutionStatus.IN_PROGRESS, activity1r1.getStatus());

        var activity2r1 = it.next();
        assertEquals(activity2.getId(), activity2r1.getId());
        assertEquals(ExecutionStatus.IN_PROGRESS, activity2r1.getStatus());

        var activity3r1 = it.next();
        assertEquals(activity3.getId(), activity3r1.getId());
        assertEquals(ExecutionStatus.WAITING_ON_DEPENDENCY, activity3r1.getStatus());

        // complete the activity1 and activity2
        activityClient.complete(a1r.getId(), null).get();
        activityClient.complete(a2r.getId(), null).get();

        Thread.sleep(1000);

        // check that activity1 and activity2 are completed and activity3 is READY
        it = timelineClient.getItems(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get().iterator();
        var activity1r2 = it.next();
        assertEquals(activity1.getId(), activity1r2.getId());
        assertEquals(ExecutionStatus.COMPLETED, activity1r2.getStatus());

        var activity2r2 = it.next();
        assertEquals(activity2.getId(), activity2r2.getId());
        assertEquals(ExecutionStatus.COMPLETED, activity2r2.getStatus());

        var activity3r2 = it.next();
        assertEquals(activity3.getId(), activity3r2.getId());
        assertEquals(ExecutionStatus.READY, activity3r2.getStatus());

        ait = activityClient.getActivities(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get().iterator();
        var a1r1 = ait.next();
        assertEquals("activity1", a1r.getDetail());
        assertEquals(ActivityStatus.SUCCESSFUL, a1r1.getStatus());
        var a2r1 = ait.next();
        assertEquals("activity2", a2r.getDetail());
        assertEquals(ActivityStatus.SUCCESSFUL, a2r1.getStatus());
        assertFalse(ait.hasNext());
    }

    void verifyEmpty() throws Exception {
        Page<TimelineItem> page = timelineClient.getItems(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get();
        assertFalse(page.iterator().hasNext());
        assertFalse(page.hasNextPage());
    }

    TimelineItem a(String name, Instant start, Duration duration) {
        return a(name, start, duration, null);
    }

    TimelineItem a(String name, Instant start, Duration duration, List<ActivityDependency> deps) {
        return a(name, start, duration, deps, false);
    }

    TimelineItem a(String name, Instant start, Duration duration, List<ActivityDependency> deps, boolean autoStart) {
        var b = TimelineItem.newBuilder()
                .setType(TimelineItemType.ACTIVITY)
                .setName(name)
                .setAutoStart(autoStart);

        if (duration != null) {
            b.setDuration(Durations.fromMillis(duration.toMillis()));
        }
        if (start != null) {
            b.setStart(toTimestamp(start));
        }

        if (deps != null) {
            b.addAllDependsOn(deps);
        }
        return b.build();
    }
}
