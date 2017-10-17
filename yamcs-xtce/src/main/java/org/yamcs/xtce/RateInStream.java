package org.yamcs.xtce;

import java.io.Serializable;

/**
 * XTCE doc:
 *  Used in packaging to define the expected rate that any individual container will be in a Stream
 *  
 * DIFFERS_FROM_XTCE
 *  XTCE defines two types: perSecond and perContainerUpdate. I don't know what perContainerUpdate means.
 * 
 * In this class, 
 *  maxInterval means the maximum number of milliseconds in between two subsequent containers updates
 *  minInterval means the minimum number of milliseconds in between two subsequent containers updates
 *  
 *  both can be -1 meaning not defined
 *  
 *  maxInterval is used to set parameter expiration times for parameters extracted from this container.
 *  minInterval is currently not used 
 *  
 * @author nm
 *
 */
public class RateInStream implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private long maxInterval = -1;
    private long minInterval = -1;
    
    public RateInStream(long minInterval, long maxInterval) {
        this.maxInterval = maxInterval;
        this.minInterval = minInterval;
    }
    
    public long getMaxInterval() {
        return maxInterval;
    }
    
    public long getMinInterval() {
        return minInterval;
    }

    @Override
    public String toString() {
        return "RateInStream [maxInterval=" + maxInterval + ", minInterval="+ minInterval + "]";
    }

}
