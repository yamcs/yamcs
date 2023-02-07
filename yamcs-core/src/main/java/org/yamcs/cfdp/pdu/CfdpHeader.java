package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.cfdp.CfdpUtils;

public class CfdpHeader {
    /*
     * Header (Variable size):
     * 3 bits = Version ('000')
     * 1 bit = PDU type (0 = File Directive, 1 = File Data)
     * 1 bit = Direction (0 = towards receiver, 1 = towards sender)
     * 1 bit = Transmission mode (0 = acknowledged, 1 = unacknowledged)
     * 1 bit = CRC flag (0 = CRC not present, 1 = CRC present)
     * 1 bit = Large File (0=small file, 1 = Large file)
     * 16 bits = PDU data field length (in octets)
     * 1 bit = reserved ('0')
     * 3 bits = length of entity IDs (number of octets in entity ID minus 1)
     * 1 bit = reserved ('0')
     * 3 bits = length of transaction sequence number (number of octets in sequence number minus 1)
     * variable = source entity id (UINT)
     * variable = transaction sequence number (UINT)
     * variable = destination entity id (UINT)
     */

    // header types
    private boolean fileDirective, towardsSender, acknowledged, withCrc, largeFile;
    private int entityIdLength, sequenceNumberLength;
    private long sourceId, destinationId, sequenceNr;

    public CfdpHeader(boolean fileDirective, boolean towardsSender, boolean acknowledged, boolean withCrc,
            int entityIdLength, int sequenceNumberLength, long sourceId, long destinationId, long sequenceNumber) {
        this.fileDirective = fileDirective;
        this.towardsSender = towardsSender;
        this.acknowledged = acknowledged;
        this.withCrc = withCrc;
        this.entityIdLength = entityIdLength;
        this.sequenceNumberLength = sequenceNumberLength;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.sequenceNr = sequenceNumber;
    }

    /**
     * decodes the header from the ByteBuffer at the current position and sets the position just after the header
     */
    CfdpHeader(ByteBuffer buffer) {
        readPduHeader(buffer);
    }

    public CfdpHeader copy() {
        return copy(fileDirective, towardsSender);
    }

    public CfdpHeader copy(boolean fileDirective) {
        return copy(fileDirective, towardsSender);
    }

    public CfdpHeader copy(boolean fileDirective, boolean towardsSender) {
        return new CfdpHeader(fileDirective, towardsSender, acknowledged, withCrc, entityIdLength, sequenceNumberLength, sourceId, destinationId, sequenceNr);
    }

    public CfdpHeader copyDirectionInverted() {
        return copy(fileDirective, !towardsSender);
    }

    public CfdpTransactionId getTransactionId() {
        return new CfdpTransactionId(this.sourceId, this.sequenceNr);
    }

    public boolean isFileDirective() {
        return fileDirective;
    }

    public boolean withCrc() {
        return withCrc;
    }

    public int getEntityIdLength() {
        return this.entityIdLength;
    }

    public int getSequenceNumberLength() {
        return this.sequenceNumberLength;
    }

    public long getSequenceNumber() {
        return sequenceNr;
    }

    public long getDestinationId() {
        return this.destinationId;
    }

    public long getSourceId() {
        return this.sourceId;
    }

    public boolean isAcknowledged() {
        return this.acknowledged;
    }

    public boolean isLargeFile() {
        return largeFile;
    }

    public void setLargeFile(boolean largeFile) {
        this.largeFile = largeFile;
    }

    /*
     * Reads the header of the incoming buffer, which is assumed to be a complete PDU
     * Afterwards puts the buffer position right after the header
     */
    private void readPduHeader(ByteBuffer buffer) {
        byte tempByte = buffer.get();
        fileDirective = !CfdpUtils.isBitOfByteSet(tempByte, 3);
        towardsSender = CfdpUtils.isBitOfByteSet(tempByte, 4);
        acknowledged = !CfdpUtils.isBitOfByteSet(tempByte, 5);
        withCrc = CfdpUtils.isBitOfByteSet(tempByte, 6);
        largeFile = CfdpUtils.isBitOfByteSet(tempByte, 7);
        CfdpUtils.getUnsignedShort(buffer); // datalength
        tempByte = buffer.get();
        entityIdLength = ((tempByte >> 4) & 0x07) + 1;
        sequenceNumberLength = (tempByte & 0x07) + 1;
        sourceId = CfdpUtils.getUnsignedLongFromBuffer(buffer, entityIdLength);
        sequenceNr = CfdpUtils.getUnsignedLongFromBuffer(buffer, sequenceNumberLength);
        destinationId = CfdpUtils.getUnsignedLongFromBuffer(buffer, entityIdLength);
    }

    public static int getDataLength(ByteBuffer buffer) {
        return buffer.getShort(1) & 0xFFFF;
    }

    protected void writeToBuffer(ByteBuffer buffer, int dataLength) {
        byte b = (byte) (0x20 | // version 2 (001)
                CfdpUtils.boolToByte(largeFile, 7) |
                CfdpUtils.boolToByte(!fileDirective, 3) |
                CfdpUtils.boolToByte(towardsSender, 4) |
                CfdpUtils.boolToByte(!acknowledged, 5) |
                CfdpUtils.boolToByte(withCrc, 6));

        buffer.put(b);
        buffer.putShort((short) dataLength);
        b = (byte) ((entityIdLength - 1 << 4) | (sequenceNumberLength - 1));
        buffer.put(b);
        buffer.put(CfdpUtils.longToBytesFixed(sourceId, entityIdLength));
        buffer.put(CfdpUtils.longToBytesFixed(sequenceNr, sequenceNumberLength));
        buffer.put(CfdpUtils.longToBytesFixed(destinationId, entityIdLength));
    }

    /**
     * 
     * @return the header length
     */
    public int getLength() {
        return 4 + entityIdLength + sequenceNumberLength + entityIdLength;
    }

    @Override
    public String toString() {
        return "CfdpHeader [fileDirective=" + fileDirective + ", towardsSender=" + towardsSender + ", acknowledged="
                + acknowledged + ", withCrc=" + withCrc + ", entityIdLength="
                + entityIdLength + ", sequenceNumberLength=" + sequenceNumberLength + ", sourceId=" + sourceId
                + ", destinationId=" + destinationId + ", sequenceNr=" + sequenceNr + ", largeFile=" + largeFile + "]";
    }

    public String toJson() {
        return " {\n"
                + "        fileDirective=" + fileDirective + ",\n"
                + "        towardsSender=" + towardsSender + ",\n"
                + "        acknowledged=" + acknowledged + ",\n"
                + "        withCrc=" + withCrc + ",\n"
                + "        entityIdLength=" + entityIdLength + ",\n"
                + "        sequenceNumberLength=" + sequenceNumberLength + ",\n"
                + "        sourceId=" + sourceId + ",\n"
                + "        destinationId=" + destinationId + ",\n"
                + "        sequenceNr=" + sequenceNr + ",\n"
                + "    }";
    }
}
