package org.yamcs.yfe;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.yamcs.YConfiguration;
import org.yamcs.archive.EventRecorder;
import org.yamcs.ConfigurationException;
import org.yamcs.client.ClientException;
import org.yamcs.client.EventSubscription;
import org.yamcs.client.MessageListener;
import org.yamcs.client.YamcsClient;
import org.yamcs.client.base.WebSocketClient;
import org.yamcs.yfe.protobuf.Yfe.Event;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.tctm.AbstractLink;
import org.yamcs.tctm.AggregatedDataLink;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db;

/**
 * 
 * Yamcs TM link - subscribes to realtime telemetry
 *
 */
public class EventLink extends AbstractLink {
    final YfeLink parentLink;
    final int targetId;

    protected AtomicLong dataCount = new AtomicLong(0);

    EventSubscription subscription;
    Stream eventStream;

    public EventLink(YfeLink yfeLink, int targetId) {
        this.parentLink = yfeLink;
        this.targetId = targetId;
    }
    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        String eventStreamName = config.getString("eventRealtimeStream", EventRecorder.REALTIME_EVENT_STREAM_NAME);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        this.eventStream = ydb.getStream(eventStreamName);
        if (this.eventStream == null) {
            throw new ConfigurationException("Cannot find stream " + eventStreamName);
        }
    }

    // a little bit of a hack: because we use only one config for the parent,
    // we generate a new one for the sublink with the name tmRealtimeStream/tmArchiveStream changed to tmStream
    // depending on which link it is
    static YConfiguration swapConfig(YConfiguration config, String oldKey, String newKey, String defaultValue) {
        Map<String, Object> root = new HashMap<>(config.getRoot());
        String value = (String) root.remove(oldKey);
        if (value == null) {
            value = defaultValue;
        }

        root.put(newKey, value);
        return YConfiguration.wrap(root);
    }

    @Override
    protected void doStart() {

        notifyStarted();
    }

   

    public void processEvent(Event ev) {
        long rectime = timeService.getMissionTime();
        long gentime = ev.hasGenerationTime() ? ev.getGenerationTime() : rectime;
        String source = ev.hasSource() ? ev.getSource() : parentLink.getName();
        int seqCount = ev.hasSeqNumber() ? ev.getSeqNumber() : 0;

        TupleDefinition tdef = eventStream.getDefinition();

        Db.Event.Builder db_ev = Db.Event.newBuilder().setReceptionTime(rectime).setGenerationTime(gentime)
                .setSource(source).setSeqNumber(seqCount);

        if (ev.hasCreatedBy()) {
            db_ev.setCreatedBy(ev.getCreatedBy());
        }
        // if (ev.hasSeverity()) {
        //     db_ev.setSeverity(ev.getSeverity());
        // }
        if (ev.hasType()) {
            db_ev.setType(ev.getType());
        }
        if (ev.hasMessage()) {
            db_ev.setMessage(ev.getMessage());
        }


        Tuple t = new Tuple(tdef, new Object[] { gentime, source, seqCount, db_ev.build() });
        dataCount.incrementAndGet();
        eventStream.emitTuple(t);
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    @Override
    protected Status connectionStatus() {
        return parentLink.connectionStatus();
    }

    @Override
    public AggregatedDataLink getParent() {
        return parentLink;
    }

    @Override
    public long getDataInCount() {
        return dataCount.get();
    }

    @Override
    public long getDataOutCount() {
        return 0;
    }

    @Override
    public void resetCounters() {
        dataCount.set(0);
    }
}
