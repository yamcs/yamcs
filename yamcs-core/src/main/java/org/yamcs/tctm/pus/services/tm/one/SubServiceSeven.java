package org.yamcs.tctm.pus.services.tm.one;

import org.yamcs.TmPacket;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class SubServiceSeven implements PusSubService {
    EventProducer eventProducer;

    static final String source = "Service: 1 | SubService: 7";
    static final String TC_COMPLETION_EXECUTION_SUCCESS = "TC_COMPLETION_EXECUTION_SUCCESS";

    public SubServiceSeven(String yamcsInstance) {
        // FIXME: Confirm the repeatedEventTimeoutMillisec value, which most likely depends on the datarate of TM
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10);
        eventProducer.setSource(source);
    }

    @Override
    public TmPacket process(PusTmPacket pusTmPacket) {
        eventProducer.sendInfo(TC_COMPLETION_EXECUTION_SUCCESS,
                "TC with Destination ID: " + pusTmPacket.getDestinationID() + " has succeeded execution");
        
        return pusTmPacket.getTmPacket();
    }
}
