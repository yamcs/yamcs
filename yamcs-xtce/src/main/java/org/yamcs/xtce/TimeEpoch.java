package org.yamcs.xtce;

import java.io.Serializable;

public class TimeEpoch implements Serializable {
    private static final long serialVersionUID = 2L;
    public static enum CommonEpochs {TAI, J2000, UNIX, GPS};
     
    private final CommonEpochs epoch;
    private final String dateTime;
    
    public TimeEpoch(CommonEpochs epoch) {
        this.epoch = epoch;
        this.dateTime = null;
    }
    
    public TimeEpoch(String dateTime) {
        this.epoch = null;
        this.dateTime = dateTime;
    }

    public CommonEpochs getCommonEpoch() {
        return epoch;
    }

    public String getDateTime() {
        return dateTime;
    }
}
