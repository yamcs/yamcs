package org.yamcs.cfdp;

import java.util.Objects;

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
}
