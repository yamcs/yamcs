package org.yamcs.simulator;

import java.net.*;
import java.io.*;
import java.util.Vector;
import java.util.Enumeration;

class CSVHandlerWaypoints extends CSVHandler
{
	class Waypoint
	{
		public double latitude, longitude, altitude;
		boolean reached;

		public String toString() {
			return String.format("[Waypoint lat=%.6f lon=%.6f alt=%.2fm reached=%d]", latitude, longitude, altitude, reached);
		}
	}


	UplinkInterface uplink;
	Vector<Waypoint> entries;
	int currentWaypoint;

	final static public double waypointReachedDistance = 400; // meters

	public CSVHandlerWaypoints(UplinkInterface uplink, String filename) {
		this.uplink = uplink;
		loadCSV(filename);
		currentWaypoint = 0;
	}

	public CSVHandlerWaypoints(UplinkInterface uplink) {
		this.uplink = uplink;
		currentWaypoint = 0;
	}

	void loadCSV(String filename)
	{
		entries = new Vector<Waypoint>(20, 20);
		try {
			BufferedReader in = new BufferedReader(new FileReader(filename));
			String line;
			in.readLine(); // skip column titles

			while ((line = in.readLine()) != null) {

				line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
				String[] parts = line.split(";");
				Waypoint entry = new Waypoint();

				entry.longitude = new Double(parts[1]).doubleValue();
				entry.latitude = new Double(parts[2]).doubleValue();
				entry.altitude = new Double(parts[3]).doubleValue();

				entries.add(entry);
			}

		} catch (IOException e) {
			System.out.println(e);
		}
		System.out.println("have "+entries.size()+" waypoint records");
	}

	public void run() {} // just in case

	int getNumberOfEntries() {
		return entries.size();
	}

	double getTimestampAtIndex(int index) {
		return 0;
	}

	void processElement(int index)
	{
		// to implement the abstract superclass
	}

/*	public void sendWaypoint(MavlinkMissionRequest request)
	{
		int index = request.seq;
		if (index >= 0 && index < entries.size()) {
			Waypoint entry = entries.elementAt(index);

			MavlinkMissionItem wp = new MavlinkMissionItem();
			wp.latitude = entry.latitude;
			wp.longitude = entry.longitude;
			wp.altitude = entry.altitude; // above MSL
			wp.seq = index;
			wp.autocontinue = 1;

			uplink.sendMessage(wp);
		}
	}*/

/*	public void sendWaypointCount()
	{
		MavlinkMissionCount count = new MavlinkMissionCount();
		count.count = (short)entries.size();
		uplink.sendMessage(count);
	}*/

	double distanceBetweenCoordinates(double lat_a, double lng_a, double lat_b, double lng_b)
	{
		double pk = (180/Math.PI);

		double a1 = lat_a / pk;
		double a2 = lng_a / pk;
		double b1 = lat_b / pk;
		double b2 = lng_b / pk;

		double t1 = Math.cos(a1)*Math.cos(a2)*Math.cos(b1)*Math.cos(b2);
		double t2 = Math.cos(a1)*Math.sin(a2)*Math.cos(b1)*Math.sin(b2);
		double t3 = Math.sin(a1)*Math.sin(b1);
		double tt = Math.acos(t1 + t2 + t3);

		return 6366000*tt;
	}

	public void checkForNextWaypoint(double lat, double lon, double alt)
	{
		boolean allReached = true;

		for (currentWaypoint = 0; currentWaypoint < entries.size(); ++currentWaypoint) {

			Waypoint entry = entries.elementAt(currentWaypoint);
			allReached &= entry.reached;
			if (entry.reached) continue;

			double d = distanceBetweenCoordinates(lat, lon, entry.latitude, entry.longitude);

			double altDiff = entry.altitude - alt;
			double dist = Math.sqrt(d*d + altDiff*altDiff);

			if (dist > waypointReachedDistance) continue;

			System.out.format("waypoint %d reached, ground-distance=%.2fm s3-alt=%.2f (wp-alt %.2f) actual-distance=%.2f\n", currentWaypoint, d, alt, entry.altitude, dist);
			entry.reached = true;

			// Send Mavlink messages

//			MavlinkMissionItemReached wpReached = new MavlinkMissionItemReached();
		//	wpReached.seq = currentWaypoint;
		//	uplink.sendMessage(wpReached);

//			MavlinkMissionCurrent wpCurrent = new MavlinkMissionCurrent();
		//wpCurrent.seq = currentWaypoint;
		//	uplink.sendMessage(wpCurrent);
		}

		// If all WPs are reached, flag all as "not reached".

		if (allReached) {
			for (Waypoint entry: entries)
				entry.reached = false;
		}
	}
}
