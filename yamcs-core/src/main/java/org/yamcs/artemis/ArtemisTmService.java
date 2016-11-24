package org.yamcs.artemis;

import java.util.List;
import org.yamcs.ConfigurationException;

/**
 * takes TM data from yarch streams and publishes it to hornetq address (reverse of ActiveMQTmProvider)
 *
 */
public class ArtemisTmService extends AbstractArtemisTranslatorService {
    public ArtemisTmService(String instance, List<String> streamNames) throws ConfigurationException {
        super(instance, streamNames, new TmTupleTranslator());
    }
}
