package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.CfdpTransactionId;
import org.yamcs.utils.CfdpUtils;

public class CfdpHeader {

    /*
     * Header (Variable size):
     * 3 bits = Version ('000')
     * 1 bit = PDU type (0 = File Directive, 1 = File Data)
     * 1 bit = Direction (0 = towards receiver, 1 = towards sender)
     * 1 bit = Transmission mode (0 = acknowledged, 1 = unacknowledged)
     * 1 bit = CRC flag (0 = CRC not present, 1 = CRC present)
     * 1 bit = reserved ('0')
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
    private boolean fileDirective, towardsSender, acknowledged, withCrc;
    private int dataLength = -1;
    private int entityIdLength, sequenceNumberLength;
    private Long sourceId, destinationId, sequenceNr;

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

    public CfdpHeader(boolean fileDirective, boolean towardsSender, boolean acknowledged, boolean withCrc,
            int datalength,
            int entityIdLength, int sequenceNumberLength, long sourceId, long destinationId, long sequenceNumber) {
        this(fileDirective, towardsSender, acknowledged, withCrc, entityIdLength, sequenceNumberLength, sourceId,
                destinationId, sequenceNumber);
        this.dataLength = datalength;
    }

    public void setDataLength(int datalength) {
        this.dataLength = datalength;
    }

    public CfdpHeader(ByteBuffer buffer) {
        readPduHeader(buffer);
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
        dataLength = CfdpUtils.getUnsignedShort(buffer);
        tempByte = buffer.get();
        entityIdLength = ((tempByte >> 4) & 0x07) + 1;
        sequenceNumberLength = (tempByte & 0x07) + 1;
        sourceId = CfdpUtils.getUnsignedLongFromBuffer(buffer, entityIdLength);
        sequenceNr = CfdpUtils.getUnsignedLongFromBuffer(buffer, sequenceNumberLength);
        destinationId = CfdpUtils.getUnsignedLongFromBuffer(buffer, entityIdLength);
    }

    protected void writeToBuffer(ByteBuffer buffer) {
        if (this.dataLength == -1) {
            throw new IllegalStateException("CFDP header needs a valid 'PDU Data field length' value.");
        }
        byte b = (byte) ((CfdpUtils.boolToByte(!fileDirective) << 4) |
                (CfdpUtils.boolToByte(towardsSender) << 3) |
                (CfdpUtils.boolToByte(!acknowledged) << 2) |
                (CfdpUtils.boolToByte(withCrc) << 1));
        buffer.put(b);
        buffer.putShort((short) dataLength);
        b = (byte) ((entityIdLength - 1 << 4) |
                (sequenceNumberLength - 1));
        buffer.put(b);
        buffer.put(CfdpUtils.longToBytes(sourceId, entityIdLength));
        buffer.put(CfdpUtils.longToBytes(sequenceNr, sequenceNumberLength));
        buffer.put(CfdpUtils.longToBytes(destinationId, entityIdLength));
    }

    @Override
    public String toString() {
        return "CfdpHeader [fileDirective=" + fileDirective + ", towardsSender=" + towardsSender + ", acknowledged="
                + acknowledged + ", withCrc=" + withCrc + ", dataLength=" + dataLength + ", entityIdLength="
                + entityIdLength + ", sequenceNumberLength=" + sequenceNumberLength + ", sourceId=" + sourceId
                + ", destinationId=" + destinationId + ", sequenceNr=" + sequenceNr + "]";
    }

    public int getDataLength() {
        return dataLength;
    }

    /**
     * 
     * @return the header length
     */
    public int getLength() {
        return 4 + entityIdLength + sequenceNumberLength + entityIdLength;
    }

}
