package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.client.EventSubscription;
import org.yamcs.client.archive.ArchiveClient.ListOptions;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.utils.TimeEncoding;

import com.google.common.collect.ImmutableList;

public class EventTests extends AbstractIntegrationTest {

    @Test
    public void testFilter() throws Exception {
        var msg1 = "Oops";
        var msg2 = "Hello";
        var msg3 = "Hello World";

        var now = TimeEncoding.getWallclockTime();
        var createRequest = CreateEventRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setTime(TimeEncoding.toProtobufTimestamp(now))
                .setSource("FS")
                .setType("FC")
                .setMessage(msg1)
                .setSeverity("critical")
                .setSequenceNumber(1)
                .build();
        yamcsClient.createEvent(createRequest).get();

        createRequest = CreateEventRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setTime(TimeEncoding.toProtobufTimestamp(now + 100))
                .setSource("FS")
                .setType("EPS")
                .setMessage(msg2)
                .setSeverity("info")
                .setSequenceNumber(2)
                .build();
        yamcsClient.createEvent(createRequest).get();

        createRequest = CreateEventRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setTime(TimeEncoding.toProtobufTimestamp(now + 200))
                .setSource("FS")
                .setType("EPS")
                .setMessage(msg3)
                .setSeverity("info")
                .setSequenceNumber(3)
                .build();
        yamcsClient.createEvent(createRequest).get();

        var archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
        var page = archiveClient.listEvents(ListOptions.ascending(true)).get();
        var results = ImmutableList.copyOf(page.iterator());
        assertEquals(3, results.size());
        assertEquals(msg1, results.get(0).getMessage());
        assertEquals(msg2, results.get(1).getMessage());
        assertEquals(msg3, results.get(2).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("source=fs"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(3, results.size());
        assertEquals(msg1, results.get(0).getMessage());
        assertEquals(msg2, results.get(1).getMessage());
        assertEquals(msg3, results.get(2).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("type=eps"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(2, results.size());
        assertEquals(msg2, results.get(0).getMessage());
        assertEquals(msg3, results.get(1).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("type!=eps"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(1, results.size());
        assertEquals(msg1, results.get(0).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("severity=critical"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(1, results.size());
        assertEquals(msg1, results.get(0).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("severity=info AND type=FC"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(0, results.size());

        page = archiveClient.listEvents(
                ListOptions.filter("severity=info OR type=FC"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(3, results.size());
        assertEquals(msg1, results.get(0).getMessage());
        assertEquals(msg2, results.get(1).getMessage());
        assertEquals(msg3, results.get(2).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("seqNumber=3"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(1, results.size());
        assertEquals(msg3, results.get(0).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("seqNumber<=2"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(2, results.size());
        assertEquals(msg1, results.get(0).getMessage());
        assertEquals(msg2, results.get(1).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("hello"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(2, results.size());
        assertEquals(msg2, results.get(0).getMessage());
        assertEquals(msg3, results.get(1).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("world hello"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(1, results.size());
        assertEquals(msg3, results.get(0).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("\"world hello\""),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(0, results.size());

        page = archiveClient.listEvents(
                ListOptions.filter("\"hello world\""),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(1, results.size());
        assertEquals(msg3, results.get(0).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("message =~ \"Hello$\""),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(1, results.size());
        assertEquals(msg2, results.get(0).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("message !~ \"Hello$\""),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(2, results.size());
        assertEquals(msg1, results.get(0).getMessage());
        assertEquals(msg3, results.get(1).getMessage());

        page = archiveClient.listEvents(
                ListOptions.filter("type:s"),
                ListOptions.ascending(true)).get();
        results = ImmutableList.copyOf(page.iterator());
        assertEquals(2, results.size());
        assertEquals(msg2, results.get(0).getMessage());
        assertEquals(msg3, results.get(1).getMessage());
    }

    @Test
    public void testSubscription() throws Exception {
        EventSubscription subscription = yamcsClient.createEventSubscription();
        MessageCaptor<Event> captor = MessageCaptor.of(subscription);

        SubscribeEventsRequest request = SubscribeEventsRequest.newBuilder()
                .setInstance(yamcsInstance)
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        long now = TimeEncoding.getWallclockTime();
        CreateEventRequest createRequest = CreateEventRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setTime(TimeEncoding.toProtobufTimestamp(now))
                .setMessage("event1")
                .build();
        yamcsClient.createEvent(createRequest).get();

        Event receivedEvent = captor.expectTimely();
        assertEquals(now, TimeEncoding.fromProtobufTimestamp(receivedEvent.getGenerationTime()));
        assertEquals("event1", receivedEvent.getMessage());
    }
}
