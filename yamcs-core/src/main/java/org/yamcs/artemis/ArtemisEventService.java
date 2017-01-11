package org.yamcs.artemis;

import java.util.List;
import org.yamcs.ConfigurationException;

/**
 * takes event data from yarch streams and publishes it to hornetq address (reverse of ActiveMQTmProvider)
 * Please remove the lines from the EventRecorder when enabling this in the config
 *
 */
public class ArtemisEventService extends AbstractArtemisTranslatorService {
    public ArtemisEventService(String instance, List<String> streamNames) throws ConfigurationException {
        super(instance, streamNames, new EventTupleTranslator());
    }
}
