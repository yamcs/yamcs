package org.yamcs.artemis;

import java.util.List;

import org.yamcs.YConfiguration;

/**
 * Takes TM data from yamcs streams and publishes it to ActiveMQ Artemis address (reverse of
 * {@link org.yamcs.tctm.ArtemisTmDataLink})
 *
 */
public class ArtemisTmService extends AbstractArtemisTranslatorService {

    @Deprecated
    public ArtemisTmService(String instance, List<String> streamNames) {
        super(instance, streamNames, new TmTupleTranslator());
        log.warn("DEPRECATION: Define stream names under 'args -> streamNames' "
                + "in the configuration of ArtemisTmService");
    }

    public ArtemisTmService(String instance, YConfiguration args) {
        super(instance, args.getList("streamNames"), new TmTupleTranslator());
    }
}
