package org.yamcs.simulator.launchland;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import org.yamcs.simulator.UplinkInterface;


public class CVSHandlerEPS {
	
	final static String csvName = "test_data/ESPLVPDU.csv";

	Vector<EpsLVPDUData> entries;
	UplinkInterface uplink;

	public CVSHandlerEPS(UplinkInterface uplink) {
		this.uplink = uplink;
		loadCSV(csvName);
	}

	public CVSHandlerEPS() {
		loadCSV(csvName);
	}
	
	void loadCSV(String filename) {
		entries = new Vector<>(100, 100);
		try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
			String line;
			in.readLine(); // skip column titles

			while ((line = in.readLine()) != null) {
				
				line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
				String[] parts = line.split(";");
							
				EpsLVPDUData entry = new EpsLVPDUData();

				entry.LVPDUStatus = new Integer(parts[0]).intValue();
				entry.LVPDUVoltage = new Float(parts[1]).floatValue();
				
				
				entries.add(entry);
			}
		} catch (IOException e) {
			System.out.println(e);
		}
		System.out.println("have "+entries.size()+" EPS LVPDU data records");
	}
	
    int getNumberOfEntries() {
		return entries.size();
	}
}
