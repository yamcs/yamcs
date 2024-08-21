package org.yamcs.pus;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.yamcs.*;
import org.yamcs.client.YamcsClient;
import org.yamcs.mdb.*;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.tests.AbstractIntegrationTest;
import org.yamcs.tests.MessageCaptor;
import org.yamcs.xtce.*;

public class PusIntegrationTest {
    
    @Mock
    Parameter eventIdParameter;

    @Mock
    XtceTmExtractor tmExtractor;

    @Mock
    SequenceContainer sequenceContainer;

    PusTmTestLink tmLink;
    static YamcsServer yamcsServer;

    YamcsClient yamcsClient;
    MessageCaptor<Event> eventCaptor;

    @BeforeAll
    public static void beforeClass() throws Exception {
        YConfiguration.setupTest("pus");

        yamcsServer = YamcsServer.getServer();
        yamcsServer.prepareStart();
        yamcsServer.start();

    }

    @BeforeEach
    public void before() throws Exception {
        tmLink = (PusTmTestLink) yamcsServer.getInstance("instance1").getLinkManager().getLink("tm_realtime");
        assertNotNull(tmLink);

        yamcsClient = YamcsClient.newBuilder("localhost", 9190)
                .withUserAgent("it-junit")
                .build();

        yamcsClient.connectWebSocket();
        var subscription = yamcsClient.createEventSubscription();
        SubscribeEventsRequest request = SubscribeEventsRequest.newBuilder()
                .setInstance("instance1")
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        eventCaptor = MessageCaptor.of(subscription);
    }

    @AfterEach
    public void after() throws InterruptedException {
        if (yamcsClient != null) {
            yamcsClient.close();
        }
    }

    @AfterAll
    public static void shutDownYamcs() throws Exception {
        YamcsServer.getServer().shutDown();
    }

    @Test
    public void testEvent1() throws Exception {
        tmLink.generateEvent1(1, (short) 2, (short) 5);
        Event ev = eventCaptor.expectTimely();

        assertEquals(EventSeverity.INFO, ev.getSeverity());
        assertEquals("this is 2 and 5", ev.getMessage());

        tmLink.generateEvent1(2, (short) 2, (short) 5);
        ev = eventCaptor.expectTimely();
        assertEquals(EventSeverity.WATCH, ev.getSeverity());

        tmLink.generateEvent1(3, (short) 2, (short) 5);
        ev = eventCaptor.expectTimely();
        assertEquals(EventSeverity.DISTRESS, ev.getSeverity());

        tmLink.generateEvent1(4, (short) 2, (short) 5);
        ev = eventCaptor.expectTimely();
        assertEquals(EventSeverity.CRITICAL, ev.getSeverity());
    }

    @Test
    public void testEvent2() throws Exception {
        tmLink.generateEvent2(1, 3.14159265f);
        Event ev = eventCaptor.expectTimely();

        assertEquals(EventSeverity.INFO, ev.getSeverity());
        assertEquals("formatted 3.142", ev.getMessage());
    }
}
