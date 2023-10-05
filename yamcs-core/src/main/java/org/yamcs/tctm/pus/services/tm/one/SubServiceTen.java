package org.yamcs.tctm.pus.services.tm.one;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmModifier;

// FIXME: Update the error codes
enum RoutingFailedErrorCode {
    R1,
    R2,
    R3
}

public class SubServiceTen implements PusSubService {
    Map<Integer, RoutingFailedErrorCode> errorCodes = new HashMap<>();
    EventProducer eventProducer;

    static final String source = "Service: 1 | SubService: 10";
    static final String TC_ROUTING_FAILED = "TC_ROUTING_FAILED";

    public SubServiceTen(String yamcsInstance) {
        // FIXME: Confirm the repeatedEventTimeoutMillisec value, which most likely depends on the datarate of TM
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10);
        eventProducer.setSource(source);

        populateErrorCodes();
    }

    @Override
    public TmPacket process(TmPacket tmPacket) {
        byte[] dataField = PusTmModifier.getDataField(tmPacket);

        int errorCode = Byte.toUnsignedInt(dataField[0]);
        byte[] deducedPresence = Arrays.copyOfRange(dataField, 1, dataField.length);

        eventProducer.sendCritical(TC_ROUTING_FAILED,
                "TC with Destination ID: " + PusTmModifier.getDestinationID(tmPacket) + " has failed to route correctly | Error Code: " + errorCodes.get(errorCode) + " Deduced: " + deducedPresence);

        return tmPacket;
    }

    public void populateErrorCodes() {
        errorCodes.put(1, RoutingFailedErrorCode.R1);
        errorCodes.put(2, RoutingFailedErrorCode.R2);
        errorCodes.put(3, RoutingFailedErrorCode.R3);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
}
