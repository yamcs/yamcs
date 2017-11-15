package org.yamcs.artemis;

import java.util.List;
import org.yamcs.ConfigurationException;

/**
 * takes TM data from yamcs streams and publishes it to ActiveMQ Artemis address (reverse of {@link org.yamcs.tctm.ArtemisTmDataLink})
 *
 */
public class ArtemisTmService extends AbstractArtemisTranslatorService {
    public ArtemisTmService(String instance, List<String> streamNames) throws ConfigurationException {
        super(instance, streamNames, new TmTupleTranslator());
    }
}
