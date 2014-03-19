package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Base type for alarms
 */
public abstract class AlarmType implements Serializable {
    
    private static final long serialVersionUID = 7443202826018275789L;
    
    private int minViolations = 1;
    
    public int getMinViolations() {
        return minViolations;
    }
    
    public void setMinViolations(int minViolations) {
        this.minViolations=minViolations;
    }
}
