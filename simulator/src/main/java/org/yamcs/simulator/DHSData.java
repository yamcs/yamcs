package org.yamcs.simulator;

import java.nio.ByteBuffer;

public class DHSData {

    float timestamp;
    float primBusVoltage1, primBusCurrent1;
    float primBusVoltage2, primBusCurrent2;
    float secBusVoltage2, secBusCurrent2;
    float secBusVoltage3, secBusCurrent3;

    public static int size() {
        return 9;
    }

    public void fillPacket(ByteBuffer buffer) {
        buffer.put((byte) primBusVoltage1);
        buffer.put((byte) primBusCurrent1);
        buffer.put((byte) primBusVoltage2);
        buffer.put((byte) primBusCurrent2);
        buffer.put((byte) secBusVoltage2);
        buffer.put((byte) secBusCurrent2);
        buffer.put((byte) secBusVoltage3);
        buffer.put((byte) secBusCurrent3);
        buffer.put((byte) 0);
    }

    @Override
    public String toString() {
        return String.format("[DHSData]");
    }

}
