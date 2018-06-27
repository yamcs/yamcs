package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Most time values are relative to another time e.g. seconds are relative to minutes, minutes are relative to hours.
 * This type is used to describe this relationship starting with the least significant time Parameter to and progressing
 * to the most significant time parameter.
 * 
 * @author nm
 *
 */
public class ReferenceTime  implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private ParameterInstanceRef offsetFrom = null;
    private TimeEpoch epoch = null;
    
    public ReferenceTime() {
        
    }

    public ReferenceTime(TimeEpoch epoch) {
        this.epoch = epoch;
    }
    
    public ReferenceTime(ParameterInstanceRef offsetFrom) {
        this.offsetFrom = offsetFrom;
    }
    
    public void setOffsetFrom(ParameterInstanceRef paramInstRef) {
        this.offsetFrom = paramInstRef;        
    }
    
    public ParameterInstanceRef getOffsetFrom() {
        return offsetFrom;
    }
   
    public TimeEpoch getEpoch() {
        return epoch;
    }

    public void setEpoch(TimeEpoch epoch) {
        this.epoch = epoch;
    }
    
    public String toString() {
        return "ReferenceTime["+(offsetFrom!=null ? "offsetFrom: [" + offsetFrom.toString()+"]" : "")
                + (epoch!=null ? epoch.toString() : "")+"]";
    }
}
