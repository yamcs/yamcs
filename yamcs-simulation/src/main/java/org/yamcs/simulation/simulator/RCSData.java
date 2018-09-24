package org.yamcs.simulation.simulator;

import java.nio.ByteBuffer;

public class RCSData {

    public float timestamp;
    public float H2TankFill, H2TankTemp, H2TankPressure, H2ValveTemp, H2ValvePressure;
    public float O2TankFill, O2TankTemp, O2TankPressure, O2ValveTemp, O2ValvePressure;
    public float TurbineTemp, TurbinePressure;

    public RCSData(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();

        H2TankFill = buffer.getFloat(0);
        O2TankFill = buffer.getFloat(4);
        H2TankTemp = (float) buffer.getShort(8);
        O2TankTemp = (float) buffer.getShort(10);

        H2TankPressure = buffer.getFloat(12);
        O2TankPressure = buffer.getFloat(16);
        H2ValveTemp = (float) buffer.getShort(20);
        O2ValveTemp = (float) buffer.getShort(22);

        H2ValvePressure = buffer.getFloat(24);
        O2ValvePressure = buffer.getFloat(28);
    }

    public RCSData() {
    }

    public void fillPacket(CCSDSPacket packet, int bufferOffset) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.position(bufferOffset);

        buffer.putFloat(H2TankFill);
        buffer.putFloat(O2TankFill);
        buffer.putShort((short) H2TankTemp);
        buffer.putShort((short) O2TankTemp);
        buffer.putFloat(H2TankPressure);
        buffer.putFloat(O2TankPressure);
        buffer.putShort((short) H2ValveTemp);
        buffer.putShort((short) O2ValveTemp);
        buffer.putFloat(H2ValvePressure);
        buffer.putFloat(O2ValvePressure);
        buffer.putShort((short) 0);
        buffer.putShort((short) 0);
    }

    @Override
    public String toString() {
        return String.format("[RCSData]");
    }
}
