package org.yamcs.alarms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ProcessorConfig;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

public class AlarmServerTest {
    Parameter p1 = new Parameter("p1");
    Parameter p2 = new Parameter("p2");
    AlarmServer<Parameter, ParameterValue> alarmServer;
    ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);
    ProcessorConfig procConfig = new ProcessorConfig();

    @BeforeAll
    static public void setupBeforeClass() {
        EventProducerFactory.setMockup(true);
        TimeEncoding.setUp();
    }

    ParameterValue getParameterValue(Parameter p, MonitoringResult mr) {
        ParameterValue pv = new ParameterValue(p);
        pv.setMonitoringResult(mr);

        return pv;
    }

    @BeforeEach
    public void before() {
        procConfig.setAlarmLoadDays(-1);
        alarmServer = new ParameterAlarmServer("toto", procConfig, timer);
    }

    @Test
    public void test1() {
        MyListener l = new MyListener();
        alarmServer.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        alarmServer.update(pv1_0, 1);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        ParameterValue pv1_1 = getParameterValue(p1, MonitoringResult.WARNING);
        alarmServer.update(pv1_1, 1);
        assertTrue(l.triggered.isEmpty());
        aa = l.valueUpdates.remove();
        assertEquals(pv1_1, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        ParameterValue pv1_2 = getParameterValue(p1, MonitoringResult.CRITICAL);
        alarmServer.update(pv1_2, 1);
        assertTrue(l.triggered.isEmpty());
        assertTrue(l.valueUpdates.isEmpty());
        aa = l.severityIncreased.remove();
        assertEquals(pv1_2, aa.getCurrentValue());
        assertEquals(pv1_2, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        long ackTime = 123L;
        alarmServer.acknowledge(aa, "test1", ackTime, "bla");
        assertTrue(l.cleared.isEmpty());

        assertEquals(1, l.acknowledged.size());
        assertEquals(aa, l.acknowledged.remove());

        ParameterValue pv1_3 = getParameterValue(p1, MonitoringResult.IN_LIMITS);
        alarmServer.update(pv1_3, 1);
        aa = l.cleared.remove();
        assertEquals(pv1_3, aa.getCurrentValue());
        assertEquals(pv1_2, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());
        assertEquals("test1", aa.getUsernameThatAcknowledged());
        assertEquals(ackTime, aa.getAcknowledgeTime());
        assertEquals("bla", aa.getAckMessage());
    }

    @Test
    public void test2() {
        MyListener l = new MyListener();
        alarmServer.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        alarmServer.update(pv1_0, 1);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        assertTrue(aa.toString().length() > 0);

        ParameterValue pv1_1 = getParameterValue(p1, MonitoringResult.IN_LIMITS);
        alarmServer.update(pv1_1, 1);
        assertTrue(l.cleared.isEmpty());
        aa = l.valueUpdates.remove();
        assertEquals(pv1_1, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        assertEquals(1, l.rtn.size());

        long ackTime = 123L;
        alarmServer.acknowledge(aa, "test2", ackTime, "bla");

        aa = l.cleared.remove();
        assertEquals(pv1_1, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());
        assertEquals("test2", aa.getUsernameThatAcknowledged());
        assertEquals(ackTime, aa.getAcknowledgeTime());
        assertEquals("bla", aa.getAckMessage());
    }

    @Test
    public void testShelve() throws InterruptedException {
        MyListener l = new MyListener();
        alarmServer.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        alarmServer.update(pv1_0, 1);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        long shelvetime = TimeEncoding.getWallclockTime();

        ActiveAlarm<ParameterValue> aa1 = alarmServer.shelve(aa, "busy operator", "looking at it later", 500);
        assertNotNull(aa1);

        assertEquals(1, l.shelved.size());
        assertEquals(aa, l.shelved.remove());

        ActiveAlarm<ParameterValue> aa2 = l.unshelved.poll(2000, TimeUnit.MILLISECONDS);
        if (aa2 == null) { // this code is in order to try to understand the spurious test failure - aa2 is null
                           // sometimes
            System.out.println("shelvetime:" + shelvetime + " wallclocktime: " + TimeEncoding.getWallclockTime());
            System.out.println("Active alarms: " + alarmServer.getActiveAlarms());
        }
        assertEquals(aa, aa2);
        assertFalse(aa.isShelved());
    }

    @Test
    public void testAutoAck() {
        MyListener l = new MyListener();
        alarmServer.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        alarmServer.update(pv1_0, 1, true, false);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        ParameterValue pv1_1 = getParameterValue(p1, MonitoringResult.IN_LIMITS);
        alarmServer.update(pv1_1, 1, true, false);

        aa = l.cleared.remove();
        assertEquals(pv1_1, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());
    }

    @Test
    public void testGetActiveAlarmWithNoAlarm() throws AlarmSequenceException {

        MyListener l = new MyListener();
        alarmServer.addAlarmListener(l);

        assertNull(alarmServer.getActiveAlarm(p1, 1));
    }

    @Test
    public void testGetActiveAlarmWithInvalidId() {
        MyListener l = new MyListener();
        alarmServer.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        alarmServer.update(pv1_0, 1, true, false);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.getCurrentValue());
        assertEquals(pv1_0, aa.getMostSevereValue());
        assertEquals(pv1_0, aa.getTriggerValue());

        assertThrows(AlarmSequenceException.class, () -> {
            alarmServer.getActiveAlarm(p1, 123 /* wrong id */);
        });

    }

    @Test
    public void testMoreSevere() {
        assertTrue(AlarmServer.moreSevere(MonitoringResult.CRITICAL, MonitoringResult.WARNING));
        assertFalse(AlarmServer.moreSevere(MonitoringResult.WARNING, MonitoringResult.CRITICAL));
        assertFalse(AlarmServer.moreSevere(MonitoringResult.CRITICAL, MonitoringResult.CRITICAL));
    }

    class MyListener implements AlarmListener<ParameterValue> {
        Queue<ActiveAlarm<ParameterValue>> valueUpdates = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> severityIncreased = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> triggered = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> acknowledged = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> cleared = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> rtn = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> shelved = new LinkedList<>();
        BlockingQueue<ActiveAlarm<ParameterValue>> unshelved = new LinkedBlockingQueue<>();
        Queue<ActiveAlarm<ParameterValue>> reset = new LinkedList<>();

        @Override
        public void notifyValueUpdate(ActiveAlarm<ParameterValue> activeAlarm) {
            valueUpdates.add(activeAlarm);
        }

        @Override
        public void notifySeverityIncrease(ActiveAlarm<ParameterValue> activeAlarm) {
            severityIncreased.add(activeAlarm);
        }

        @Override
        public void notifyUpdate(AlarmNotificationType notificationType, ActiveAlarm<ParameterValue> activeAlarm) {
            switch (notificationType) {
            case TRIGGERED:
                triggered.add(activeAlarm);
                break;
            case ACKNOWLEDGED:
                acknowledged.add(activeAlarm);
                break;
            case CLEARED:
                cleared.add(activeAlarm);
                break;
            case RTN:
                rtn.add(activeAlarm);
                break;
            case RESET:
                rtn.add(activeAlarm);
                break;
            case SHELVED:
                shelved.add(activeAlarm);
                break;
            case UNSHELVED:
                unshelved.add(activeAlarm);
                break;
            default:
                throw new IllegalStateException();
            }
        }
    }
}
