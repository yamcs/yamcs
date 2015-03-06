package org.yamcs.simulator;

import java.net.*;
import java.io.*;

class HeartbeatThread extends Thread
{
	UplinkInterface uplink;

	public HeartbeatThread(UplinkInterface uplink) {
		this.uplink = uplink;
	}

	public void run()
	{
		try {

			for (;;) {
				//sendHeartbeat();
				Thread.sleep(1000);
			}

//		} catch (IOException e) {
//			System.out.println(e);
		} catch (InterruptedException e) {
//			System.out.println(e);
		}

		System.out.println("HeartbeatThread ended");
	}

/*	void sendHeartbeat() //throws IOException
	{
		//
		// Send Heartbeat
		//

		MavlinkHeartbeat hb = new MavlinkHeartbeat();
		hb.system_status = uplink.haveAOS() ? MavlinkHeartbeat.MAV_STATE_ACTIVE : MavlinkHeartbeat.MAV_STATE_LOS;
		hb.custom_mode = MavlinkHeartbeat.MAV_MODE_FLAG_AUTO_ENABLED | MavlinkHeartbeat.MAV_MODE_FLAG_SAFETY_ARMED;
		uplink.sendMessageAndIgnoreLOS(hb);

		//
		// MavlinkSysStatus
		//

		MavlinkSysStatus status = new MavlinkSysStatus();
		status.voltage_battery = (float)(Math.random()*0.4 + 11.8);
		uplink.sendMessage(status);
	}

	void processHeartbeat(MavlinkHeartbeat message) throws IOException
	{
	}*/
}
