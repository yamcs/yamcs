package org.yamcs.alarms;

import java.util.ArrayList;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;

/**
 * Receives event alarms from the {@link AlarmServer} and sends them to the events_alarms stream to be recorded
 * 
 * @author nm
 *
 */
public class EventAlarmStreamer extends AlarmStreamer<Event> {

    static public final DataType EVENT_DATA_TYPE = DataType
            .protobuf(Event.class.getName());
    static public final String CNAME_TRIGGER = "triggerEvent";
    static public final String CNAME_CLEAR = "clearEvent";
    static public final String CNAME_SEVERITY_INCREASED = "severityIncreasedEvent";
    
    public EventAlarmStreamer(Stream s) {
       super(s, EVENT_DATA_TYPE, StandardTupleDefinitions.EVENT_ALARM);
    }

    protected ArrayList<Object> getTupleKey(ActiveAlarm<Event> activeAlarm, AlarmNotificationType e) {
        ArrayList<Object> al = new ArrayList<>(7);
        Event triggerValue = activeAlarm.triggerValue;

        // triggerTime
        al.add(triggerValue.getGenerationTime());
        // event source
        al.add(triggerValue.getSource());
        // seqNum
        al.add(activeAlarm.getId());
        // event
        al.add(e.name());

        return al;
    }

    @Override
    protected Object getYarchValue(Event currentValue) {
        return currentValue;
    }

    @Override
    protected String getColNameClear() {
        return CNAME_CLEAR;
    }

    @Override
    protected String getColNameTrigger() {
        return CNAME_TRIGGER;
    }

    @Override
    protected String getColNameSeverityIncreased() {
        return CNAME_SEVERITY_INCREASED;
    }

}
