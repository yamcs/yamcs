package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.utils.CfdpUtils;

public class FileDataPacket extends CfdpPacket {

    private long offset;
    private byte[] filedata;

    public FileDataPacket(byte[] data, long offset, CfdpHeader header) {
        super(header);
        this.offset = offset;
        this.filedata = data;
        finishConstruction();
    }

    public FileDataPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        this.offset = CfdpUtils.getUnsignedInt(buffer);
        this.filedata = new byte[buffer.limit() - buffer.position()];
        buffer.get(this.filedata);
    }

    public byte[] getData() {
        return filedata;
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        CfdpUtils.writeUnsignedInt(buffer, offset);
        buffer.put(filedata);
    }

    public static CfdpHeader createHeader(byte[] filedata) {
        // the '+4' originates from the length of the offset bytes
        return new CfdpHeader(false, false, false, false, filedata.length + 4, 1, 1, 123, 111, 246);
    }

    @Override
    protected int calculateDataFieldLength() {
        return 4 + this.filedata.length;
    }

}
