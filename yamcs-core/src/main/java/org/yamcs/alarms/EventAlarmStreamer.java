package org.yamcs.alarms;

import java.util.ArrayList;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.Event;

/**
 * Receives event alarms from the {@link AlarmServer} and sends them to the events_alarms stream to be recorded
 * 
 */
public class EventAlarmStreamer extends AlarmStreamer<Event> {

    public static final DataType EVENT_DATA_TYPE = DataType
            .protobuf(Event.class.getName());
    public static final String CNAME_LAST_EVENT = "event";
    public static final String CNAME_TRIGGER = "triggerEvent";
    public static final String CNAME_CLEAR = "clearEvent";
    public static final String CNAME_SEVERITY_INCREASED = "severityIncreasedEvent";

    public EventAlarmStreamer(Stream s) {
        super(s, EVENT_DATA_TYPE, StandardTupleDefinitions.EVENT_ALARM);
    }

    @Override
    protected ArrayList<Object> getTupleKey(AlarmNotificationType notificationType, ActiveAlarm<Event> activeAlarm) {
        ArrayList<Object> al = new ArrayList<>(7);
        Event triggerValue = activeAlarm.getTriggerValue();

        // triggerTime
        al.add(triggerValue.getGenerationTime());
        // event source
        al.add(triggerValue.getSource());
        // seqNum
        al.add(activeAlarm.getId());
        // event
        al.add(notificationType.toString());

        // event type
        al.add(triggerValue.getType());

        return al;
    }

    /**
     * generate a tuple with the violation/value counters and the last value to be saved in the database before shutdown
     */
    @Override
    public void notifyShutdown(ActiveAlarm<Event> alarm) {
        Tuple t = new Tuple();

        // primary key
        t.addTimestampColumn(AlarmStreamer.CNAME_TRIGGER_TIME, alarm.getTriggerValue().getGenerationTime());
        t.addColumn(StandardTupleDefinitions.EVENT_SOURCE_COLUMN,
                alarm.getTriggerValue().getSource());
        t.addColumn(StandardTupleDefinitions.SEQNUM_COLUMN, alarm.getId());

        // values we are interested in
        t.addColumn(CNAME_VIOLATION_COUNT, alarm.getViolations());
        t.addColumn(CNAME_VALUE_COUNT, alarm.getValueCount());
        t.addColumn(CNAME_LAST_VALUE, EVENT_DATA_TYPE, alarm.getCurrentValue());

        stream.emitTuple(t);
    }

    @Override
    protected String getColNameLastEvent() {
        return CNAME_LAST_EVENT;
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

    @Override
    protected long getUpdateTime(Event alarmDetail) {
        return alarmDetail.getGenerationTime();
    }
}
