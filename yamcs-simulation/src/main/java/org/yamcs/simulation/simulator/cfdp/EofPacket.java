package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class EofPacket extends Packet {

    private ConditionCode conditionCode;
    private long fileChecksum;
    private long fileSize;
    private TLV faultLocation = null;

    public EofPacket(ByteBuffer buffer, Header header) {
        super(buffer, header);

        this.conditionCode = ConditionCode.readConditionCode(buffer);
        this.fileChecksum = Utils.getUnsignedInt(buffer);
        this.fileSize = Utils.getUnsignedShort(buffer);

        if (conditionCode != ConditionCode.NoError
                && conditionCode != ConditionCode.Reserved) {
            this.faultLocation = TLV.readTLV(buffer);
        }
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        super.writeCFDPPacket(buffer);
        this.conditionCode.writeAsByteToBuffer(buffer);
        Utils.writeUnsignedInt(buffer, this.fileChecksum);
        Utils.writeUnsignedInt(buffer, this.fileSize);
        if (this.faultLocation != null) {
            faultLocation.writeToBuffer(buffer);
        }
    }

}
