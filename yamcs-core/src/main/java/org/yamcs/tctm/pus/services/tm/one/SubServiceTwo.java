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
enum AcceptanceRejectionCode {
    R1,
    R2,
    R3
}

public class SubServiceTwo implements PusSubService {
    Map<Integer, AcceptanceRejectionCode> errorCodes = new HashMap<>();
    EventProducer eventProducer;

    static final String source = "Service: 1 | SubService: 2";
    static final String TC_ACCEPTANCE_FAILED = "TC_ACCEPTANCE_FAILED";

    public SubServiceTwo(String yamcsInstance) {
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

        eventProducer.sendCritical(TC_ACCEPTANCE_FAILED,
                "TC with Destination ID: " + PusTmModifier.getDestinationID(tmPacket) + " has been rejected | Error Code: " + errorCodes.get(errorCode) + " Deduced: " + deducedPresence);

        return tmPacket;
    }

    public void populateErrorCodes() {
        errorCodes.put(1, AcceptanceRejectionCode.R1);
        errorCodes.put(2, AcceptanceRejectionCode.R2);
        errorCodes.put(3, AcceptanceRejectionCode.R3);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
}
