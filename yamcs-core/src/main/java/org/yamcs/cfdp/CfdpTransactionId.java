package org.yamcs.cfdp;

import java.util.Objects;

import org.yamcs.utils.TimeEncoding;

public class CfdpTransactionId {
    private int sequenceNumber;
    private long initiatorEntity;
    private final long startTime;

    static IdGenerator transactionNrGenerator = new IdGenerator();

    public CfdpTransactionId(long entityId) {
        this(entityId, CfdpTransactionId.transactionNrGenerator.generate());
    }

    public CfdpTransactionId(long entityId, long sequenceNumber) {
        this.startTime = TimeEncoding.getWallclockTime();//TODO use mission time?
        this.initiatorEntity = entityId;
        this.sequenceNumber = (int)sequenceNumber;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public long getInitiatorEntity() {
        return initiatorEntity;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        CfdpTransactionId other = (CfdpTransactionId) o;
        return Objects.equals(sequenceNumber, other.sequenceNumber)
                && Objects.equals(initiatorEntity, other.initiatorEntity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceNumber, initiatorEntity);
    }
    
    @Override
    public String toString() {
        return initiatorEntity+"_"+sequenceNumber;
    }

    public long getStartTime() {
        return startTime;
    }
}
