package org.yamcs.algorithms;

public class OutputValueBinding extends ValueBinding {
    
    // Value as set by algorithm. May still need to be calibrated afterwards.
    public Object value;
    
    // Whether the value was updated. A user algorithm can optionally set
    // this to false, to prevent adding the output parameter to a delivery.
    public boolean updated = true;
}
