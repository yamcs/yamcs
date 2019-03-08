package org.yamcs.cfdp;

import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.yarch.Stream;

public abstract class CfdpTransaction extends Thread {

    protected CfdpTransactionId myId;
    private Stream cfdpOut;

    public CfdpTransaction(int initiatorEntity, Stream cfdpOut) {
        this.myId = new CfdpTransactionId(initiatorEntity);
        this.cfdpOut = cfdpOut;
    }

    public CfdpTransactionId getTransactionId() {
        return this.myId;
    }

    // public CfdpTransaction() {
    // this.transactionId = transactionNrGenerator.generate();
    // }

    public abstract void step();

    public abstract void processPacket(CfdpPacket packet);

    protected void sendPacket(CfdpPacket p) {
        cfdpOut.emitTuple(p.toTuple(this.myId));
    }

}
