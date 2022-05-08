package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.client.EventSubscription;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.utils.TimeEncoding;

public class EventTests extends AbstractIntegrationTest {

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
