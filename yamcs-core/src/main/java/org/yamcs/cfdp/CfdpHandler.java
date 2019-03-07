package org.yamcs.cfdp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

public class CfdpHandler implements StreamSubscriber {

    static Logger log = LoggerFactory.getLogger(CfdpHandler.class.getName());

    private Stream cfdpIn, cfdpOut;

    public CfdpHandler(Stream in, Stream out) {
        this.cfdpIn = in;
        this.cfdpOut = out;
        this.cfdpIn.addSubscriber(this);
    }

    public CfdpTransaction processRequest(CfdpRequest request) {
        switch (request.getType()) {
        case PUT:
            return processPutRequest((PutRequest) request);
        }
        return null;
    }

    public CfdpTransfer processPutRequest(PutRequest request) {
        // TODO processing and returning should be asynchronous
        CfdpTransfer transfer = new CfdpTransfer(request, this.cfdpOut);
        while (transfer.isOngoing()) {
            transfer.step();
        }
        return transfer;
    }

    @Override
    public void onTuple(Stream stream, Tuple tuple) {
        CfdpPacket packet = CfdpPacket.fromTuple(tuple);
        // log.error(packet.toString());

    }

    @Override
    public void streamClosed(Stream stream) {
        // TODO Auto-generated method stub

    }

}
