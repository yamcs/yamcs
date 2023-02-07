package org.yamcs.cfdp.pdu;

import com.google.common.primitives.Bytes;
import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.CfdpUtils;

import java.nio.ByteBuffer;

public class OriginatingTransactionId extends ReservedMessageToUser {
    // Maybe good to associate with CfdpTransactionId?

    private final long sourceEntityId;
    private final long transactionSequenceNumber;

    public OriginatingTransactionId(long sourceEntityId, long transactionSequenceNumber) {
        super(MessageType.ORIGINATING_TRANSACTION_ID, encode(sourceEntityId, transactionSequenceNumber));

        this.sourceEntityId = sourceEntityId;
        this.transactionSequenceNumber = transactionSequenceNumber;
    }

    public OriginatingTransactionId(byte[] content) {
        super(MessageType.ORIGINATING_TRANSACTION_ID, content);

        ByteBuffer buffer = ByteBuffer.wrap(content);
        byte lengths = buffer.get();

        this.sourceEntityId = CfdpUtils.getUnsignedLongFromBuffer(buffer, ((lengths >> 4) & 0x07) + 1);
        this.transactionSequenceNumber = CfdpUtils.getUnsignedLongFromBuffer(buffer, (lengths & 0x07) + 1);
    }

    private static byte[] encode(long sourceEntityId, long transactionSequenceNumber) {
        byte[] entityId = CfdpUtils.longToTrimmedBytes(sourceEntityId);
        byte[] sequenceNumber = CfdpUtils.longToTrimmedBytes(transactionSequenceNumber);

        byte[] lengths = new byte[]{ (byte) ((entityId.length - 1 << 4) | (sequenceNumber.length - 1)) };
        return Bytes.concat(lengths, entityId, sequenceNumber);
    }

    public long getSourceEntityId() {
        return sourceEntityId;
    }

    public long getTransactionSequenceNumber() {
        return transactionSequenceNumber;
    }

    public CfdpTransactionId toCfdpTransactionId() {
        return new CfdpTransactionId(sourceEntityId, transactionSequenceNumber);
    }

    @Override
    public String toJson() {
        return "{type=" + getType() + ", length=" + getValue().length + ", messageType=" + MessageType.ORIGINATING_TRANSACTION_ID
                + ", sourceEntityId=" + sourceEntityId + ", transactionSequenceNumber=" + transactionSequenceNumber + "}";
    }

    @Override
    public String toString() {
        return "OriginatingTransactionId(sourceEntityID: " + sourceEntityId +", seqNum: " + transactionSequenceNumber +")";
    }
}
