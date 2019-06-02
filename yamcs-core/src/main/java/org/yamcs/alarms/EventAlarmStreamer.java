package org.yamcs.alarms;

import java.util.ArrayList;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

/**
 * Receives event alarms from the {@link AlarmServer} and sends them to the events_alarms stream to be recorded
 * 
 * @author nm
 *
 */
public class EventAlarmStreamer implements AlarmListener<Event> {

    static public final DataType EVENT_DATA_TYPE = DataType
            .protobuf(Event.class.getName());

    Stream stream;

    public EventAlarmStreamer(Stream s) {
        this.stream = s;
    }

    private ArrayList<Object> getTupleKey(ActiveAlarm<Event> activeAlarm, AlarmNotificationType e) {
        ArrayList<Object> al = new ArrayList<>(7);
        Event triggerValue = activeAlarm.triggerValue;

        // triggerTime
        al.add(triggerValue.getGenerationTime());
        // event source 
        al.add(triggerValue.getSource());
        // seqNum
        al.add(activeAlarm.id);
        // event
        al.add(e.name());

        return al;
    }

    @Override
    public void notifyTriggered(ActiveAlarm<Event> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.EVENT_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.TRIGGERED);

        tdef.addColumn("triggerEvent", EVENT_DATA_TYPE);
        al.add(activeAlarm.triggerValue);

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifySeverityIncrease(ActiveAlarm<Event> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.EVENT_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.SEVERITY_INCREASED);

        tdef.addColumn("severityIncreasedEvent", EVENT_DATA_TYPE);
        al.add(activeAlarm.mostSevereValue);

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifyValueUpdate(ActiveAlarm<Event> activeAlarm) {
        // do not send parameter updates
    }

    @Override
    public void notifyAcknowledged(ActiveAlarm<Event> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.EVENT_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.ACKNOWLEDGED);

        tdef.addColumn("acknowledgedBy", DataType.STRING);
        String username = activeAlarm.usernameThatAcknowledged;
        if (activeAlarm.autoAcknowledge) {
            username = "autoAcknowledged";
        }
        al.add(username);

        if (activeAlarm.message != null) {
            tdef.addColumn("acknowledgeMessage", DataType.STRING);
            al.add(activeAlarm.message);
        }

        tdef.addColumn("acknowledgeTime", DataType.TIMESTAMP);
        al.add(activeAlarm.acknowledgeTime);

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifyCleared(ActiveAlarm<Event> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.EVENT_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.CLEARED);

        tdef.addColumn("clearedEvent", EVENT_DATA_TYPE);
        al.add(activeAlarm.currentValue);

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }
}
