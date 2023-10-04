package org.yamcs.tctm.pus.services.tm.one;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

public class SubServiceThree implements PusSubService {
    EventProducer eventProducer;

    static final String source = "Service: 1 | SubService: 3";
    static final String TC_START_EXECUTION_SUCCESS = "TC_START_EXECUTION_SUCCESS";

    public SubServiceThree(String yamcsInstance) {
        // FIXME: Confirm the repeatedEventTimeoutMillisec value, which most likely depends on the datarate of TM
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10);
        eventProducer.setSource(source);
    }

    @Override
    public TmPacket process(PusTmPacket pusTmPacket) {
        eventProducer.sendInfo(TC_START_EXECUTION_SUCCESS,
                "TC with Destination ID: " + pusTmPacket.getDestinationID() + " has started execution");

        return pusTmPacket.getTmPacket();
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

}
