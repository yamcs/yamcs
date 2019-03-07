package org.yamcs.cfdp;

public class CfdpTransactionId {
    private long sequenceNumber;
    private long initiatorEntity;

    static IdGenerator transactionNrGenerator = new IdGenerator();

    public CfdpTransactionId(long entityId) {
        this(entityId, CfdpTransactionId.transactionNrGenerator.generate());
    }

    public CfdpTransactionId(long entityId, long sequenceNumber) {
        this.initiatorEntity = entityId;
        this.sequenceNumber = sequenceNumber;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getInitiatorEntity() {
        return initiatorEntity;
    }
}
