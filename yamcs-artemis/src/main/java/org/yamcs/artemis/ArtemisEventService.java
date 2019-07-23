package org.yamcs.artemis;

import java.util.List;

import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;
import org.yamcs.YConfiguration;

/**
 * Takes event data from yarch streams and publishes it to artemis address (reverse of {@link ArtemisEventDataLink})
 */
public class ArtemisEventService extends AbstractArtemisTranslatorService {

    @Deprecated
    public ArtemisEventService(String instance, List<String> streamNames) {
        super(instance, streamNames, new EventTupleTranslator());
        log.warn("DEPRECATION: Define stream names under 'args -> streamNames' "
                + "in the configuration of ArtemisEventService");
    }

    public ArtemisEventService(String instance) {
        this(instance, StreamConfig.getInstance(instance).getStreamNames(StandardStreamType.event));
    }

    public ArtemisEventService(String instance, YConfiguration args) {
        super(instance, args.getList("streamNames"), new EventTupleTranslator());
    }
}
