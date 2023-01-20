package org.yamcs.cfdp.pdu;

import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.cfdp.FileDirective;

import java.nio.ByteBuffer;

public class KeepAlivePacket extends CfdpPacket implements FileDirective {

    private final long progress; // in octets

    public KeepAlivePacket(long progress, CfdpHeader header) {
        super(header);
        this.progress = progress;
    }

    public KeepAlivePacket(ByteBuffer buffer, CfdpHeader header) {
        super(header);
        this.progress = CfdpUtils.getUnsignedNumber(buffer, header.isLargeFile());
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(getFileDirectiveCode().getCode());
        CfdpUtils.writeUnsignedNumber(buffer, progress, header.isLargeFile());
    }

    @Override
    public int getDataFieldLength() {
        return 1 + (!header.isLargeFile() ? 4 : 8);
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.KEEP_ALIVE;
    }

    @Override
    public String toString() {
        return "KeepAlivePacket [progress=" + progress + "]";
    }
}
