package org.yamcs.cascading;

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
import org.yamcs.protobuf.Event;
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
public class YamcsEventLink extends AbstractLink {
    YamcsLink parentLink;
    protected AtomicLong dataCount = new AtomicLong(0);

    EventSubscription subscription;
    Stream eventStream;

    public YamcsEventLink(YamcsLink parentLink) {
        this.parentLink = parentLink;
    }

    public void init(String instance, String name, YConfiguration config) {
        super.init(instance, name, config);
        String eventStreamName = config.getString("eventRealtimeStream", EventRecorder.REALTIME_EVENT_STREAM_NAME);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        this.eventStream = ydb.getStream(eventStreamName);
        if (this.eventStream == null) {
            throw new ConfigurationException("Cannto find stream " + eventStreamName);
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
        if (!isEffectivelyDisabled()) {
            doEnable();
        }
        notifyStarted();
    }

    @Override
    public void doDisable() {
        if (subscription != null) {
            subscription.cancel(true);
            subscription = null;
        }
    }

    @Override
    public void doEnable() {
        if (subscription != null && !subscription.isDone()) {
            return;
        }
        WebSocketClient wsclient = parentLink.getClient().getWebSocketClient();
        if (wsclient.isConnected()) {
            subscribeEvents();
        }
    }

    private void subscribeEvents() {
        YamcsClient yclient = parentLink.getClient();

        subscription = yclient.createEventSubscription();
        subscription.addMessageListener(new MessageListener<Event>() {
            @Override
            public void onMessage(Event ev) {
                processEvent(ev);
            }

            public void onError(Throwable t) {
                if (t instanceof ClientException) {
                    eventProducer.sendWarning("Got error when subscribing to containers: " + t.getMessage());
                } else {
                    log.warn("Got error when subscribing to containers: " + t.getMessage());
                }
            }
        });

        subscription.sendMessage(SubscribeEventsRequest.newBuilder()
                .setInstance(parentLink.getUpstreamInstance())
                .build());
    }

    private void processEvent(Event ev) {
        long rectime = timeService.getMissionTime();
        long gentime = ev.hasGenerationTime() ? TimeEncoding.fromProtobufTimestamp(ev.getGenerationTime()) : rectime;
        String source = ev.hasSource() ? ev.getSource() : parentLink.getName();
        int seqCount = ev.hasSeqNumber() ? ev.getSeqNumber() : 0;

        TupleDefinition tdef = eventStream.getDefinition();

        Db.Event.Builder db_ev = Db.Event.newBuilder().setReceptionTime(rectime).setGenerationTime(gentime)
                .setSource(source).setSeqNumber(seqCount);

        if (ev.hasCreatedBy()) {
            db_ev.setCreatedBy(ev.getCreatedBy());
        }
        if (ev.hasSeverity()) {
            db_ev.setSeverity(ev.getSeverity());
        }
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
        if (!isDisabled()) {
            doDisable();
        }
        notifyStopped();
    }

    @Override
    protected Status connectionStatus() {
        Status parentStatus = parentLink.connectionStatus();
        if (parentStatus == Status.OK) {
            boolean ok = subscription != null && !subscription.isDone();
            return ok ? Status.OK : Status.UNAVAIL;
        } else {
            return parentStatus;
        }
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
