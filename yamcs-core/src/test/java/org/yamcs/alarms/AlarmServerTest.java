package org.yamcs.alarms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.api.EventProducerFactory;
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
    public void test1 () throws CouldNotAcknowledgeAlarmException {
        AlarmServer as = new AlarmServer("toto");
        MyListener l = new MyListener();
        as.subscribe(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1);
        
        ActiveAlarm aa = l.triggered.remove();
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
        as.acknowledge(p1, aa.id, "test1", ackTime, "bla");
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
    public void test2 () throws CouldNotAcknowledgeAlarmException {
        AlarmServer as = new AlarmServer("toto");
        MyListener l = new MyListener();
        as.subscribe(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1);
        
        ActiveAlarm aa = l.triggered.remove();
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
        as.acknowledge(p1, aa.id, "test2", ackTime, "bla");
        
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
    public void testAutoAck () {
        AlarmServer as = new AlarmServer("toto");
        MyListener l = new MyListener();
        as.subscribe(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1, true);
        
        ActiveAlarm aa = l.triggered.remove();
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
    
    @Test(expected = CouldNotAcknowledgeAlarmException.class)
    public void testAcknowledgeButNoAlarm() throws CouldNotAcknowledgeAlarmException {
        AlarmServer as = new AlarmServer("toto");
        MyListener l = new MyListener();
        as.subscribe(l);
        
        long ackTime = 123L;
        as.acknowledge(p1, 1, "a-user", ackTime, "bla");
    }
    
    @Test(expected = CouldNotAcknowledgeAlarmException.class)
    public void testAcknowledgeButNoParameterMatch() throws CouldNotAcknowledgeAlarmException {
        AlarmServer as = new AlarmServer("toto");
        MyListener l = new MyListener();
        as.subscribe(l);
        ParameterValue pv1_0 = getParameterValue(p1, MonitoringResult.WARNING);
        as.update(pv1_0, 1, true);
        
        ActiveAlarm aa = l.triggered.remove();
        assertEquals(pv1_0, aa.currentValue);
        assertEquals(pv1_0, aa.mostSevereValue);
        assertEquals(pv1_0, aa.triggerValue);
        
        long ackTime = 123L;
        as.acknowledge(p2 /* not p1 */, aa.id, "a-user", ackTime, "bla");
    }
    
    @Test
    public void testMoreSevere() {
        assertTrue(AlarmServer.moreSevere(MonitoringResult.CRITICAL, MonitoringResult.WARNING));
        assertFalse(AlarmServer.moreSevere(MonitoringResult.WARNING, MonitoringResult.CRITICAL));
        assertFalse(AlarmServer.moreSevere(MonitoringResult.CRITICAL, MonitoringResult.CRITICAL));
    }
    
    class MyListener implements AlarmListener {
        Queue<ActiveAlarm> triggered = new LinkedList<>();
        Queue<ActiveAlarm> updated = new LinkedList<>();
        Queue<ActiveAlarm> severityIncreased = new LinkedList<>();
        Queue<ActiveAlarm> acknowledged = new LinkedList<>();
        Queue<ActiveAlarm> cleared = new LinkedList<>();
        
        @Override
        public void notifyTriggered(ActiveAlarm activeAlarm) {
            triggered.add(activeAlarm);
        }
        
        @Override
        public void notifyParameterValueUpdate(ActiveAlarm activeAlarm) {
            updated.add(activeAlarm);
        }
        
        @Override
        public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
            severityIncreased.add(activeAlarm);
        }
        
        @Override
        public void notifyAcknowledged(ActiveAlarm activeAlarm) {
            acknowledged.add(activeAlarm);
        }
        
        @Override
        public void notifyCleared(ActiveAlarm activeAlarm) {
            cleared.add(activeAlarm);
        }
    }
}
