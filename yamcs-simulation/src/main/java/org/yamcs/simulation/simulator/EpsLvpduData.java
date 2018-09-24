package org.yamcs.simulation.simulator;

import java.nio.ByteBuffer;

public class EpsLvpduData {

    float timestamp;
    public float LVPDUStatus;
    public float LVPDUVoltage;

    public EpsLvpduData(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        LVPDUStatus = (float) buffer.get(0);
        LVPDUVoltage = (float) buffer.get(1);
    }

    public EpsLvpduData() {
    }

    public void fillPacket(CCSDSPacket packet, int bufferOffset) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.position(bufferOffset);

        buffer.put((byte) LVPDUStatus);
        buffer.put((byte) LVPDUVoltage);
    }

    @Override
    public String toString() {
        return String.format("[EpsLVPDUData]");
    }
}
