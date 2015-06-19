package org.yamcs;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yamcs.AlarmServer.ActiveAlarm;
import org.yamcs.AlarmServer.AlarmListener;
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
    public void test1 () {
	AlarmServer as = new AlarmServer("toto");
	MyListener l = new MyListener();
	as.setListener(l);
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
	assertTrue(l.updated.isEmpty());
	aa = l.severityIncreased.remove();
	assertEquals(pv1_2, aa.currentValue);
	assertEquals(pv1_2, aa.mostSevereValue);
	assertEquals(pv1_0, aa.triggerValue);
	
	as.acknowledge(p1, aa.id, "test1");
	assertTrue(l.cleared.isEmpty());
	
	ParameterValue pv1_3 = getParameterValue(p1, MonitoringResult.IN_LIMITS);
	as.update(pv1_3, 1);
	aa = l.cleared.remove();
	assertEquals(pv1_3, aa.currentValue);
	assertEquals(pv1_2, aa.mostSevereValue);
	assertEquals(pv1_0, aa.triggerValue);
	assertEquals("test1", aa.usernameThatAcknowledged);
    }
    
    @Test
    public void test2 () {
	AlarmServer as = new AlarmServer("toto");
	MyListener l = new MyListener();
	as.setListener(l);
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
	
	as.acknowledge(p1, aa.id, "test2");
	
	aa = l.cleared.remove();
	assertEquals(pv1_1, aa.currentValue);
	assertEquals(pv1_0, aa.mostSevereValue);
	assertEquals(pv1_0, aa.triggerValue);
	assertEquals("test2", aa.usernameThatAcknowledged);
    }    

    @Test
    public void testAutoAck () {
	AlarmServer as = new AlarmServer("toto");
	MyListener l = new MyListener();
	as.setListener(l);
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

    
    
    class MyListener implements AlarmListener {
	Queue<ActiveAlarm> triggered = new LinkedList<AlarmServer.ActiveAlarm>();
	Queue<ActiveAlarm> updated = new LinkedList<AlarmServer.ActiveAlarm>();
	Queue<ActiveAlarm> severityIncreased = new LinkedList<AlarmServer.ActiveAlarm>();
	Queue<ActiveAlarm> cleared = new LinkedList<AlarmServer.ActiveAlarm>();
	
	@Override
	public void notifyTriggered(ActiveAlarm activeAlarm) {
	    triggered.add(activeAlarm);
	}
	

	@Override
	public void notifyUpdate(ActiveAlarm activeAlarm) {
	    updated.add(activeAlarm);
	}
	
	@Override
	public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
	    severityIncreased.add(activeAlarm);
	}

	@Override
	public void notifyCleared(ActiveAlarm activeAlarm) {
	    cleared.add(activeAlarm);
	}
    }
}
