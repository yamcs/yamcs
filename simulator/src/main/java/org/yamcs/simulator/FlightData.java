package org.yamcs.simulator;

import java.nio.ByteBuffer;

public class FlightData {

    public final static double MACH_ONE = 340.3; // m/s

    public double latitude, longitude, altitude;
    public double heading, timestamp, phi, theta, psi;
    public double groundSpeed, verticalSpeed, mach, sinkRate, tas, cas, alpha, beta, loadFactor;

    public FlightData() {
        groundSpeed = -1;
        verticalSpeed = -1;
    }

    public void fillPacket(ByteBuffer buffer) {
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

    public static int size() {
        return 60;
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
