package org.yamcs.alarms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.AbstractYamcsService;
import org.yamcs.ConfigurationException;
import org.yamcs.InitException;
import org.yamcs.StreamConfig;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.StreamConfig.StreamConfigEntry;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db.Event;

/**
 * This service is used in a replication setup to mirror the active alarms from a master instance. It works by
 * monitoring the replicated alarms_realtime and event_alarms_realtime and maintaining a list of active alarms.
 * <p>
 * Only reading alarms is allowed (including websocket subscription), no editing (ack, clear, shelve, etc).
 * <p>
 * Both parameters and events are supported.
 */
public class AlarmMirrorService extends AbstractYamcsService {

    Map<Stream, StreamSubscriber> susbscribers = new HashMap<>();
    Mdb mdb;
    ParameterAlarmMirrorServer parameterServer;
    EventAlarmMirrorServer eventServer;
    double alarmLoadDays = 30;

    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        mdb = MdbFactory.getInstance(yamcsInstance);
        this.alarmLoadDays = config.getDouble("alarmLoadDays", alarmLoadDays);
        parameterServer = new ParameterAlarmMirrorServer(yamcsInstance, alarmLoadDays);
        eventServer = new EventAlarmMirrorServer(yamcsInstance, alarmLoadDays);
    }

    @Override
    protected void doStart() {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        StreamConfig sc = StreamConfig.getInstance(yamcsInstance);


        List<StreamConfigEntry> sceList = sc.getEntries(StandardStreamType.PARAMETER_ALARM);
        for (StreamConfigEntry sce : sceList) {
            Stream inputStream = ydb.getStream(sce.getName());
            if (inputStream == null) {
                throw new ConfigurationException("Cannot find stream '" + sce.getName() + "'");
            }
            StreamSubscriber subscr = parameterServer::processTuple;
            inputStream.addSubscriber(subscr);
            susbscribers.put(inputStream, subscr);
        }

        sceList = sc.getEntries(StandardStreamType.EVENT_ALARM);
        for (StreamConfigEntry sce : sceList) {
            Stream inputStream = ydb.getStream(sce.getName());
            if (inputStream == null) {
                throw new ConfigurationException("Cannot find stream '" + sce.getName() + "'");
            }
            StreamSubscriber subscr = eventServer::processTuple;
            inputStream.addSubscriber(subscr);
            susbscribers.put(inputStream, subscr);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for (Map.Entry<Stream, StreamSubscriber> entry : susbscribers.entrySet()) {
            Stream stream = entry.getKey();
            StreamSubscriber subscriber = entry.getValue();
            stream.removeSubscriber(subscriber);
        }
        susbscribers.clear();
        notifyStopped();
    }


    public AbstractAlarmServer<Parameter, ParameterValue> getParameterServer() {
        return parameterServer;
    }

    public AbstractAlarmServer<EventId, Event> getEventServer() {
        return eventServer;
    }
}
