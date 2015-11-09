package org.yamcs.xtce;

/**
 * An abstract block of data; used as the base type for more specific container types
 * @author nm
 *
 */
public abstract class Container extends NameDescription {
    private static final long serialVersionUID = 200706051148L;

    /*DIFFERS_FROM_XTCE XTCE does not specify the size of a container 
     *  (but sometimes it may be derived by looking at the parameters inside)
     *  
     * Yamcs uses it when dealing with containers that are part of other containers, to speed up the processing of the 
     * parameter subsequent to this container. 
     * If specified, means that this container will ALWAYS have this size
     */
    protected int sizeInBits=-1;
    
    //expected rate
    RateInStream rate=null;

    Container(String name) {
        super(name);
    }
    
    public void setSizeInBits(int sizeInBits) {
        this.sizeInBits = sizeInBits;
    }
    
    public int getSizeInBits() {
        return sizeInBits;
    }

    public void setRateInStream(RateInStream r) {
        this.rate=r;
    }
    
    public RateInStream getRateInStream() {
        return rate;
    }
}
