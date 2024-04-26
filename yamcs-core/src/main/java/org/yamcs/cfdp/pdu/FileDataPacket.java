package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.CfdpUtils;

/**
 * Structure is
 * 
 * <pre>
 * offset - 4 bytes
 * file data - variable
 * </pre>
 *
 * The version 5 of the CFDP standard introduces some metadata. This is not supported.
 */
public class FileDataPacket extends CfdpPacket {
    public static final int OFFSET_SIZE = 4;

    private long offset;
    private byte[] filedata;

    public FileDataPacket(byte[] fileData, long offset, CfdpHeader header) {
        super(header);
        this.offset = offset;
        this.filedata = fileData;
    }

    public FileDataPacket(ByteBuffer buffer, CfdpHeader header) {
        super(header);

        this.offset = CfdpUtils.getUnsignedInt(buffer);
        int fileDataSize = CfdpHeader.getDataLength(buffer) - OFFSET_SIZE;

        if (header.withCrc()) {
            fileDataSize -= 2;
        }
        this.filedata = new byte[fileDataSize];
        buffer.get(this.filedata);
    }

    public long getOffset() {
        return this.offset;
    }

    public byte[] getData() {
        return this.filedata;
    }

    public long getEndOffset() {
        return offset + filedata.length;
    }

    public int getLength() {
        return this.filedata.length;
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        CfdpUtils.writeUnsignedInt(buffer, offset);
        buffer.put(filedata);
    }

    @Override
    public int getDataFieldLength() {
        return OFFSET_SIZE + this.filedata.length;
    }

    @Override
    public String toString() {
        return "FileDataPacket [offset=" + offset + ", length=" + filedata.length + "]";
    }

}
