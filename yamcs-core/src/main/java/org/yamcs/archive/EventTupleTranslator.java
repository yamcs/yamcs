package org.yamcs.archive;

import org.hornetq.api.core.client.ClientMessage;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.hornet.TupleTranslator;

import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.protobuf.Yamcs.Event;

public class EventTupleTranslator implements TupleTranslator {
    
    @Override
    public ClientMessage buildMessage(ClientMessage msg, Tuple tuple) {
        Event event=(Event)tuple.getColumn("body");
        Protocol.encode(msg, event);
        return msg;
    }

    @Override
    public Tuple buildTuple(TupleDefinition tdef, ClientMessage msg) {
        try {
            Event event=(Event)Protocol.decode(msg, Event.newBuilder());
            Tuple t=new Tuple(tdef, new Object[]{event.getGenerationTime(), 
                     event.getSource(), event.getSeqNumber(), event});
            return t;
        } catch (YamcsApiException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

}
