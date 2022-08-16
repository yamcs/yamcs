package org.yamcs.timeline;

public interface TimelineItem {
    /**
     * get the item identifier
     */
    public String getId();

    public long getStart();

    /**
     * 
     * @return duration in milliseconds
     */
    public long getDuration();

    /**
     * transform the item to protobuf
     * 
     * @param detail
     * @return
     */
    public org.yamcs.protobuf.timeline.TimelineItem toProtoBuf(boolean detail);

}
