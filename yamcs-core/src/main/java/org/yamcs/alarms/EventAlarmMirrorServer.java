package org.yamcs.alarms;

import static org.yamcs.alarms.EventAlarmStreamer.CNAME_TRIGGER;

import java.util.Map;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.archive.AlarmRecorder;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db.Event;

class EventAlarmMirrorServer extends AbstractAlarmMirrorServer<EventId, Event> {
    public EventAlarmMirrorServer(String yamcsInstance, double alarmLoadDays) {
        super(yamcsInstance, alarmLoadDays);
    }

    @Override
    EventId getSubject(Tuple tuple) {
        String source = tuple.getColumn(StandardTupleDefinitions.EVENT_SOURCE_COLUMN);
        String type = tuple.getColumn(StandardTupleDefinitions.EVENT_TYPE_COLUMN);
        return new EventId(source, type);
    }

    @Override
    protected ActiveAlarm<Event> createNewAlarm(EventId eventId, Tuple tuple) {
        var o = tuple.getColumn(CNAME_TRIGGER);
        if (o == null || !(o instanceof Event)) {
            return null;
        }

        return EventAlarmServer.tupleToActiveAlarm((Event) o, tuple);
    }

    @Override
    protected String getColNameLastEvent() {
        return EventAlarmStreamer.CNAME_LAST_EVENT;
    }

    @Override
    protected String getColNameSeverityIncreased() {
        return EventAlarmStreamer.CNAME_SEVERITY_INCREASED;
    }

    @Override
    protected String alarmTableName() {
        return AlarmRecorder.EVENT_ALARM_TABLE_NAME;
    }

    @Override
    protected EventId getSubject(Event ev) {
        return new EventId(ev.getSource(), ev.hasType() ? ev.getType() : null);
    }

    @Override
    protected void addActiveAlarmFromTuple(Mdb mdb, Tuple tuple, Map<EventId, ActiveAlarm<Event>> alarms) {
        var o = tuple.getColumn(CNAME_TRIGGER);
        if (o == null || !(o instanceof Event)) {
            log.info("Not adding alarm from tuple because could not extract the triggered Event: {}", tuple);
            return;
        }
        var triggerValue = (Event) o;
        var activeAlarm = EventAlarmServer.tupleToActiveAlarm(triggerValue, tuple);
        alarms.put(getSubject(triggerValue), activeAlarm);
    }
}
