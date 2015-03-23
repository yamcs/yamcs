package org.yamcs;

import static org.junit.Assert.*;

import java.util.LinkedList;
import java.util.Queue;

import org.junit.Test;
import org.yamcs.AlarmServer.ActiveAlarm;
import org.yamcs.AlarmServer.AlarmListener;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.xtce.Parameter;

public class AlarmServerTest {
    Parameter p1 = new Parameter("p1");
    Parameter p2 = new Parameter("p2");
    ParameterValue getParameterValue(Parameter p, MonitoringResult mr) {
	ParameterValue pv = new ParameterValue(p, false);
	pv.monitoringResult = mr;
	
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
	assertEquals(pv1_0, aa.msValue);
	assertEquals(pv1_0, aa.triggerValue);
	
	ParameterValue pv1_1 = getParameterValue(p1, MonitoringResult.WARNING);
	as.update(pv1_1, 1);
	
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
