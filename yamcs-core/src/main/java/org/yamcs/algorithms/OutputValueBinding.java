package org.yamcs.algorithms;

public class OutputValueBinding extends ValueBinding {

    // raw value set by the algorithm it will be calibrated afterwards.
    public Object rawValue;
    
    // Value as set by algorithm
    public Object value;
    
    // Whether the value was updated. A user algorithm can optionally set
    // this to false, to prevent adding the output parameter to a delivery.
    public boolean updated = true;

    @Override
    public String toString() {
        return "OutputValueBinding [rawValue=" + rawValue + ", value=" + value + ", updated=" + updated + "]";
    }
}
