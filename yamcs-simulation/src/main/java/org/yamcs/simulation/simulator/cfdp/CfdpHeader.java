package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class CfdpHeader {

    /*
    Header (Variable size):
        3 bits      = Version ('000')
        1 bit       = PDU type (0 = File Directive, 1 = File Data)
        1 bit       = Direction (0 = towards receiver, 1 = towards sender)
        1 bit       = Transmission mode (0 = acknowledged, 1 = unacknowledged)
        1 bit       = CRC flag (0 = CRC not present, 1 = CRC present)
        1 bit       = reserved ('0')
        16 bits     = PDU data field length (in octets)
        1 bit       = reserved ('0')
        3 bits      = length of entity IDs (number of octets in entity ID minus 1)
        1 bit       = reserved ('0')
        3 bits      = length of transaction sequence number (number of octets in sequence number minus 1)
        variable    = source entity id (UINT)
        variable    = transaction sequence number (UINT)
        variable    = destination entity id (UINT)
    */

    // header types
    private boolean fileDirective, towardsSender, acknowledged, withCrc;
    private int dataLength, entityIdLength, sequenceNumberLength;
    private Long sourceId, destinationId, sequenceNr;

    public CfdpHeader(boolean fileDirective, boolean towardsSender, boolean acknowledged, boolean withCrc, int dataLength,
            int entityIdLength, int sequenceNumberLength, long sourceId, long destinationId, long sequenceNumber) {
        this.fileDirective = fileDirective;
        this.towardsSender = towardsSender;
        this.acknowledged = acknowledged;
        this.withCrc = withCrc;
        this.dataLength = dataLength;
        this.entityIdLength = entityIdLength;
        this.sequenceNumberLength = sequenceNumberLength;
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.sequenceNr = sequenceNumber;
    }

    public CfdpHeader(ByteBuffer buffer) {
        readPduHeader(buffer);
    }

    public boolean isFileDirective() {
        return fileDirective;
    }

    public boolean withCrc() {
        return withCrc;
    }

    /*
     * Reads the header of the incoming buffer, which is assumed to be a complete PDU
     * Afterwards puts the buffer position right after the header 
     */
    private void readPduHeader(ByteBuffer buffer) {
        buffer.position(0);
        byte tempByte = buffer.get();
        fileDirective = !Utils.getBitOfByte(tempByte, 4);
        towardsSender = Utils.getBitOfByte(tempByte, 5);
        acknowledged = !Utils.getBitOfByte(tempByte, 6);
        withCrc = Utils.getBitOfByte(tempByte, 7);
        dataLength = Utils.getUnsignedShort(buffer);
        tempByte = buffer.get();
        entityIdLength = (tempByte >> 4) & 0x07;
        sequenceNumberLength = tempByte & 0x07;
        sourceId = Utils.getUnsignedLongFromBuffer(buffer, entityIdLength);
        sequenceNr = Utils.getUnsignedLongFromBuffer(buffer, sequenceNumberLength);
        destinationId = Utils.getUnsignedLongFromBuffer(buffer, entityIdLength);
    }

    protected void writeToBuffer(ByteBuffer buffer) {
        byte b = (byte) ((Utils.boolToByte(!fileDirective) << 4) |
                (Utils.boolToByte(towardsSender) << 3) |
                (Utils.boolToByte(!acknowledged) << 2) |
                (Utils.boolToByte(withCrc) << 1));
        buffer.put(b);
        buffer.putShort((short) dataLength);
        b = (byte) ((entityIdLength - 1 << 4) |
                (sequenceNumberLength - 1));
        buffer.put(b);
        buffer.put(Utils.longToBytes(sourceId, entityIdLength));
        buffer.put(Utils.longToBytes(sequenceNr, sequenceNumberLength));
        buffer.put(Utils.longToBytes(destinationId, entityIdLength));
    }

}
