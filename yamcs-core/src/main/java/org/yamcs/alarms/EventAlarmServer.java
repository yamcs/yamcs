package org.yamcs.alarms;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.yamcs.ConfigurationException;
import org.yamcs.ProcessorConfig;
import org.yamcs.archive.EventRecorder;
import org.yamcs.yarch.protobuf.Db.Event;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

/**
 * Handles alarms for events. These are generated whenever an event with a severity level different than INFO is
 * received.
 * <p>
 * The events having the same (source, type) are considered to be part of the same alarm.
 * 
 * <p>
 * In the future we could implement some additional options like configuring events that never throw alarms or adding
 * the possibility to configure the minViolations for each event id (= event source+ type).
 * 
 * @author nm
 *
 */
public class EventAlarmServer extends AlarmServer<EventId, Event> {
    private StreamSubscriber eventStreamSubscriber;
    private int eventAlarmMinViolations;
    Stream eventStream;
    static final String EVENT_ALARMS_REALTIME_STREAM = "event_alarms_realtime";

    public EventAlarmServer(String yamcsInstance, ProcessorConfig procConfig, ScheduledThreadPoolExecutor timer) {
        super(yamcsInstance, timer);
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
}
