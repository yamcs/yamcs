package org.yamcs;

import com.google.protobuf.util.Durations;
import org.junit.Before;
import org.junit.Test;
import org.yamcs.client.ClientException;
import org.yamcs.client.Page;
import org.yamcs.client.timeline.TimelineClient;
import org.yamcs.protobuf.*;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.yamcs.client.utils.TimeUtils.*;

public class TimelineIntegrationTest extends AbstractIntegrationTest {
    private TimelineClient timelineClient;
    private YarchDatabaseInstance ydb;

    @Before
    public void prepareTests() throws InterruptedException {
        timelineClient = yamcsClient.createTimelineClient(yamcsInstance, "realtime");
        ydb = YarchDatabase.getInstance(yamcsInstance);

    }

    @Test
    public void testGetSources() throws Exception {
        Map<String, TimelineSourceCapabilities> sources = timelineClient.getSources().get();
        assertEquals(1, sources.size());
        TimelineSourceCapabilities c = sources.get("rdb");
        assertNotNull(c);
        assertFalse(c.getReadOnly());
        assertTrue(c.getHasActivityGroups());
    }

    @Test
    public void testItem1() throws Exception {
        verifyEmpty();
        TimelineItem item1a = TimelineItem.newBuilder()
                .setType(TimelineItemType.EVENT)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .addTags("tag1")
                .addTags("tag2")
                .build();

        TimelineItem item1b = timelineClient.addItem(item1a).get();
        assertEquals(item1a.getStart(), item1b.getStart());
        assertEquals(item1a.getDuration(), item1b.getDuration());
        assertEquals(item1a.getTagsList(), item1b.getTagsList());

        TimelineItem item1c = timelineClient.getItem(item1b.getUuid()).get();

        assertEquals(item1b, item1c);

        List<String> tags = timelineClient.getTags().get();
        assertEquals(Arrays.asList("tag1", "tag2"), tags);

        TimelineItem item1d = item1b.toBuilder().addTags("tag3")
                .setStart(toTimestamp(Instant.parse("2020-01-25T00:00:00Z"))).build();

        TimelineItem item1e = timelineClient.updateItem(item1d).get();
        assertEquals(item1d, item1e);

        TimelineItem item1f = timelineClient.getItem(item1b.getUuid()).get();
        assertEquals(item1d, item1f);

        tags = timelineClient.getTags().get();
        assertEquals(Arrays.asList("tag1", "tag2", "tag3"), tags);

        TimelineItem item1g = timelineClient.deleteItem(item1b.getUuid()).get();
        assertEquals(item1d, item1g);

        Throwable t = null;

        try {
            timelineClient.getItem(item1b.getUuid()).get();
        } catch (ExecutionException e) {
            t = e.getCause();
        }
        assertNotNull(t);
        assertTrue(t.getMessage().contains("not found"));
    }

    @Test
    public void testItem2() throws Exception {
        verifyEmpty();
        TimelineItem item1a = TimelineItem.newBuilder()
                .setType(TimelineItemType.EVENT)
                .setStart(toTimestamp(Instant.parse("2020-01-11T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .addTags("tag1")
                .addTags("tag2")
                .build();
        TimelineItem item1b = TimelineItem.newBuilder()
                .setType(TimelineItemType.EVENT)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .addTags("tag2")
                .addTags("tag3")
                .build();
        TimelineBand band1a = TimelineBand.newBuilder()
                .setType(TimelineBandType.BANDEVENT)
                .setName("name1a")
                .setShared(true)
                .addTags("tag2")
                .build();
        item1a = timelineClient.addItem(item1a).get();
        item1b = timelineClient.addItem(item1b).get();
        band1a = timelineClient.addBand(band1a).get();

        Page<TimelineItem> page = timelineClient.getItems(
                Instant.parse("2020-01-20T00:00:00Z"),
                Instant.parse("2020-01-22T00:00:00Z"),
                band1a)
                .get();
        Iterator<TimelineItem> iterator = page.iterator();
        TimelineItem item1 = iterator.next();
        assertEquals("tag2",item1.getTags(0));
        assertEquals("tag3",item1.getTags(1));
        assertEquals(false,iterator.hasNext());

    }


    @Test
    public void testGroup1() throws Exception {
        verifyEmpty();
        // create group
        TimelineItem group = TimelineItem.newBuilder()
                .setType(TimelineItemType.ITEM_GROUP)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .build();
        group = timelineClient.addItem(group).get();
        // create event1 in group
        TimelineItem event1 = TimelineItem.newBuilder()
                .setType(TimelineItemType.EVENT)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .setGroupUuid(group.getUuid())
                .build();
        event1 = timelineClient.addItem(event1).get();
        // create event2 in group
        TimelineItem event2 = TimelineItem.newBuilder()
                .setType(TimelineItemType.EVENT)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .setGroupUuid(group.getUuid())
                .build();
        event2 = timelineClient.addItem(event2).get();
        // try to remove group => error
        timelineClient.deleteItem(group.getUuid()).handle((item,t)->{assertNotNull(t);return null;}).get();
        // remove event1 from group
        event1 = event1.toBuilder().clearGroupUuid().build();
        event1 = timelineClient.updateItem(event1).get();
        // try to remove group => error
        timelineClient.deleteItem(group.getUuid()).handle((item,t)->{assertNotNull(t);return null;}).get();
        // remove group via deleteTimelineGroup
        timelineClient.deleteTimelineGroup(group.getUuid()).get();
        // verify that group and event2 are gone
        timelineClient.getItem(group.getUuid()).handle((item, t)-> verifyException(t,"NotFoundException")).get();
        timelineClient.getItem(event2.getUuid()).handle((item, t)->verifyException(t,"NotFoundException")).get();
        // verify that event1 is still there
        event1 = timelineClient.getItem(event1.getUuid()).get();
        assertNotNull(event1);
    }

    @Test
    public void testGroup2() throws Exception {
        verifyEmpty();
        // create group
        TimelineItem group = TimelineItem.newBuilder()
                .setType(TimelineItemType.ITEM_GROUP)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .build();
        group = timelineClient.addItem(group).get();
        // create activity group
        TimelineItem activityGroup = TimelineItem.newBuilder()
                .setType(TimelineItemType.ACTIVITY_GROUP)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .build();
        activityGroup = timelineClient.addItem(activityGroup).get();
        // create event
        TimelineItem event = TimelineItem.newBuilder()
                .setType(TimelineItemType.EVENT)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .build();
        event = timelineClient.addItem(event).get();
        // create activity
        TimelineItem activity = TimelineItem.newBuilder()
                .setType(TimelineItemType.MANUAL_ACTIVITY)
                .setStart(toTimestamp(Instant.parse("2020-01-21T00:00:00Z")))
                .setDuration(Durations.fromMillis(1001))
                .build();
        activity = timelineClient.addItem(activity).get();
        // try to add event to "group" activity => error
        event = event.toBuilder().setGroupUuid(activity.getUuid()).build();
        timelineClient.updateItem(event).handle((item, t)-> verifyException(t,"BadRequestException")).get();
        // try to add event to group => ok
        event = event.toBuilder().setGroupUuid(group.getUuid()).build();
        timelineClient.updateItem(event).get();
        // try to add event to activityGroup => error
        event = event.toBuilder().setGroupUuid(activityGroup.getUuid()).build();
        timelineClient.updateItem(event).handle((item, t)-> verifyException(t,"BadRequestException")).get();
        // try to add activity to activityGroup => ok
        activity = activity.toBuilder().setGroupUuid(activityGroup.getUuid()).build();
        timelineClient.updateItem(activity).get();
    }

    @Test
    public void testBand1() throws Exception {
        verifyEmpty();
        TimelineBand band1a = TimelineBand.newBuilder()
                .setType(TimelineBandType.BANDEVENT)
                .setName("name")
                .setDescription("description")
                .setShared(true)
                .addTags("tag1")
                .addTags("tag2")
                .putAllProperties(Collections.singletonMap("key1", "value1"))
                .build();

        TimelineBand band1b = timelineClient.addBand(band1a)
                .get();
        assertEquals(band1a.getName(), band1b.getName());
        assertEquals(band1a.getDescription(), band1b.getDescription());
        assertEquals(band1a.getTagsList(), band1b.getTagsList());
        assertEquals(band1a.getPropertiesMap(), band1b.getPropertiesMap());
    }

    @Test
    public void testBand2() throws Exception {
        verifyEmpty();
        TimelineBand band1a = TimelineBand.newBuilder()
                .setType(TimelineBandType.BANDEVENT)
                .setName("name1a")
                .setShared(true)
                .build();
        TimelineBand band1b = TimelineBand.newBuilder()
                .setType(TimelineBandType.BANDEVENT)
                .setName("name1b")
                .setShared(false)
                .build();
        TimelineBand band1c = TimelineBand.newBuilder()
                .setType(TimelineBandType.BANDEVENT)
                .setName("name1c")
                .setShared(false)
                .build();

        band1a = timelineClient.addBand(band1a).get();
        band1b = timelineClient.addBand(band1b).get();
        band1c = timelineClient.addBand(band1c).get();

        List<TimelineBand> timelineBands = timelineClient.getBands().get();
        assertEquals(3,timelineBands.size());

        ydb.execute("update timeline_band set username='blabla'");
        timelineBands = timelineClient.getBands().get();
        assertEquals(1,timelineBands.size());
        assertEquals("name1a",timelineBands.get(0).getName());
    }

    @Test
    public void testInvalidSource() throws Exception {
        TimelineItem item = TimelineItem.newBuilder().setType(TimelineItemType.EVENT).build();
        Throwable t = null;
        try {
            timelineClient.addItem("invalid-source", item).get();
        } catch (ExecutionException e) {
            t = e.getCause();
        }
        assertNotNull(t);
        assertTrue(t.toString().contains("Invalid"));
    }

    void verifyEmpty() throws Exception {
        Page<TimelineItem> page = timelineClient.getItems(TIMESTAMP_MIN, TIMESTAMP_MAX,null).get();
        assertFalse(page.iterator().hasNext());
        assertFalse(page.hasNextPage());
    }

    private Void verifyException(Throwable t, String type) {
        ClientException e = (ClientException)t;
        assertEquals(type,((ClientException) t).getDetail().getType());
        return null;
    }
}
