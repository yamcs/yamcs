package org.yamcs.cfdp;

import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.yarch.Stream;

public abstract class CfdpTransaction {

    static private IdGenerator transactionNrGenerator = new IdGenerator();

    private int id;
    private Stream cfdpOut;
    private IdGenerator sequenceNumberGenerator;

    public CfdpTransaction(Stream cfdpOut) {
        this.cfdpOut = cfdpOut;
        this.sequenceNumberGenerator = new IdGenerator();
    }

    public long getId() {
        return this.id;
    }

    public CfdpTransaction() {
        this.id = transactionNrGenerator.generate();
    }

    public abstract void step();

    void sendPacket(CfdpPacket p) {
        cfdpOut.emitTuple(p.toTuple(this.id));
    }

    public int getNextSequenceNumber() {
        return sequenceNumberGenerator.generate();
    }

}
