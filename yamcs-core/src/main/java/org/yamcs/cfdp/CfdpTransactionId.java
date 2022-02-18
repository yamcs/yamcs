package org.yamcs.cfdp;

import java.util.Objects;

public class CfdpTransactionId {
    private int sequenceNumber;
    private long initiatorEntity;

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
        return sequenceNumber == other.sequenceNumber && initiatorEntity == other.initiatorEntity;
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
