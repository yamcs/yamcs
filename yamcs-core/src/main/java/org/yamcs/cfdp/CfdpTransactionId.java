package org.yamcs.cfdp;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class CfdpTransactionId {
    private int sequenceNumber;
    private long initiatorEntity;

    static final AtomicInteger transactionNrGenerator = new AtomicInteger(1); 

    public CfdpTransactionId(long entityId) {
        this(entityId, transactionNrGenerator.getAndIncrement());
    }

    public CfdpTransactionId(long entityId, long sequenceNumber) {
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

}
