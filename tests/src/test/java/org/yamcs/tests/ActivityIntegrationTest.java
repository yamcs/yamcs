package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.yamcs.client.utils.WellKnownTypes.TIMESTAMP_MAX;
import static org.yamcs.client.utils.WellKnownTypes.TIMESTAMP_MIN;
import static org.yamcs.client.utils.WellKnownTypes.toTimestamp;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.YamcsServer;
import org.yamcs.client.ClientException;
import org.yamcs.client.Page;
import org.yamcs.client.timeline.TimelineClient;
import org.yamcs.protobuf.ActivityDependency;
import org.yamcs.protobuf.ActivityDependencyCondition;
import org.yamcs.protobuf.TimelineItem;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.util.Durations;

public class ActivityIntegrationTest extends AbstractIntegrationTest {
    private TimelineClient timelineClient;
    private TimeService timeService;

    @BeforeEach
    public void prepareTests() throws Exception {
        timelineClient = yamcsClient.createTimelineClient(yamcsInstance, "realtime");

        for (TimelineItem item : timelineClient.getItems(null, null, null).get()) {
            timelineClient.deleteItem(item.getId()).get();
        }
        timeService = YamcsServer.getTimeService(yamcsInstance);
    }

    @Test
    public void testActivityDependency() throws Exception {
        verifyEmpty();
        var start = TimeEncoding.toJavaInstant(timeService.getMissionTime());

        TimelineItem activity1 = TimelineItem.newBuilder()
                .setType(TimelineItemType.ACTIVITY)
                .setStart(toTimestamp(start))
                .setDuration(Durations.fromMillis(1001))
                .setName("activity1")
                .build();

        activity1 = timelineClient.addItem(activity1).get();

        var dep = ActivityDependency.newBuilder().setCondition(ActivityDependencyCondition.START_ON_SUCCESS)
                .setId(activity1.getId()).build();
        
        TimelineItem activity2 = TimelineItem.newBuilder()
                .setType(TimelineItemType.ACTIVITY)
                .setName("activity2")
                .addDependsOn(dep)
                .setDuration(Durations.fromMillis(1001))
                .build();
        activity2 = timelineClient.addItem(activity2).get();
        assertEquals(1, activity2.getDependsOnCount());

    }

    void verifyEmpty() throws Exception {
        Page<TimelineItem> page = timelineClient.getItems(TIMESTAMP_MIN, TIMESTAMP_MAX, null).get();
        assertFalse(page.iterator().hasNext());
        assertFalse(page.hasNextPage());
    }

    private Void verifyException(Throwable t, String type) {
        ClientException e = (ClientException) t;
        assertEquals(type, e.getDetail().getType());
        return null;
    }
}
