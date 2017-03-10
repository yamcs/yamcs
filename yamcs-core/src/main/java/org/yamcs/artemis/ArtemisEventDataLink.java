package org.yamcs.artemis;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StreamConfigEntry;
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
    Map<String, StreamAdapter> streamAdapters = new HashMap<>();

    public ArtemisEventDataLink(String instance) {
        this.instance = instance;
    }
    @Override
    protected void doStart() {
        try {
            YarchDatabase ydb = YarchDatabase.getInstance(instance);
            StreamConfig sc = StreamConfig.getInstance(instance);
            for(StreamConfigEntry sce: sc.getEntries()) {
                if(sce.getType() == StreamConfig.StandardStreamType.event) {
                    Stream stream = ydb.getStream(sce.getName());
                    StreamAdapter adapter = new StreamAdapter(stream, new SimpleString(instance+"."+sce.getName()), new EventTupleTranslator());
                    streamAdapters.put(sce.getName(), adapter);
                }
            }
        } catch (Exception e){
            notifyFailed(e);
        }
        notifyStarted();
    }

    @Override
    protected void doStop() {
        for(StreamAdapter adapter: streamAdapters.values()) {
            adapter.quit();
        }
        notifyStopped();
    }

}
