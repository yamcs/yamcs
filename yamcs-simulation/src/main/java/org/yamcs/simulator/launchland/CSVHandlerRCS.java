package org.yamcs.simulator.launchland;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

class CSVHandlerRCS extends CSVHandler
{
	final static String csvName = "test_data/RCS.csv";

	Vector<RCSData> entries;
	UplinkInterface uplink;

	CSVHandlerRCS(UplinkInterface uplink) {
		this.uplink = uplink;
		loadCSV(csvName);
	}

	CSVHandlerRCS() {
		loadCSV(csvName);
	}

	void loadCSV(String filename)
	{
		entries = new Vector<RCSData>(100, 100);
		try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
			String line;
			in.readLine(); // skip column titles

			while ((line = in.readLine()) != null) {

				line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
				String[] parts = line.split(";");
				RCSData entry = new RCSData();

				entry.timestamp = new Float(parts[0]).floatValue();

				entry.H2TankFill      = new Float(parts[1]).floatValue();
				entry.H2TankTemp      = new Float(parts[2]).floatValue();
				entry.H2TankPressure  = new Float(parts[3]).floatValue();
				entry.H2ValveTemp     = new Float(parts[4]).floatValue();
				entry.H2ValvePressure = new Float(parts[5]).floatValue();

				entry.O2TankFill      = new Float(parts[6]).floatValue();
				entry.O2TankTemp      = new Float(parts[7]).floatValue();
				entry.O2TankPressure  = new Float(parts[8]).floatValue();
				entry.O2ValveTemp     = new Float(parts[9]).floatValue();
				entry.O2ValvePressure = new Float(parts[10]).floatValue();

				entry.TurbineTemp     = new Float(parts[11]).floatValue();
				entry.TurbinePressure = new Float(parts[12]).floatValue();

				entries.add(entry);
			}
		} catch (IOException e) {
			System.out.println(e);
		}
		System.out.println("have "+entries.size()+" RHS data records");
	}

	@Override
    int getNumberOfEntries() {
		return entries.size();
	}

	@Override
    double getTimestampAtIndex(int index) {
		RCSData entry = entries.elementAt(index);
		return entry.timestamp;
	}

	@Override
    void processElement(int index) {
		RCSData entry = entries.elementAt(index);
	//	entry.sendMavlinkPackets(uplink);
	}
}
