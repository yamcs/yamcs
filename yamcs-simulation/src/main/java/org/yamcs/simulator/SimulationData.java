package org.yamcs.simulator;

/**
 * Represents one tuple of simulated data.
 * Almost like a Map&lt;String,String&gt;. Provides some easy accessors.
 */
public class SimulationData {
    
    private String[] headers;
    private String[] values;

    public SimulationData(String[] headers, String[] values) {
        this.headers = headers;
        this.values = values;
    }
    
    public String getString(String header) {
        for (int i = 0; i < headers.length; i++)
            if (headers[i].equals(header))
                return values[i];

        return null;
    }
    
    public int getInt(String header) {
        return Integer.parseInt(getString(header));
    }
    
    public float getFloat(String header) {
        return Float.parseFloat(getString(header));
    }
    
    public void setString(String header, String value) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equals(header)) {
                values[i] = value;
                return;
            }
        }
        throw new IllegalArgumentException("Unexpected header " + header);
    }
    
    public void setInt(String header, int value) {
        setString(header, Integer.toString(value));
    }
    
    public void setFloat(String header, float value) {
        setString(header, Float.toString(value));
    }
}
