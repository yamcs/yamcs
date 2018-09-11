package org.yamcs.simulation.simulator;

import java.nio.ByteBuffer;

public class PowerData {

    public float timestamp;
    public int busStatus;
    public float busVoltage, busCurrent, systemCurrent;
    public float batteryVoltage1, batteryTemp1, batteryCapacity1;
    public float batteryVoltage2, batteryTemp2, batteryCapacity2;
    public float batteryVoltage3, batteryTemp3, batteryCapacity3;

    public PowerData(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();

        busStatus = buffer.get(0);

        busVoltage = (float) buffer.get(1);
        busCurrent = (float) buffer.get(2);
        systemCurrent = (float) buffer.get(3);

        batteryVoltage1 = (float) buffer.get(4);
        batteryTemp1 = (float) buffer.get(5);
        batteryCapacity1 = (float) buffer.getShort(6);

        batteryVoltage2 = (float) buffer.get(8);
        batteryTemp2 = (float) buffer.get(9);
        batteryCapacity2 = (float) buffer.getShort(10);

        batteryVoltage3 = (float) buffer.get(12);
        batteryTemp3 = (float) buffer.get(13);
        batteryCapacity3 = (float) buffer.getShort(14);
    }

    public PowerData() {
    }

    public void fillPacket(CCSDSPacket packet, int bufferOffset) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.position(bufferOffset);
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
