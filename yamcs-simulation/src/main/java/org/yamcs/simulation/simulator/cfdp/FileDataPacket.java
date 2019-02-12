package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class FileDataPacket extends Packet {

    private long offset;
    private byte[] filedata;

    public FileDataPacket(ByteBuffer buffer, Header header) {
        super(buffer, header);

        this.offset = Utils.getUnsignedInt(buffer);
        this.filedata = new byte[buffer.limit() - buffer.position()];
        buffer.get(this.filedata);

    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        super.writeCFDPPacket(buffer);
        Utils.writeUnsignedInt(buffer, offset);
        buffer.put(filedata);
    }

}
