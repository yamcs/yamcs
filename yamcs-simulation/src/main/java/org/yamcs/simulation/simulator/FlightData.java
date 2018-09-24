package org.yamcs.simulation.simulator;

import java.nio.ByteBuffer;

public class FlightData {

    public final static double MACH_ONE = 340.3; // m/s

    public double latitude, longitude, altitude;
    public double heading, timestamp, phi, theta, psi;
    public double groundSpeed, verticalSpeed, mach, sinkRate, tas, cas, alpha, beta, loadFactor;

    public FlightData(CCSDSPacket packet) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        timestamp = buffer.getFloat(0);
        longitude = buffer.getFloat(4);
        latitude = buffer.getFloat(8);
        altitude = buffer.getFloat(12);
        heading = buffer.getFloat(16);
        alpha = buffer.getFloat(20);
        beta = buffer.getFloat(24);
        tas = buffer.getFloat(28);
        cas = buffer.getFloat(32);
        mach = buffer.getFloat(36);
        loadFactor = buffer.getFloat(40);
        sinkRate = buffer.getFloat(44);
        phi = buffer.getFloat(48);
        theta = buffer.getFloat(52);
        psi = buffer.getFloat(56);

        groundSpeed = -1;
        verticalSpeed = -1;
    }

    public FlightData() {
        groundSpeed = -1;
        verticalSpeed = -1;
    }

    public void fillPacket(CCSDSPacket packet, int bufferOffset) {
        ByteBuffer buffer = packet.getUserDataBuffer();
        buffer.position(bufferOffset);

        buffer.putFloat((float) timestamp);
        buffer.putFloat((float) longitude);
        buffer.putFloat((float) latitude);
        buffer.putFloat((float) altitude);
        buffer.putFloat((float) heading);
        buffer.putFloat((float) alpha);
        buffer.putFloat((float) beta);
        buffer.putFloat((float) tas);
        buffer.putFloat((float) cas);
        buffer.putFloat((float) mach);
        buffer.putFloat((float) loadFactor);
        buffer.putFloat((float) sinkRate);
        buffer.putFloat((float) phi);
        buffer.putFloat((float) theta);
        buffer.putFloat((float) psi);
    }

    public double getYaw() {
        return Math.toRadians(psi); // left/right turn
    }

    public double getPitch() {
        return Math.toRadians(theta); // nose up/down
    }

    public double getRoll() {
        return Math.toRadians(phi);
    }

    @Override
    public String toString() {
        return String.format("[CSVEntry lat=%.6f lon=%.6f alt=%.2fm time=%.3fs]", latitude, longitude, altitude,
                timestamp);
    }
}
