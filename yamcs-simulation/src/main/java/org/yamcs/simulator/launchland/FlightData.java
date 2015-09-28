package org.yamcs.simulator.launchland;

import java.nio.ByteBuffer;

import org.yamcs.simulator.CCSDSPacket;
import org.yamcs.simulator.Vector3d;

class FlightData {
	double latitude, longitude, altitude;
	double heading, timestamp, phi, theta, psi;
	double groundSpeed, verticalSpeed, mach, sinkRate, tas, cas, alpha, beta, loadFactor;

	final static double MACH_ONE = 340.3; // m/s

	FlightData(CCSDSPacket packet) {
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

	FlightData() {
		groundSpeed = -1;
		verticalSpeed = -1;
	}

	@Override
    public String toString() {
		return String.format("[CSVEntry lat=%.6f lon=%.6f alt=%.2fm time=%.3fs]", latitude, longitude, altitude, timestamp);
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset) {
		ByteBuffer buffer = packet.getUserDataBuffer();
		buffer.position(bufferOffset);

		buffer.putFloat((float)timestamp);
		buffer.putFloat((float)longitude);
		buffer.putFloat((float)latitude);
		buffer.putFloat((float)altitude);
		buffer.putFloat((float)heading);
		buffer.putFloat((float)alpha);
		buffer.putFloat((float)beta);
		buffer.putFloat((float)tas);
		buffer.putFloat((float)cas);
		buffer.putFloat((float)mach);
		buffer.putFloat((float)loadFactor);
		buffer.putFloat((float)sinkRate);
		buffer.putFloat((float)phi);
		buffer.putFloat((float)theta);
		buffer.putFloat((float)psi);
	}

	Vector3d getVelocity() {
		if (groundSpeed < 0) {
			Vector3d v = new Vector3d(heading + 90, mach*MACH_ONE);
			return v;
		}

		Vector3d v = new Vector3d(heading + 90, groundSpeed);
		v.z = verticalSpeed;
		return v;
	}

	double getYaw() { return Math.toRadians(psi); } // left/right turn
	double getPitch() { return Math.toRadians(theta); } // nose up/down
	double getRoll() { return Math.toRadians(phi); }
}
