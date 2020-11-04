package org.yamcs.timeline;

public abstract class ItemSource {
    String name;
    
    /**
     * Get all the timeline items overlapping with the [start, stop) interval
     * 
     * @param start
     * @param stop
     * @return
     */
    abstract TimelineItem getItems(long start, long stop);
}
