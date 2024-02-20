package org.yamcs.tctm.pus.services.tm.one;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
enum AcceptanceRejectionCode {
    R1,
    R2,
    R3
}

public class SubServiceTwo implements PusSubService {
    Map<Long, AcceptanceRejectionCode> errorCodes = new HashMap<>();
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

        eventProducer.sendCritical(TC_ACCEPTANCE_FAILED,
            "TC with (Source ID: " + pPkt.getDestinationID() + " | Apid: " + tcCcsdsApid + " | Packet Seq Count: "
                    + tcCcsdsSeqCount
                    + ") has been rejected | Error Code: " + errorCodes.get(errorCode) + " Deduced: " + StringConverter.arrayToHexString(deducedPresence)
        );

        ArrayList<TmPacket> pktList = new ArrayList<>();
        pktList.add(tmPacket);

        return pktList;
    }
    public void populateErrorCodes() {
        errorCodes.put((long)1, AcceptanceRejectionCode.R1);
        errorCodes.put((long)2, AcceptanceRejectionCode.R2);
        errorCodes.put((long)3, AcceptanceRejectionCode.R3);
    }

    @Override
    public PreparedCommand process(PreparedCommand pusTelecommand) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'process'");
    }
}
