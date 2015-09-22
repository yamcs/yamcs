package org.yamcs.simulator.leospacecraft;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.simulator.SimulationData;

import com.csvreader.CsvReader;

/**
 * Currently loads everything in-mem. Should stream instead... 
 */
public class DataFeeder {
    
    private static final Logger log = LoggerFactory.getLogger(DataFeeder.class);
    private final static String sourceFile = "test_data/leo_spacecraft.csv.gz";
    private List<SimulationData> entries = new ArrayList<>();
    
    private int cursor = 0;
    private boolean loop;
    
    public DataFeeder(boolean loop) {
        this.loop = loop;
        CsvReader reader = null;
        try {
            reader = new CsvReader(new GZIPInputStream(new FileInputStream(sourceFile)),
                    ',', Charset.forName("UTF-8"));
            reader.readHeaders();
            String[] headers = reader.getHeaders();
            
            while (reader.readRecord()) {
                String[] vals = reader.getValues();
                entries.add(new SimulationData(headers, vals));
            }
        } catch (IOException e) {
            log.error("Could not load input file", e);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        log.info(entries.size() + " steps");
    }
    
    public void reset() {
        cursor = 0;
    }
    
    public SimulationData readNext() {
        if (cursor < entries.size()) {
            SimulationData record = entries.get(cursor);
            cursor++;
            return record;
        } else if (loop && !entries.isEmpty()) {
            reset();
            return readNext();
        } else {
            return null;
        }
    }
}
