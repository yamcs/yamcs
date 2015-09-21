package org.yamcs.simulator.launchland;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.UplinkInterface;

class CSVHandlerDHS {
    final static String csvName = "test_data/DHS.csv";
    private static final Logger log = LoggerFactory.getLogger(CSVHandlerDHS.class);

    Vector<DHSData> entries;
    UplinkInterface uplink;

    CSVHandlerDHS(UplinkInterface uplink) {
        this.uplink = uplink;
        loadCSV(csvName);
    }

    CSVHandlerDHS() {
        loadCSV(csvName);
    }

    protected void loadCSV(String filename) {
        entries = new Vector<>(100, 100);
        try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
            String line;
            in.readLine(); // skip column titles

            while ((line = in.readLine()) != null) {
                line = line.replace(',', '.'); // compatible to decimals with comma (e.g. 1,23)
                String[] parts = line.split(";");
                DHSData entry = new DHSData();

                entry.timestamp = new Float(parts[0]).floatValue();
                entry.primBusVoltage1 = new Float(parts[1]).floatValue();
                entry.primBusCurrent1 = new Float(parts[2]).floatValue();
                entry.primBusVoltage2 = new Float(parts[3]).floatValue();
                entry.primBusCurrent2 = new Float(parts[4]).floatValue();
                entry.secBusVoltage2 = new Float(parts[5]).floatValue();
                entry.secBusCurrent2 = new Float(parts[6]).floatValue();
                entry.secBusVoltage3 = new Float(parts[7]).floatValue();
                entry.secBusCurrent3 = new Float(parts[8]).floatValue();

                entries.add(entry);
            }
        } catch (IOException e) {
            System.out.println(e);
        }
        log.info("have " + entries.size() + " DHS data records");
    }

    int getNumberOfEntries() {
        return entries.size();
    }
}
