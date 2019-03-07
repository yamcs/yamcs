package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.utils.CfdpUtils;

public class EofPacket extends CfdpPacket {

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

        this.conditionCode = ConditionCode.readConditionCode(buffer);
        this.fileChecksum = CfdpUtils.getUnsignedInt(buffer);
        this.fileSize = CfdpUtils.getUnsignedShort(buffer);

        if (conditionCode != ConditionCode.NoError
                && conditionCode != ConditionCode.Reserved) {
            this.faultLocation = TLV.readTLV(buffer);
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(FileDirectiveCode.EOF.getCode());
        this.conditionCode.writeAsByteToBuffer(buffer);
        CfdpUtils.writeUnsignedInt(buffer, this.fileChecksum);
        CfdpUtils.writeUnsignedInt(buffer, this.fileSize);
        if (this.faultLocation != null) {
            faultLocation.writeToBuffer(buffer);
        }
    }

    @Override
    protected CfdpHeader createHeader() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected int calculateDataFieldLength() {
        return 9 // condition code (1) + checksum (4) + file size (4)
                + ((faultLocation != null) ? 2 + faultLocation.getValue().length : 0);
    }

}
