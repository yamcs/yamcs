package org.yamcs.tctm.pus.services.tm.one;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

// FIXME: Update the error codes
enum StartExecutionFailedErrorCode {
    R1,
    R2,
    R3
}

public class SubServiceFour implements PusSubService {
    Map<Integer, StartExecutionFailedErrorCode> errorCodes = new HashMap<>();
    EventProducer eventProducer;

    static final String source = "Service: 1 | SubService: 4";
    static final String TC_START_EXECUTION_FAILED = "TC_START_EXECUTION_FAILED";


    public SubServiceFour(String yamcsInstance) {
        // FIXME: Confirm the repeatedEventTimeoutMillisec value, which most likely depends on the datarate of TM
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10);
        eventProducer.setSource(source);

        populateErrorCodes();
    }

    @Override
    public void process(PusTmPacket pusTmPacket) {
        byte[] dataField = pusTmPacket.getDataField();

        int errorCode = Byte.toUnsignedInt(dataField[0]);
        byte[] deducedPresence = Arrays.copyOfRange(dataField, 1, dataField.length);

        eventProducer.sendCritical(TC_START_EXECUTION_FAILED,
                "TC with Destination ID: " + pusTmPacket.getDestinationID() + " has failed to start execution | Error Code: " + errorCodes.get(errorCode) + " Deduced: " + deducedPresence);

    }

    public void populateErrorCodes() {
        errorCodes.put(1, StartExecutionFailedErrorCode.R1);
        errorCodes.put(2, StartExecutionFailedErrorCode.R2);
        errorCodes.put(3, StartExecutionFailedErrorCode.R3);
    }

}
