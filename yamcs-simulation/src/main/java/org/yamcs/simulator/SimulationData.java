package org.yamcs.simulator;

/**
 * Represents one tuple of simulated data.
 * Almost like a Map<String,String>. Provides some easy accessors.
 */
public class SimulationData {
    
    private String[] headers;
    private String[] values;

    public SimulationData(String[] headers, String[] values) {
        this.headers = headers;
        this.values = values;
    }
    
    public String getValue(String header) {
        for (int i = 0; i < headers.length; i++)
            if (headers[i].equals(header))
                return values[i];

        return null;
    }
}
