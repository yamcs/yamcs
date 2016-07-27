package org.yamcs.hornetq;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;


/**
 * Translates between tuples and ActiveMQ messages.
 * 
 **/

public interface TupleTranslator {
    /**
     * @return the original msg
     */
    ClientMessage buildMessage(ClientMessage msg, Tuple tuple);
    /**
     * 
     * @param message
     * @return
     */
    Tuple buildTuple(TupleDefinition tdef, ClientMessage message);
}
