package org.yamcs.simulator;

import java.nio.ByteBuffer;

public class RCSData {

    public float timestamp;
    public float H2TankFill, H2TankTemp, H2TankPressure, H2ValveTemp, H2ValvePressure;
    public float O2TankFill, O2TankTemp, O2TankPressure, O2ValveTemp, O2ValvePressure;
    public float TurbineTemp, TurbinePressure;

    public static int size() {
        return 36;
    }

    public void fillPacket(ByteBuffer buffer) {
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
