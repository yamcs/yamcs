package org.yamcs.timeline;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

import java.util.UUID;

import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;

public class ActivityGroup extends Activity {

    public ActivityGroup(UUID id) {
        super(id);
    }

    ActivityGroup(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {

    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.ACTIVITY_GROUP.name());
    }
}
