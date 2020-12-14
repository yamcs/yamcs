package org.yamcs.simulator;

import java.nio.ByteBuffer;

public class PowerData {

    public float timestamp;
    public int busStatus;
    public float busVoltage, busCurrent, systemCurrent;
    public float batteryVoltage1, batteryTemp1, batteryCapacity1;
    public float batteryVoltage2, batteryTemp2, batteryCapacity2;
    public float batteryVoltage3, batteryTemp3, batteryCapacity3;



    public static int size() {
        return 16;
    }

    public void fillPacket(ByteBuffer buffer) {
        buffer.put((byte) busStatus);
        buffer.put((byte) busVoltage);
        buffer.put((byte) busCurrent);
        buffer.put((byte) systemCurrent);
        buffer.put((byte) batteryVoltage1);
        buffer.put((byte) batteryTemp1);
        buffer.putShort((short) batteryCapacity1);
        buffer.put((byte) batteryVoltage2);
        buffer.put((byte) batteryTemp2);
        buffer.putShort((short) batteryCapacity2);
        buffer.put((byte) batteryVoltage3);
        buffer.put((byte) batteryTemp3);
        buffer.putShort((short) batteryCapacity3);
    }

    @Override
    public String toString() {
        return String.format("[PowerData]");
    }

}
