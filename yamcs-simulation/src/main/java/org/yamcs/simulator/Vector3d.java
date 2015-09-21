package org.yamcs.simulator;

public class Vector3d {
	public double x, y, z;

	public Vector3d(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public Vector3d(double degAngle, double length) {
		x = length*Math.cos(Math.toRadians(degAngle));
		y = length*Math.sin(Math.toRadians(degAngle));
		z = 0;
	}

	public double getLength() {
		return Math.sqrt(x*x + y*y + z*z);
	}
}
