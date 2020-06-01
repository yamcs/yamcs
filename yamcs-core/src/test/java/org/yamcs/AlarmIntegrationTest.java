package org.yamcs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.yamcs.client.AlarmSubscription;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.AlarmNotificationType;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.EventAlarmData;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.alarms.EditAlarmRequest;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsResponse;
import org.yamcs.protobuf.alarms.SubscribeAlarmsRequest;

public class AlarmIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void testEventAlarms() throws Exception {
        AlarmSubscription subscription = yamcsClient.createAlarmSubscription();
        MessageCaptor<AlarmData> captor = MessageCaptor.of(subscription);

        SubscribeAlarmsRequest request = SubscribeAlarmsRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .build();
        subscription.sendMessage(request);
        Thread.sleep(2000);

        CreateEventRequest createRequest = CreateEventRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setSeverity("warning")
                .setSource("IntegrationTest")
                .setType("Event-Alarm-Test")
                .setMessage("event1")
                .build();
        yamcsClient.createEvent(createRequest).get();

        AlarmData a1 = captor.expectTimely();
        EventAlarmData ea1 = a1.getEventDetail();

        assertEquals(EventSeverity.WARNING, ea1.getTriggerEvent().getSeverity());
        assertEquals("IntegrationTest", ea1.getTriggerEvent().getSource());
        assertEquals("Event-Alarm-Test", ea1.getTriggerEvent().getType());
        assertEquals("event1", ea1.getTriggerEvent().getMessage());

        EditAlarmRequest ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(a1.getId().getNamespace() + "/" + a1.getId().getName())
                .setSeqnum(a1.getSeqNum())
                .setState("shelved")
                .setComment("I will deal with this later")
                .setShelveDuration(500).build();
        yamcsClient.editAlarm(ear).get();
        AlarmData a2 = captor.expectTimely();
        assertEquals(AlarmNotificationType.SHELVED, a2.getNotificationType());
        assertTrue(a2.hasShelveInfo());
        assertEquals("I will deal with this later", a2.getShelveInfo().getShelveMessage());

        // after 500 millisec, the shelving has expired
        AlarmData a3 = captor.expectTimely();
        assertEquals(AlarmNotificationType.UNSHELVED, a3.getNotificationType());

        // shelve it again
        ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(a1.getId().getNamespace() + "/" + a1.getId().getName())
                .setSeqnum(a1.getSeqNum())
                .setState("shelved")
                .setComment("I will deal with this later#2")
                .build();
        yamcsClient.editAlarm(ear).get();
        a2 = captor.expectTimely();
        assertEquals(AlarmNotificationType.SHELVED, a2.getNotificationType());
        a3 = captor.poll(2000);
        assertNull(a3);

        ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(a1.getId().getNamespace() + "/" + a1.getId().getName())
                .setSeqnum(a1.getSeqNum())
                .setState("acknowledged")
                .setComment("a nice ack explanation")
                .build();
        yamcsClient.editAlarm(ear).get();
        AlarmData a4 = captor.expectTimely();

        assertEquals("a nice ack explanation", a4.getAcknowledgeInfo().getAcknowledgeMessage());

        ListProcessorAlarmsResponse lar = yamcsClient.listAlarms(yamcsInstance, "realtime").get();

        assertEquals(1, lar.getAlarmsCount());
        assertEquals("a nice ack explanation", lar.getAlarms(0).getAcknowledgeInfo().getAcknowledgeMessage());

        ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(a1.getId().getNamespace() + "/" + a1.getId().getName())
                .setSeqnum(a1.getSeqNum())
                .setState("cleared")
                .setComment("a nice clear explanation")
                .build();

        yamcsClient.editAlarm(ear).get();

        AlarmData a5 = captor.expectTimely();
        assertTrue(a5.hasClearInfo());
        assertEquals("a nice clear explanation", a5.getClearInfo().getClearMessage());

        lar = yamcsClient.listAlarms(yamcsInstance, "realtime").get();
        assertEquals(0, lar.getAlarmsCount());
    }
}
