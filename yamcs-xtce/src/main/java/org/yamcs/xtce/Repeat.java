package org.yamcs.xtce;

import java.io.Serializable;

/**
 * Hold a structure that can be repeated X times, where X is the Count
 * @author nm
 *
 */
public class Repeat implements Serializable {
	private static final long serialVersionUID=200706111239L;
	/**
	 * Value (either fixed or dynamic) that contains the count of repeated structures.
	 */
	IntegerValue count;
	/**
	 * Indicates the distance between repeating entries (the last bit of one entry to the start bit of the next entry)
	 */
	private int offsetSizeInBits=0;
	
	

    public void setOffsetSizeInBits(int offsetSizeInBits) {
        this.offsetSizeInBits = offsetSizeInBits;
    }

    public int getOffsetSizeInBits() {
        return offsetSizeInBits;
    }

    public void setCount(IntegerValue count) {
        this.count = count;
    }

    public IntegerValue getCount() {
        return count;
    }
    
    @Override
    public String toString() {
        return "offsetSizeInBits: "+getOffsetSizeInBits()+", count: "+getCount();
    }
}
