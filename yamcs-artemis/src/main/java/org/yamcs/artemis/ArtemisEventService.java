package org.yamcs.artemis;

import java.util.List;
import org.yamcs.ConfigurationException;
import org.yamcs.StreamConfig;
import org.yamcs.StreamConfig.StandardStreamType;

/**
 * takes event data from yarch streams and publishes it to artemis address (reverse of {@link ArtemisEventDataLink}
 *
 */
public class ArtemisEventService extends AbstractArtemisTranslatorService {
    public ArtemisEventService(String instance, List<String> streamNames) throws ConfigurationException {
        super(instance, streamNames, new EventTupleTranslator());
    }
    public ArtemisEventService(String instance) throws ConfigurationException {
        this(instance, StreamConfig.getInstance(instance).getStreamNames(StandardStreamType.event));
    }
}
