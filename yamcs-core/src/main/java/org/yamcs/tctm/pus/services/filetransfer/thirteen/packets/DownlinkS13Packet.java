package org.yamcs.tctm.pus.services.filetransfer.thirteen.packets;

import org.yamcs.tctm.pus.services.filetransfer.thirteen.S13TransactionId;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public class DownlinkS13Packet extends FileTransferPacket {
    public static final TupleDefinition S13_TM = new TupleDefinition();

    public static enum PacketType {
        FIRST, INTERMEDIATE, LAST
    }

    static final String COL_LARGE_PACKET_TRANSACTION_ID = "largePacketTransactionId";
    static final String COL_SOURCE_ID = "sourceId";
    static final String COL_FILE_PART = "filePart";
    static final String COL_PART_SEQUENCE_NUMBER = "partSequenceNumber";
    static final String COL_PACKET_TYPE = "packetType";

    static {
        S13_TM.addColumn(COL_LARGE_PACKET_TRANSACTION_ID, DataType.LONG);
        S13_TM.addColumn(COL_SOURCE_ID, DataType.LONG);
        S13_TM.addColumn(COL_FILE_PART, DataType.BINARY);
        S13_TM.addColumn(COL_PART_SEQUENCE_NUMBER, DataType.LONG);
        S13_TM.addColumn(COL_PACKET_TYPE, DataType.ENUM);
    }

    protected PacketType packetType;
    protected long partSequenceNumber;
    protected byte[] filePart;

    DownlinkS13Packet(S13TransactionId transactionId, long partSequenceNumber, byte[] filePart, PacketType packetType) {
        super(transactionId);

        this.packetType = packetType;
        this.partSequenceNumber = partSequenceNumber;
        this.filePart = filePart;
    }

    public static DownlinkS13Packet fromTuple(Tuple t) {
        long largePacketTransactionId = (long) t.getLongColumn(COL_LARGE_PACKET_TRANSACTION_ID);
        long sourceId = (long) t.getLongColumn(COL_SOURCE_ID);
        long partSequenceNumber = (long) t.getLongColumn(COL_PART_SEQUENCE_NUMBER);
        byte[] filePart = (byte[]) t.getColumn(COL_FILE_PART);
        PacketType packetType = PacketType.valueOf((String) t.getColumn(COL_PACKET_TYPE));

        return new DownlinkS13Packet(new S13TransactionId(sourceId, 
                largePacketTransactionId, largePacketTransactionId), partSequenceNumber, filePart, packetType);
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
}
