package org.yamcs.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.yamcs.client.AlarmSubscription;
import org.yamcs.client.GlobalAlarmStatusSubscription;
import org.yamcs.client.archive.ArchiveClient;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.AlarmNotificationType;
import org.yamcs.protobuf.AlarmSeverity;
import org.yamcs.protobuf.AlarmType;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.EventAlarmData;
import org.yamcs.protobuf.alarms.EditAlarmRequest;
import org.yamcs.protobuf.alarms.GlobalAlarmStatus;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsResponse;
import org.yamcs.protobuf.alarms.SubscribeAlarmsRequest;
import org.yamcs.protobuf.alarms.SubscribeGlobalStatusRequest;
import org.yamcs.utils.TimeEncoding;

public class AlarmIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void testEventAlarms() throws Exception {
        AlarmSubscription subscription = subscribeAlarms();
        MessageCaptor<AlarmData> captor = MessageCaptor.of(subscription);

        var event = createWarningEvent();

        AlarmData a1 = captor.expectTimely();
        EventAlarmData ea1 = a1.getEventDetail();

        assertEquals(EventSeverity.WARNING, ea1.getTriggerEvent().getSeverity());
        assertEquals(event.getSource(), ea1.getTriggerEvent().getSource());
        assertEquals(event.getType(), ea1.getTriggerEvent().getType());
        assertEquals(event.getMessage(), ea1.getTriggerEvent().getMessage());

        shelveAlarm(a1.getId().getNamespace() + "/" + a1.getId().getName(), a1.getSeqNum(), 500,
                "I will deal with this later");

        AlarmData a2 = captor.expectTimely();
        assertEquals(AlarmNotificationType.SHELVED, a2.getNotificationType());
        assertTrue(a2.hasShelveInfo());
        assertEquals("I will deal with this later", a2.getShelveInfo().getShelveMessage());

        // after 500 millisec, the shelving has expired
        AlarmData a3 = captor.expectTimely();
        assertEquals(AlarmNotificationType.UNSHELVED, a3.getNotificationType());

        // shelve it again
        shelveAlarm(a1.getId().getNamespace() + "/" + a1.getId().getName(), a1.getSeqNum(), -1,
                "I will deal with this later#2");

        a2 = captor.expectTimely();
        assertEquals(AlarmNotificationType.SHELVED, a2.getNotificationType());
        a3 = captor.poll(2000);
        assertNull(a3);

        acknowledgeAlarm(a1.getId().getNamespace() + "/" + a1.getId().getName(), a1.getSeqNum(),
                "a nice ack explanation");
        AlarmData a4 = captor.expectTimely();

        assertEquals("a nice ack explanation", a4.getAcknowledgeInfo().getAcknowledgeMessage());

        ListProcessorAlarmsResponse lar = yamcsClient.listAlarms(yamcsInstance, "realtime").get();

        assertEquals(1, lar.getAlarmsCount());
        assertEquals("a nice ack explanation", lar.getAlarms(0).getAcknowledgeInfo().getAcknowledgeMessage());

        clearAlarm(a1.getId().getNamespace() + "/" + a1.getId().getName(), a1.getSeqNum(),
                "a nice clear explanation");

        AlarmData a5 = captor.expectTimely();
        assertTrue(a5.hasClearInfo());
        assertEquals("a nice clear explanation", a5.getClearInfo().getClearMessage());

        lar = yamcsClient.listAlarms(yamcsInstance, "realtime").get();
        assertEquals(0, lar.getAlarmsCount());

        // check the archive
        ListAlarmsResponse lar1 = yamcsClient.listAlarms(yamcsInstance).get();
        assertEquals(1, lar1.getAlarmsCount());
        AlarmData a6 = lar1.getAlarms(0);
        assertEquals("a nice clear explanation", a6.getClearInfo().getClearMessage());
    }

    @Test
    public void testParamAlarms() throws Exception {
        AlarmSubscription subscription = subscribeAlarms();
        MessageCaptor<AlarmData> captor = MessageCaptor.of(subscription);

        packetGenerator.setGenerationTime(TimeEncoding.parse("2022-01-19T21:21:00"));
        // this generates a EnumerationPara1_10_2 WARNING and a FloatPara1_10_3 DISTRESS
        packetGenerator.generate_PKT1_10(0, 3, 51);

        packetGenerator.setGenerationTime(TimeEncoding.parse("2022-01-19T21:21:01"));
        // this increases the severity of FloatPara1_10_3 to CRITICAL
        packetGenerator.generate_PKT1_10(0, 3, 70);

        AlarmData a1 = captor.expectTimely();
        assertEquals("EnumerationPara1_10_2", a1.getId().getName());
        assertEquals(AlarmSeverity.WARNING, a1.getSeverity());

        AlarmData a2 = captor.expectTimely();
        assertEquals("FloatPara1_10_3", a2.getId().getName());
        assertEquals(AlarmSeverity.DISTRESS, a2.getSeverity());
        assertEquals(1, a2.getCount());

        AlarmData a3 = captor.expectTimely();
        assertEquals("EnumerationPara1_10_2", a3.getId().getName());
        assertEquals(AlarmSeverity.WARNING, a3.getSeverity());
        assertEquals(AlarmNotificationType.VALUE_UPDATED, a3.getNotificationType());

        AlarmData a4 = captor.expectTimely();
        assertEquals("FloatPara1_10_3", a4.getId().getName());
        assertEquals(AlarmSeverity.CRITICAL, a4.getSeverity());
        assertEquals(AlarmNotificationType.SEVERITY_INCREASED, a4.getNotificationType());
        assertEquals(2, a4.getCount());

        // shelve
        EditAlarmRequest ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName("/REFMDB/SUBSYS1/FloatPara1_10_3")
                .setSeqnum(a2.getSeqNum())
                .setState("shelved")
                .setComment("I will deal with this later")
                .setShelveDuration(200000).build();

        yamcsClient.editAlarm(ear);

        AlarmData a5 = captor.expectTimely();
        assertEquals("FloatPara1_10_3", a5.getId().getName());
        assertEquals(AlarmSeverity.CRITICAL, a5.getSeverity());
        assertEquals(AlarmNotificationType.SHELVED, a5.getNotificationType());

        // unshelve
        ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName("/REFMDB/SUBSYS1/FloatPara1_10_3")
                .setSeqnum(a2.getSeqNum())
                .setState("unshelved")
                .build();

        yamcsClient.editAlarm(ear);
        AlarmData a6 = captor.expectTimely();
        assertEquals("FloatPara1_10_3", a6.getId().getName());
        assertEquals(AlarmSeverity.CRITICAL, a6.getSeverity());
        assertEquals(AlarmNotificationType.UNSHELVED, a6.getNotificationType());

        // check the archive
        Instant t0 = Instant.parse("2022-01-19T21:21:00Z");
        Instant t1 = t0.plusSeconds(2);
        ArchiveClient archiveClient = yamcsClient.createArchiveClient(yamcsInstance);
        List<AlarmData> l1 = archiveClient.listAlarms(t0, t1).get().stream()
                .filter(alarm -> alarm.getType() == AlarmType.PARAMETER)
                .collect(Collectors.toList());
        assertEquals(2, l1.size());

        List<AlarmData> l2 = archiveClient.listAlarms("/REFMDB/SUBSYS1/FloatPara1_10_3", t0, t1).get();
        assertEquals(1, l2.size());
    }

    @Test
    public void testGlobalStatusSubscription() throws Exception {
        GlobalAlarmStatusSubscription subscription = yamcsClient.createGlobalAlarmStatusSubscription();
        MessageCaptor<GlobalAlarmStatus> captor = MessageCaptor.of(subscription);

        SubscribeGlobalStatusRequest request = SubscribeGlobalStatusRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        GlobalAlarmStatus s0 = captor.expectTimely();
        assertEquals(0, s0.getUnacknowledgedCount());

        packetGenerator.setGenerationTime(TimeEncoding.parse("2022-01-19T23:59:00"));
        packetGenerator.generate_PKT1_10(0, 3, 51);

        GlobalAlarmStatus s1 = captor.expectTimely();
        assertEquals(2, s1.getUnacknowledgedCount());
        assertTrue(s1.getUnacknowledgedActive());
        assertFalse(s1.getAcknowledgedActive());
        assertFalse(s1.getShelvedActive());

        ListProcessorAlarmsResponse lar = yamcsClient.listAlarms(yamcsInstance, "realtime").get();
        assertEquals(2, lar.getAlarmsCount());
        AlarmData a1 = lar.getAlarms(1);

        // acknowledge
        EditAlarmRequest ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(a1.getId().getNamespace() + "/" + a1.getId().getName())
                .setSeqnum(a1.getSeqNum())
                .setState("acknowledged")
                .setComment("all is good")
                .build();

        yamcsClient.editAlarm(ear);

        GlobalAlarmStatus s2 = captor.expectTimely();
        assertEquals(1, s2.getUnacknowledgedCount());
        assertEquals(1, s2.getAcknowledgedCount());
        assertTrue(s2.getUnacknowledgedActive());
        assertTrue(s2.getAcknowledgedActive());
        assertFalse(s2.getShelvedActive());

    }

    @Test
    public void testAlarmPersistence() throws Exception {
        createWarningEvent();
        packetGenerator.setGenerationTime(TimeEncoding.getWallclockTime());
        packetGenerator.generate_PKT1_10(0, 3, 51);

        var alarms0 = yamcsClient.listAlarms(yamcsInstance, "realtime").get().getAlarmsList();
        assertEquals(3, alarms0.size());
        yamcsClient.restartInstance(yamcsInstance).get();

        var alarms1 = yamcsClient.listAlarms(yamcsInstance, "realtime").get().getAlarmsList();
        assertEquals(3, alarms1.size());
        alarms0 = sortAlarms(alarms0);
        alarms1 = sortAlarms(alarms1);

        for (int i = 0; i < alarms0.size(); i++) {
            var a0 = alarms0.get(i);
            var a1 = alarms1.get(i);
            assertEquals(a0.getId(), a1.getId());
            assertEquals(a0.getSeverity(), a1.getSeverity());
            assertEquals(a0.getAcknowledged(), a1.getAcknowledged());
            assertEquals(a0.getTriggerTime(), a1.getTriggerTime());

            clearAlarm(a0.getId().getNamespace() + "/" + a1.getId().getName(), a0.getSeqNum(), "");
        }
    }

    private List<AlarmData> sortAlarms(List<AlarmData> alarmList) {
        var l = new ArrayList<>(alarmList);
        l.sort((a, b) -> {
            return a.getId().getName().compareTo(b.getId().getName());
        });
        return l;
    }

    AlarmSubscription subscribeAlarms() throws Exception {
        AlarmSubscription subscription = yamcsClient.createAlarmSubscription();

        SubscribeAlarmsRequest request = SubscribeAlarmsRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .build();
        subscription.sendMessage(request);
        subscription.awaitConfirmation();

        return subscription;
    }

    Event createWarningEvent() throws Exception {
        CreateEventRequest createRequest = CreateEventRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setSeverity("warning")
                .setSource("IntegrationTest")
                .setType("Event-Alarm-Test")
                .setMessage("event1")
                .build();
        return yamcsClient.createEvent(createRequest).get();
    }

    void clearAlarm(String name, int seq, String comment) throws Exception {
        EditAlarmRequest ear = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(name)
                .setSeqnum(seq)
                .setState("cleared")
                .setComment(comment)
                .setShelveDuration(500).build();
        yamcsClient.editAlarm(ear).get();
    }

    void acknowledgeAlarm(String name, int seq, String comment) throws Exception {
        var earb = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(name)
                .setSeqnum(seq)
                .setState("acknowledged")
                .setComment(comment);

        yamcsClient.editAlarm(earb.build()).get();
    }

    void shelveAlarm(String name, int seq, long duration, String comment) throws Exception {
        var earb = EditAlarmRequest.newBuilder()
                .setInstance(yamcsInstance)
                .setProcessor("realtime")
                .setName(name)
                .setSeqnum(seq)
                .setState("shelved")
                .setComment(comment);

        if (duration > 0) {
            earb.setShelveDuration(duration);
        }
        yamcsClient.editAlarm(earb.build()).get();
    }
}
