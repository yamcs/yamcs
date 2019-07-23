package org.yamcs.artemis;

import java.util.List;

import org.yamcs.YConfiguration;
import org.yamcs.tctm.ArtemisParameterDataLink;

/**
 * takes Parameter data from yarch streams and publishes it to hornetq address (reverse of
 * {@link ArtemisParameterDataLink})
 * 
 * To avoid a ping-pong effect:
 * <ul>
 * <li>it creates a queue with a filter on hornet side
 * <li>it remembers a thread local version of the tuple in transition on yarch side
 * </ul>
 */
public class ArtemisPpService extends AbstractArtemisTranslatorService {

    @Deprecated
    public ArtemisPpService(String instance, List<String> streamNames) {
        super(instance, streamNames, new PpTupleTranslator());
        log.warn("DEPRECATION: Define stream names under 'args -> streamNames' "
                + "in the configuration of ArtemisPpService");
    }

    public ArtemisPpService(String instance, YConfiguration args) {
        super(instance, args.getList("streamNames"), new PpTupleTranslator());
    }
}
