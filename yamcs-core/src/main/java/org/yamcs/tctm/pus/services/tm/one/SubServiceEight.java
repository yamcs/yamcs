package org.yamcs.tctm.pus.services.tm.one;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmPacket;

// FIXME: Update the error codes
enum CompletionExecutionErrorCode {
    R1,
    R2,
    R3
}

public class SubServiceEight implements PusSubService {
    Map<Integer, CompletionExecutionErrorCode> errorCodes = new HashMap<>();
    EventProducer eventProducer;

    static final String source = "Service: 1 | SubService: 8";
    static final String TC_COMPLETION_EXECUTION_FAILED = "TC_COMPLETION_EXECUTION_FAILED";

    public SubServiceEight(String yamcsInstance) {
        // FIXME: Confirm the repeatedEventTimeoutMillisec value, which most likely depends on the datarate of TM
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10);
        eventProducer.setSource(source);

        populateErrorCodes();   
    }

    @Override
    public TmPacket process(PusTmPacket pusTmPacket) {
        byte[] dataField = pusTmPacket.getDataField();

        int errorCode = Byte.toUnsignedInt(dataField[0]);
        byte[] deducedPresence = Arrays.copyOfRange(dataField, 1, dataField.length);

        eventProducer.sendCritical(TC_COMPLETION_EXECUTION_FAILED,
                "TC with Destination ID: " + pusTmPacket.getDestinationID() + " has failed to complete execution | Error Code: " + errorCodes.get(errorCode) + " Deduced: " + deducedPresence);

        return pusTmPacket.getTmPacket();
    }

    public void populateErrorCodes() {
        errorCodes.put(1, CompletionExecutionErrorCode.R1);
        errorCodes.put(2, CompletionExecutionErrorCode.R2);
        errorCodes.put(3, CompletionExecutionErrorCode.R3);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
}
