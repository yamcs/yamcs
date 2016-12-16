package org.yamcs;

import org.yamcs.utils.TimeEncoding;

/**
 *  time interval where both ends can be open 
 *  
 **/
public class TimeInterval {
    private long start, stop;
    private boolean hasStart = false;
    private boolean hasStop = false;

    public TimeInterval(long start, long stop) {
        setStart(start);
        setStop(stop);
    }
    /**
     * Creates a TimeInterval with no start and no stop
     */
    public TimeInterval() {
    }
    /**
     * creates a TimeInterval with no start but with an stop
     * @return
     */
    public static TimeInterval openStart(long stop) {
        TimeInterval ti = new TimeInterval();
        ti.setStop(stop);
        return ti;
    }
  
    public boolean hasStart() {
        return hasStart;
    }
    
    public boolean hasStop() {
        return hasStop;
    }
    public void setStart(long start) {
        hasStart=true;
        this.start = start;
    }

    public long getStart() {
        return start;
    }

    public void setStop(long stop) {
        hasStop = true;
        this.stop = stop;
    }

    public long getStop() {
        return stop;
    }

    @Override
    public String toString() {
        StringBuilder sb=new StringBuilder();
        sb.append("(");
        if(hasStart) sb.append(start);
        sb.append(",");
        if(hasStop) sb.append(stop);
        sb.append(")");
        return sb.toString();
    }
    
    public String toStringEncoded() {
        StringBuilder sb=new StringBuilder();
        sb.append("(");
        if(hasStart) sb.append(TimeEncoding.toString(start));
        sb.append(",");
        if(hasStop) sb.append(TimeEncoding.toString(stop));
        sb.append(")");
        return sb.toString();
    }
}
