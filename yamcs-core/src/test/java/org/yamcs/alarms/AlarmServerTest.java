package org.yamcs.alarms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.xtce.Parameter;

public class AlarmServerTest {
    Parameter p1 = new Parameter("p1");
    Parameter p2 = new Parameter("p2");

    @BeforeClass
    static public void setupBeforeClass() {
        EventProducerFactory.setMockup(true);
    }

    ParameterValue getParameterValue(Parameter p, MonitoringResult mr) {
        ParameterValue pv = new ParameterValue(p);
        pv.setMonitoringResult(mr);

        return pv;
    }

    @Test
    public void test1() throws CouldNotAcknowledgeAlarmException {
        AlarmServer<Parameter, ParameterValue> as = new AlarmServer<>("toto");
        MyListener l = new MyListener();
        as.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);

        ParameterValue pv1_1 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_1, 1);
        assertTrue(l.triggered.isEmpty());
        aa = l.updated.remove();
        assertEquals(pv1_1, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);

        ParameterValue pv1_2 = getParameterValue(p1, MonitoringResult.CRITICAL);
        as.update(pv1_2, 1);
        assertTrue(l.triggered.isEmpty());
        assertFalse(l.updated.isEmpty());
        aa = l.severityIncreased.remove();
        assertEquals(pv1_2, aa.currentValue);
        assertEquals(pv1_2, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);

        long ackTime = 123L;
        as.acknowledge(aa, "test1", ackTime, "bla");
        assertTrue(l.cleared.isEmpty());

        assertEquals(1, l.acknowledged.size());
        assertEquals(aa, l.acknowledged.remove());

        ParameterValue pv1_3 = getParameterValue(p1, MonitoringResult.IN_LIMITS);
        as.update(pv1_3, 1);
        aa = l.cleared.remove();
        assertEquals(pv1_3, aa.currentValue);
        assertEquals(pv1_2, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);
        assertEquals("test1", aa.usernameThatAcknowledged);
        assertEquals(ackTime, aa.acknowledgeTime);
        assertEquals("bla", aa.message);
    }

    @Test
    public void test2() throws CouldNotAcknowledgeAlarmException {
        AlarmServer<Parameter, ParameterValue> as = new AlarmServer<>("toto");
        MyListener l = new MyListener();
        as.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);

        ParameterValue pv1_1 = getParameterValue(p1, MonitoringResult.IN_LIMITS);
        as.update(pv1_1, 1);
        assertTrue(l.cleared.isEmpty());
        aa = l.updated.remove();
        assertEquals(pv1_1, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);

        long ackTime = 123L;
        as.acknowledge(aa, "test2", ackTime, "bla");

        assertEquals(1, l.acknowledged.size());
        assertEquals(aa, l.acknowledged.remove());

        aa = l.cleared.remove();
        assertEquals(pv1_1, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);
        assertEquals("test2", aa.usernameThatAcknowledged);
        assertEquals(ackTime, aa.acknowledgeTime);
        assertEquals("bla", aa.message);
    }

    @Test
    public void testAutoAck() {
        AlarmServer<Parameter, ParameterValue> as = new AlarmServer<>("toto");
        MyListener l = new MyListener();
        as.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1, true);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);

        ParameterValue pv1_1 = getParameterValue(p1, MonitoringResult.IN_LIMITS);
        as.update(pv1_1, 1, true);

        aa = l.cleared.remove();
        assertEquals(pv1_1, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);
    }

    @Test
    public void testGetActiveAlarmWithNoAlarm() throws AlarmSequenceException {
        AlarmServer<Parameter, ParameterValue> as = new AlarmServer<>("toto");
        MyListener l = new MyListener();
        as.addAlarmListener(l);

        assertNull(as.getActiveAlarm(p1, 1));
    }

    @Test(expected = AlarmSequenceException.class)
    public void testGetActiveAlarmWithInvalidId() throws AlarmSequenceException {
        AlarmServer<Parameter, ParameterValue> as = new AlarmServer<>("toto");
        MyListener l = new MyListener();
        as.addAlarmListener(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1, true);

        ActiveAlarm<ParameterValue> aa = l.triggered.remove();
        assertEquals(pv1_0, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);

        as.getActiveAlarm(p1, 123 /* wrong id */);
    }

    @Test
    public void testMoreSevere() {
        assertTrue(AlarmServer.moreSevere(MonitoringResult.CRITICAL, MonitoringResult.WARNING));
        assertFalse(AlarmServer.moreSevere(MonitoringResult.WARNING, MonitoringResult.CRITICAL));
        assertFalse(AlarmServer.moreSevere(MonitoringResult.CRITICAL, MonitoringResult.CRITICAL));
    }

    class MyListener implements AlarmListener<ParameterValue> {
        Queue<ActiveAlarm<ParameterValue>> triggered = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> updated = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> severityIncreased = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> acknowledged = new LinkedList<>();
        Queue<ActiveAlarm<ParameterValue>> cleared = new LinkedList<>();

        @Override
        public void notifyTriggered(ActiveAlarm<ParameterValue> activeAlarm) {
            triggered.add(activeAlarm);
        }

        @Override
        public void notifyValueUpdate(ActiveAlarm<ParameterValue> activeAlarm) {
            updated.add(activeAlarm);
        }

        @Override
        public void notifySeverityIncrease(ActiveAlarm<ParameterValue> activeAlarm) {
            severityIncreased.add(activeAlarm);
        }

        @Override
        public void notifyAcknowledged(ActiveAlarm<ParameterValue> activeAlarm) {
            acknowledged.add(activeAlarm);
        }

        @Override
        public void notifyCleared(ActiveAlarm<ParameterValue> activeAlarm) {
            cleared.add(activeAlarm);
        }
    }
}
