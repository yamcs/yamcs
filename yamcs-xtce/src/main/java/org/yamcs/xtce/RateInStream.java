package org.yamcs.xtce;

import java.io.Serializable;

/**
 * XTCE doc:
 *  Used in packaging to define the expected rate that any individual container will be in a Stream
 *  
 * DIFFERS_FROM_XTCE
 *  XTCE defines two types: perSecond and perContainerUpdate. I don't know what perContainerUpdate means.
 * 
 * In this class, maxValue means the maximum number of milliseconds in between two subsequent containers.
 *  Nothing else is supported so far.
 *  
 * @author nm
 *
 */
public class RateInStream implements Serializable {
    private static final long serialVersionUID = 1L;
    
    long maxInterval;
    
    public RateInStream(long maxInterval) {
        this.maxInterval=maxInterval;
    }
    
    public long getMaxInterval() {
        return maxInterval;
    }
}
