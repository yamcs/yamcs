package org.yamcs.tctm.pus.services.tm.one;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import org.yamcs.TmPacket;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.tctm.pus.PusTmManager;
import org.yamcs.tctm.pus.services.PusSubService;
import org.yamcs.tctm.pus.services.tm.PusTmCcsdsPacket;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.StringConverter;

// FIXME: Update the error codes
enum CompletionExecutionErrorCode {
    R1,
    R2,
    R3
}

public class SubServiceEight implements PusSubService {
    Map<Long, CompletionExecutionErrorCode> errorCodes = new HashMap<>();
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
    public ArrayList<TmPacket> process(TmPacket tmPacket) {
        PusTmCcsdsPacket pPkt = new PusTmCcsdsPacket(tmPacket.getPacket());

        byte[] dataField = pPkt.getDataField();
        int tcCcsdsApid = ByteArrayUtils.decodeUnsignedShort(dataField, 0) & 0x07FF;
        int tcCcsdsSeqCount = ByteArrayUtils.decodeUnsignedShort(dataField, 2) & 0x3FFF;

        if (PusTmManager.destinationId != pPkt.getDestinationID())
            return null;

        byte[] failureNotice = Arrays.copyOfRange(dataField, ServiceOne.REQUEST_ID_LENGTH, dataField.length);
        long errorCode = ByteArrayUtils.decodeCustomInteger(failureNotice, 0, ServiceOne.failureCodeSize);
        byte[] deducedPresence = Arrays.copyOfRange(failureNotice, ServiceOne.failureCodeSize, failureNotice.length);

        eventProducer.sendCritical(TC_COMPLETION_EXECUTION_FAILED,
            "TC with (Source ID: " + pPkt.getDestinationID() + " | Apid: " + tcCcsdsApid + " | Packet Seq Count: "
                    + tcCcsdsSeqCount
                    + ") has failed to complete execution | Error Code: " + errorCodes.get(errorCode) + " Deduced: " + StringConverter.arrayToHexString(deducedPresence)
        );

        ArrayList<TmPacket> pktList = new ArrayList<>();
        pktList.add(tmPacket);

        return pktList;
    }

    public void populateErrorCodes() {
        errorCodes.put((long)1, CompletionExecutionErrorCode.R1);
        errorCodes.put((long)2, CompletionExecutionErrorCode.R2);
        errorCodes.put((long)3, CompletionExecutionErrorCode.R3);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
}
