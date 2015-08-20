package org.yamcs.alarms;

import java.util.ArrayList;

import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.tctm.PpProviderAdapter;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public class AlarmStreamer implements AlarmListener {
    enum AlarmEvent {
        TRIGGERED, UPDATED, SEVERITY_INCREASED, CLEARED;
    }
    final DataType PP_DATA_TYPE=DataType.protobuf(ParameterValue.class.getName());
    
    Stream stream;
    public AlarmStreamer(Stream s) {
        this.stream = s;
    }
    
    private ArrayList<Object> getTupleKey(ActiveAlarm activeAlarm, AlarmEvent e) {
        ArrayList<Object> al=new ArrayList<Object>(7);
    
        //triggerTime
        al.add(activeAlarm.triggerValue.getGenerationTime());
        //parameter
        al.add(activeAlarm.triggerValue.getParameter().getQualifiedName());
        //seqNum
        al.add(activeAlarm.id);
        //event
        al.add(e.name());
        
        return al;
    }
    
    @Override
    public void notifyTriggered(ActiveAlarm activeAlarm) {
        TupleDefinition tdef = AlarmServer.ALARM_TUPLE_DEFINITION.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.TRIGGERED);
        
        tdef.addColumn("triggerPV", PpProviderAdapter.PP_DATA_TYPE);
        al.add(activeAlarm.triggerValue);
        
        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }
    
    @Override
    public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
        TupleDefinition tdef = AlarmServer.ALARM_TUPLE_DEFINITION.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.SEVERITY_INCREASED);
        
        tdef.addColumn("severityIncreasedPV", PpProviderAdapter.PP_DATA_TYPE);
        al.add(activeAlarm.mostSevereValue);
    
        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }
    
    @Override
    public void notifyUpdate(ActiveAlarm activeAlarm) {   
        TupleDefinition tdef = AlarmServer.ALARM_TUPLE_DEFINITION.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.UPDATED);
        
        tdef.addColumn("updatedPV", PpProviderAdapter.PP_DATA_TYPE);
        al.add(activeAlarm.currentValue);
    
        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }
    
    @Override
    public void notifyCleared(ActiveAlarm activeAlarm) {
        TupleDefinition tdef = AlarmServer.ALARM_TUPLE_DEFINITION.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmEvent.CLEARED);
        
        tdef.addColumn("clearedPV", PpProviderAdapter.PP_DATA_TYPE);
        al.add(activeAlarm.currentValue);
        
        String username = activeAlarm.usernameThatAcknowledged;
        
        if(username==null) {
            if(activeAlarm.autoAcknowledge) {
                username = "autoAcknowledged";
            } else {
                username = "unknown";
            }
        }
        tdef.addColumn("username", DataType.STRING);
        al.add(username);
        
        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }
}
