package org.yamcs.simulator;

import java.nio.ByteBuffer;

public class EpsLvpduData {

    float timestamp;
    public float LVPDUStatus;
    public float LVPDUVoltage;

    public static int size() {
        return 2;
    }

    public void fillPacket(ByteBuffer buffer) {
        buffer.put((byte) LVPDUStatus);
        buffer.put((byte) LVPDUVoltage);
    }

    @Override
    public String toString() {
        return String.format("[EpsLVPDUData]");
    }

}
