package org.yamcs.cfdp;

import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.yarch.Stream;

public abstract class CfdpTransaction {

    private CfdpTransactionId myId;
    private Stream cfdpOut;
    private IdGenerator sequenceNumberGenerator;

    public CfdpTransaction(int initiatorEntity, Stream cfdpOut) {
        this.myId = new CfdpTransactionId(initiatorEntity);
        this.cfdpOut = cfdpOut;
        this.sequenceNumberGenerator = new IdGenerator();
    }

    public CfdpTransactionId getId() {
        return this.myId;
    }

    // public CfdpTransaction() {
    // this.transactionId = transactionNrGenerator.generate();
    // }

    public abstract void step();

    void sendPacket(CfdpPacket p) {
        cfdpOut.emitTuple(p.toTuple(this.myId));
    }

    public int getNextSequenceNumber() {
        return sequenceNumberGenerator.generate();
    }

}
