package org.yamcs.simulator.launchland;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

class CSVHandlerPower extends CSVHandler
{
	final static String csvName = "test_data/power.csv";

	Vector<PowerData> entries;
	UplinkInterface uplink;

	public CSVHandlerPower(UplinkInterface uplink) {
		this.uplink = uplink;
		loadCSV(csvName);
	}

	public CSVHandlerPower() {
		loadCSV(csvName);
	}

	void loadCSV(String filename)
	{
		entries = new Vector<PowerData>(100, 100);
		try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
			String line;
			in.readLine(); // skip column titles

			while ((line = in.readLine()) != null) {
				line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
				String[] parts = line.split(";");
							
				PowerData entry = new PowerData();

				entry.timestamp = new Float(parts[0]).floatValue();
				entry.busStatus = new Integer(parts[1]).intValue();
				entry.busVoltage = new Float(parts[2]).floatValue();
				entry.busCurrent = new Float(parts[3]).floatValue();
				entry.systemCurrent = new Float(parts[4]).floatValue();

				entry.batteryVoltage1 = new Float(parts[5]).floatValue();
				entry.batteryTemp1 = new Float(parts[6]).floatValue();
				entry.batteryCapacity1 = new Float(parts[7]).floatValue();

				entry.batteryVoltage2 = new Float(parts[8]).floatValue();
				entry.batteryTemp2 = new Float(parts[9]).floatValue();
				entry.batteryCapacity2 = new Float(parts[10]).floatValue();

				entry.batteryVoltage3 = new Float(parts[11]).floatValue();
				entry.batteryTemp3 = new Float(parts[12]).floatValue();
				//entry.batteryCapacity3 = new Float(parts[13]).floatValue();
				
				entries.add(entry);
			}
		} catch (IOException e) {
			System.out.println(e);
		}
		System.out.println("have "+entries.size()+" power data records");
	}

	@Override
    int getNumberOfEntries() {
		return entries.size();
	}

	@Override
    double getTimestampAtIndex(int index) {
		PowerData entry = entries.elementAt(index);
		return entry.timestamp;
	}

	@Override
    void processElement(int index) {
		PowerData entry = entries.elementAt(index);
		//entry.sendMavlinkPackets(uplink);
	}
}
