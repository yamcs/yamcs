package org.yamcs.simulation.simulator.cfdp;

import java.nio.ByteBuffer;

public class EofPacket extends Packet {

    private ConditionCode conditionCode;
    private long fileChecksum;
    private long fileSize;
    private TLV faultLocation;

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

}
