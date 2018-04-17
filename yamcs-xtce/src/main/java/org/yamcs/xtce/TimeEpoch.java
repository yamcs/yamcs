package org.yamcs.xtce;

import java.io.Serializable;

public class TimeEpoch implements Serializable {
    private static final long serialVersionUID = 1L;
    
   final CommonEpochs epoch;
   final String dateTime;
    public static enum CommonEpochs {TAI, J2000, UNIX, GPS};
    
    public TimeEpoch(CommonEpochs epoch) {
        this.epoch = epoch;
        this.dateTime = null;
    }
    
    public TimeEpoch(String dateTime) {
        this.epoch = null;
        this.dateTime = dateTime;
    }
    
}
