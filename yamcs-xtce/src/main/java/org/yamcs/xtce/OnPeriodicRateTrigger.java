package org.yamcs.xtce;

import java.io.Serializable;

public class OnPeriodicRateTrigger implements Serializable {
    private static final long serialVersionUID = -7880893090845503905L;
    private long fireRate; // in milliseconds
    
    public OnPeriodicRateTrigger(long fireRateInMilliseconds) {
        fireRate=fireRateInMilliseconds;
    }
    
    public long getFireRate() {
        return fireRate;
    }
    
    @Override
    public String toString() {
        return "fireRate:"+fireRate+"ms";
    }
}
