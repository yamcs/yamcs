package org.yamcs.timeline;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

/**
 * Used for displaying day/night, TDRS, GS visibility, milestones (e.g. launch, a deadline), general items covering
 * large time intervals for which no specific action is taken (e.g. mission phase).
 * <p>
 * In the timeline widget also enumerated telemetry (e.g. ON/OFF, NOMINAL/STAND_BY) and packet histograms are displayed
 * as events.
 * 
 *
 */
public class TimelineEvent extends TimelineItem {

    public TimelineEvent(String id) {
        super(TimelineItemType.EVENT, id);
    }

    public TimelineEvent(Tuple tuple) {
        super(TimelineItemType.EVENT, tuple);
    }

    @Override
    protected void addToProto(boolean detail, Builder protob) {
    }

    @Override
    protected void addToTuple(Tuple tuple) {
    }

    @Override
    public String toString() {
        return "TimelineEvent [id=" + id + ", start=" + start + ", duration=" + duration + ", relativeItemUuid="
                + relativeItemUuid + ", relativeStart=" + relativeStart + ", groupUuid=" + groupUuid + ", source="
                + source + ", name=" + name + ", tooltip=" + tooltip + ", description=" + description + ", tags=" + tags
                + "]";
    }
}
