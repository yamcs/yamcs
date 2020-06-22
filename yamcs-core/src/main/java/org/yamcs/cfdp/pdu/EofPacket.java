package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.FileDirective;
import org.yamcs.cfdp.CfdpUtils;

public class EofPacket extends CfdpPacket implements FileDirective {

    private ConditionCode conditionCode;
    private long fileChecksum;
    private long fileSize;
    private TLV faultLocation = null;

    public EofPacket(ConditionCode code, long checksum, long filesize, TLV faultLocation, CfdpHeader header) {
        super(header);
        this.conditionCode = code;
        this.fileChecksum = checksum;
        this.fileSize = filesize;
        if (this.conditionCode != ConditionCode.NoError) {
            this.faultLocation = faultLocation;
        }
        finishConstruction();
    }

    public EofPacket(ByteBuffer buffer, CfdpHeader header) {
        super(buffer, header);

        byte temp = buffer.get();
        this.conditionCode = ConditionCode.readConditionCode(temp);
        this.fileChecksum = CfdpUtils.getUnsignedInt(buffer);
        this.fileSize = CfdpUtils.getUnsignedInt(buffer);

        if (conditionCode != ConditionCode.NoError
                && conditionCode != ConditionCode.Reserved) {
            this.faultLocation = TLV.readTLV(buffer);
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(getFileDirectiveCode().getCode());
        this.conditionCode.writeAsByteToBuffer(buffer);
        CfdpUtils.writeUnsignedInt(buffer, this.fileChecksum);
        CfdpUtils.writeUnsignedInt(buffer, this.fileSize);
        if (this.faultLocation != null) {
            faultLocation.writeToBuffer(buffer);
        }
    }

    @Override
    protected int calculateDataFieldLength() {
        return 10 // condition code (1) + checksum (4) + file size (4)
                + ((faultLocation != null) ? 2 + faultLocation.getValue().length : 0);
    }

    public ConditionCode getConditionCode() {
        return conditionCode;
    }
    
    public long getFileChecksum() {
        return this.fileChecksum;
    }
    
    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.EOF;
    }

    @Override
    public String toString() {
        return "EofPacket [conditionCode=" + conditionCode + ", fileChecksum=" + fileChecksum + ", fileSize=" + fileSize
                + ", faultLocation=" + faultLocation + "]";
    }

}
