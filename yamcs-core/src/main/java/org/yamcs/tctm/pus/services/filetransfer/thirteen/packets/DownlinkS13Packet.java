package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.protobuf.TransferDirection;

public class DownlinkS13Packet extends FileTransferPacket {
    public static final TupleDefinition S13_TM = new TupleDefinition();

    public static enum PacketType {
        FIRST("FIRST"), INTERMEDIATE("INTERMEDIATE"), LAST("LAST"), ABORTION("ABORTION");

        private String packetType;

        PacketType(String packetType) {
            this.packetType = packetType;
        }

        public String getText() {
            return this.packetType;
        }

        public static PacketType fromString(String text) {
            for (PacketType b : PacketType.values()) {
                if (b.packetType.equalsIgnoreCase(text)) {
                    return b;
                }
            }
            return null;
        }
    }

    static final String COL_LARGE_PACKET_TRANSACTION_ID = "largePacketTransactionId";
    static final String COL_SOURCE_ID = "sourceId";
    static final String COL_FILE_PART = "filePart";
    static final String COL_PART_SEQUENCE_NUMBER = "partSequenceNumber";
    static final String COL_PACKET_TYPE = "packetType";
    static final String COL_FAILURE_REASON = "failureReason";

    static {
        S13_TM.addColumn(COL_LARGE_PACKET_TRANSACTION_ID, DataType.LONG);
        S13_TM.addColumn(COL_SOURCE_ID, DataType.LONG);
        S13_TM.addColumn(COL_FILE_PART, DataType.BINARY);
        S13_TM.addColumn(COL_PART_SEQUENCE_NUMBER, DataType.LONG);
        S13_TM.addColumn(COL_PACKET_TYPE, DataType.ENUM);
        S13_TM.addColumn(COL_FAILURE_REASON, DataType.INT);
    }

    protected PacketType packetType;
    protected long partSequenceNumber;
    protected byte[] filePart;
    protected Integer failureCode;

    DownlinkS13Packet(S13TransactionId transactionId, long partSequenceNumber, byte[] filePart, PacketType packetType, Integer failureCode) {
        super(transactionId);
        this.packetType = packetType;
        this.partSequenceNumber = partSequenceNumber;
        this.filePart = filePart;
        this.failureCode = failureCode;
    }

    public static DownlinkS13Packet fromTuple(Tuple t) {
        Long largePacketTransactionId = (Long) t.getLongColumn(COL_LARGE_PACKET_TRANSACTION_ID);
        Long sourceId = (Long) t.getLongColumn(COL_SOURCE_ID);
        Long partSequenceNumber = (Long) t.getLongColumn(COL_PART_SEQUENCE_NUMBER);
        byte[] filePart = (byte[]) t.getColumn(COL_FILE_PART);
        PacketType packetType = PacketType.fromString((String) t.getColumn(COL_PACKET_TYPE));
        Integer failureCode = (Integer) t.getColumn(COL_FAILURE_REASON);

        return new DownlinkS13Packet(new S13TransactionId(sourceId, 
                largePacketTransactionId, largePacketTransactionId, TransferDirection.DOWNLOAD), partSequenceNumber, filePart, packetType, failureCode);
    }
    
    public PacketType getPacketType() {
        return packetType;
    }

    public byte[] getFilePart() {
        return filePart;
    }

    public long getPartSequenceNumber() {
        return partSequenceNumber;
    }

    public Integer getFailureCode() {
        return failureCode;
    }

}
