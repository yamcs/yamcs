package org.yamcs.simulator;

import java.nio.ByteBuffer;

class FlightData
{
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

	public String toString() {
		return String.format("[CSVEntry lat=%.6f lon=%.6f alt=%.2fm time=%.3fs]", latitude, longitude, altitude, timestamp);
	}

	void fillPacket(CCSDSPacket packet, int bufferOffset)
	{
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

/*	void sendMavlinkPackets(UplinkInterface uplink)
	{
		//
		// Global position
		//

		Vector3d v;

		MavlinkGlobalPosition pos = new MavlinkGlobalPosition();
		pos.lat = latitude;
		pos.lon = longitude;
		pos.rel_alt = altitude; // above ground
		pos.alt = altitude; // above MSL
		pos.hdg = heading; //MavlinkGlobalPosition.HDG_UNKNOWN;

		v = getVelocity();
		pos.vx = v.x;
		pos.vy = v.y;
		pos.vz = v.z;

		uplink.sendMessage(pos);


		//
		// GPS
		//

		MavlinkGPSRaw gps = new MavlinkGPSRaw();
		gps.lat = latitude;
		gps.lon = longitude;
		gps.alt = altitude;

		v = getVelocity();
		gps.vel = v.getLength();

		uplink.sendMessage(gps);


		//
		// Attitude
		//

		MavlinkAttitude att = new MavlinkAttitude();
		att.yaw = getYaw();
		att.pitch = getPitch();
		att.roll = getRoll();
		uplink.sendMessage(att);


		//
		// VFR (Visual Flight Rules) HUD
		//

		MavlinkVFRHUD hud = new MavlinkVFRHUD();
		hud.altitude = altitude; // above MSL
		hud.heading = heading;
		hud.throttle = 50 + (int)(Math.random()*50);

		v = getVelocity();
		hud.airspeed = v.getLength();
		hud.groundspeed = hud.airspeed;

		uplink.sendMessage(hud);

	}*/
}
