package org.yamcs.algorithms;
import java.util.HashMap;

public class OutputValueBinding extends ValueBinding {

    // raw value set by the algorithm it will be calibrated afterwards.
    public Object rawValue;
    
    // Value as set by algorithm
    public Object value;
    
    // Whether the value was updated. A user algorithm can optionally set
    // this to false, to prevent adding the output parameter to a delivery.
    public boolean updated = true;
    
    public HashMap<String, Object> values = new HashMap<String, Object>(); // Used for aggregate types

    @Override
    public String toString() {
        return "OutputValueBinding [rawValue=" + rawValue + ", value=" + value + ", updated=" + updated + "]";
    }
}
