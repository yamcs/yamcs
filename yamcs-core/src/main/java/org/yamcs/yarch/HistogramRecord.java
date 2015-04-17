package org.yamcs.yarch;

import java.util.Comparator;
import com.google.common.primitives.UnsignedBytes;

/**
 * Stores a record in the histogram database.
 *  
 * A record is composed of 
 *  - value
 *  - start
 *  - stop
 *  - num tuples

 * Note: this class has a natural ordering that is inconsistent with equals.
 * @author nm
 *
 */
public class HistogramRecord implements Comparable<HistogramRecord>{   
	final byte[] columnv;
    final long start;
    final long stop;
    final int num;
    
    static final Comparator<byte[]>comparator=UnsignedBytes.lexicographicalComparator();

    public HistogramRecord(byte[] columnv, long start, long stop, int num) {
        this.columnv=columnv;
        this.start=start;
        this.stop=stop;
        this.num=num;
    }
  
    @Override
    public int compareTo(HistogramRecord p) {
        if(start!=p.start)return Long.signum(start-p.start);
        return comparator.compare(columnv, p.columnv);
    }
    
    @Override
    public String toString() {
        return String.format("time:(%d,%d), nump: %d", start, stop, num);
    }
    
    public byte[] getColumnv() {
		return columnv;
	}

	public long getStart() {
		return start;
	}

	public long getStop() {
		return stop;
	}

	public int getNumTuples() {
		return num;
	}

}