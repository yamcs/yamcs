package org.yamcs.tctm.pus.services.tm.one;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;

// FIXME: Update the error codes
enum ProgressExecutionFailedErrorCode {
    R1,
    R2,
    R3
}

public class SubServiceSix implements PusSubService {
    Map<Long, ProgressExecutionFailedErrorCode> errorCodes = new HashMap<>();
    EventProducer eventProducer;

    static final String source = "Service: 1 | SubService: 6";
    static final String TC_PROGRESS_EXECUTION_FAILED = "TC_PROGRESS_EXECUTION_FAILED";

    public SubServiceSix(String yamcsInstance) {
        // FIXME: Confirm the repeatedEventTimeoutMillisec value, which most likely depends on the datarate of TM
        eventProducer = EventProducerFactory.getEventProducer(yamcsInstance, this.getClass().getSimpleName(), 10);
        eventProducer.setSource(source);

        populateErrorCodes();
    }

    @Override
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());

        byte[] dataField = pPkt.getDataField();
        byte[] failureNotice = Arrays.copyOfRange(dataField, ServiceOne.REQUEST_ID_LENGTH, dataField.length);

        long errorCode = ByteArrayUtils.decodeCustomInteger(failureNotice, 0, ServiceOne.failureCodeSize);
        byte[] deducedPresence = Arrays.copyOfRange(failureNotice, ServiceOne.failureCodeSize, failureNotice.length);

        eventProducer.sendCritical(TC_PROGRESS_EXECUTION_FAILED,
                "TC with Destination ID: " + pPkt.getDestinationID() + " has failed during execution | Error Code: " + errorCodes.get(errorCode) + " Deduced: " + deducedPresence);

        ArrayList<TmPacket> pktList = new ArrayList<>();
        pktList.add(tmPacket);

        return pktList;
    }

    public void populateErrorCodes() {
        errorCodes.put((long)1, ProgressExecutionFailedErrorCode.R1);
        errorCodes.put((long)2, ProgressExecutionFailedErrorCode.R2);
        errorCodes.put((long)3, ProgressExecutionFailedErrorCode.R3);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }

}
