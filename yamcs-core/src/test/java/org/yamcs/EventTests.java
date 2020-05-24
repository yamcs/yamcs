package org.yamcs;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.yamcs.client.EventSubscription;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;

import io.netty.handler.codec.http.HttpMethod;

public class EventTests extends AbstractIntegrationTest {

    @Test
    public void testSubscription() throws Exception {
        EventSubscription subscription = yamcsClient.createEventSubscription();
        MessageCaptor<Event> captor = MessageCaptor.of(subscription);

        SubscribeEventsRequest request = SubscribeEventsRequest.newBuilder()
                .setInstance(yamcsInstance)
                .build();
        subscription.sendMessage(request);
        Thread.sleep(2000);

        long now = TimeEncoding.getWallclockTime();
        CreateEventRequest createRequest = CreateEventRequest.newBuilder()
                .setTime(TimeEncoding.toString(now))
                .setMessage("event1")
                .build();
        restClient.doRequest("/archive/" + yamcsInstance + "/events", HttpMethod.POST, createRequest);

        Event receivedEvent = captor.expectTimely();
        assertEquals(now, TimeEncoding.parse(receivedEvent.getGenerationTimeUTC()));
        assertEquals("event1", receivedEvent.getMessage());
    }
}
