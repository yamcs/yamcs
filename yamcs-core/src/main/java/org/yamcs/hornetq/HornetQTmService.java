package org.yamcs.hornetq;

import java.util.List;
import org.yamcs.ConfigurationException;

/**
 * takes TM data from yarch streams and publishes it to hornetq address (reverse of ActiveMQTmProvider)
 *
 */
public class HornetQTmService extends AbstractHornetQTranslatorService {
    public HornetQTmService(String instance, List<String> streamNames) throws ConfigurationException {
        super(instance, streamNames, new TmTupleTranslator());
    }
}
