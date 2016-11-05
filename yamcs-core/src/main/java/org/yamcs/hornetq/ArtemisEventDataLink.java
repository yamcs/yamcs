package org.yamcs.hornetq;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.yamcs.archive.EventRecorder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Receives event data from Artemis queues and publishes into yamcs streams
 * @author nm
 *
 */
public class ArtemisEventDataLink extends AbstractService {
    String instance;

    StreamAdapter rtStreamAdapter, dumpStreamAdapter;

    public ArtemisEventDataLink(String instance) {
        this.instance = instance;
    }
    @Override
    protected void doStart() {
        try {
            YarchDatabase ydb = YarchDatabase.getInstance(instance);
            Stream realtimeEventStream=ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
            rtStreamAdapter = new StreamAdapter(realtimeEventStream, new SimpleString(instance+".events_realtime"), new EventTupleTranslator());

            Stream dumpEventStream=ydb.getStream(EventRecorder.DUMP_EVENT_STREAM_NAME);
            dumpStreamAdapter = new StreamAdapter(dumpEventStream, new SimpleString(instance+".events_dump"), new EventTupleTranslator());
        } catch (Exception e){
            notifyFailed(e);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        rtStreamAdapter.quit();
        dumpStreamAdapter.quit();
        notifyStopped();
    }

}
