package org.yamcs.alarms;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.archive.AlarmRecorder;
import org.yamcs.archive.EventRecorder;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import static org.yamcs.alarms.AlarmStreamer.CNAME_ACK_BY;
import static org.yamcs.alarms.AlarmStreamer.CNAME_ACK_MSG;
import static org.yamcs.alarms.AlarmStreamer.CNAME_ACK_TIME;
import static org.yamcs.alarms.AlarmStreamer.CNAME_SEQ_NUM;
import static org.yamcs.alarms.AlarmStreamer.CNAME_SHELVED_BY;
import static org.yamcs.alarms.AlarmStreamer.CNAME_SHELVED_MSG;
import static org.yamcs.alarms.AlarmStreamer.CNAME_SHELVED_TIME;
import static org.yamcs.alarms.AlarmStreamer.CNAME_VIOLATION_COUNT;
import static org.yamcs.alarms.EventAlarmStreamer.*;

/**
 * Handles alarms for events. These are generated whenever an event with a severity level different than INFO is
 * received.
 * <p>
 * The events having the same (source, type) are considered to be part of the same alarm.
 *
 */
// In the future we could implement some additional options like configuring events that never throw alarms or adding
// the possibility to configure the minViolations for each event id (= event source+ type).
public class EventAlarmServer extends AlarmServer<EventId, Event> {
    static private final Logger log = LoggerFactory.getLogger(EventAlarmServer.class);
    private StreamSubscriber eventStreamSubscriber;
    private int eventAlarmMinViolations;
    Stream eventStream;
    static final String EVENT_ALARMS_REALTIME_STREAM = "event_alarms_realtime";

    public EventAlarmServer(String yamcsInstance, ProcessorConfig procConfig, ScheduledThreadPoolExecutor timer) {
        super(yamcsInstance, procConfig, timer);
        eventAlarmMinViolations = procConfig.getEventAlarmMinViolations();
    }

    @Override
    public void doStart() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);

        Stream s = ydb.getStream(EVENT_ALARMS_REALTIME_STREAM);
        if (s == null) {
            notifyFailed(
                    new ConfigurationException("Cannot find a stream named '" + EVENT_ALARMS_REALTIME_STREAM + "'"));
            return;
        }
        addAlarmListener(new EventAlarmStreamer(s));

        eventStream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        eventStreamSubscriber = new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                Event event = (Event) tuple.getColumn("body");
                update(event, eventAlarmMinViolations);
            }

            @Override
            public void streamClosed(Stream stream) {
                notifyFailed(new Exception("Stream " + stream.getName() + " closed"));
            }
        };
        eventStream.addSubscriber(eventStreamSubscriber);

        notifyStarted();
    }

    @Override
    public void doStop() {
        eventStream.removeSubscriber(eventStreamSubscriber);
        notifyStopped();
    }

    protected String alarmTableName() {
        return AlarmRecorder.EVENT_ALARM_TABLE_NAME;
    }

    @Override
    protected EventId getSubject(Event ev) {
        return new EventId(ev.getSource(), ev.hasType() ? ev.getType() : null);
    }

    @Override
    protected void addActiveAlarmFromTuple(Mdb mdb, Tuple tuple) {
        var o = tuple.getColumn(CNAME_TRIGGER);
        if (o == null || !(o instanceof Event)) {
            log.info("Not adding alarm from tuple because could not extract the triggered Event: {}", tuple);
            return;
        }
        var triggeredValue = (Event) o;
        int seqNum = tuple.getIntColumn(CNAME_SEQ_NUM);

        var activeAlarm = new ActiveAlarm<Event>(triggeredValue, false, false, seqNum);
        activeAlarm.trigger();

        activeAlarm.setViolations(tuple.getIntColumn(CNAME_VIOLATION_COUNT));
        if (tuple.hasColumn(CNAME_ACK_TIME)) {
            long t = tuple.getTimestampColumn(CNAME_ACK_TIME);
            activeAlarm.acknowledge(tuple.getColumn(CNAME_ACK_BY), t, tuple.getColumn(CNAME_ACK_MSG));
        }

        if (tuple.hasColumn(CNAME_SHELVED_TIME)) {
            long t = tuple.getTimestampColumn(CNAME_SHELVED_TIME);
            activeAlarm.shelve(t, tuple.getColumn(CNAME_SHELVED_BY), tuple.getColumn(CNAME_SHELVED_MSG), t);
        }

        o = tuple.getColumn(CNAME_SEVERITY_INCREASED);
        if (o != null && !(o instanceof Event)) {
            activeAlarm.setMostSevereValue((Event) o);
        }

        activeAlarms.put(getSubject(triggeredValue), activeAlarm);

    }

    @Override
    protected String getColNameLastEvent() {
        return CNAME_LAST_EVENT;
    }
}
