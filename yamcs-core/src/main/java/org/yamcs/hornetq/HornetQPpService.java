package org.yamcs.hornetq;

import java.util.List;

import org.yamcs.ConfigurationException;

/**
 * takes Parameter data from yarch streams and publishes it to hornetq address (reverse of ActiveMQPpProvider)
 * 
 * To avoid a ping-pong effect:
 *  - it creates a queue with a filter on hornet side
 *  - it remembers a thread local version of the tuple in transition on yarch side
 *
 */
public class HornetQPpService extends AbstractHornetQTranslatorService {
    
    public HornetQPpService(String instance, List<String> streamNames) throws ConfigurationException {
        super(instance, streamNames, new PpTupleTranslator());
    }
}
