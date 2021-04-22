package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ActivityGroup extends Activity {

    public ActivityGroup(UUID id) {
        super(id);
    }

    ActivityGroup(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {
        protob.setType(TimelineItemType.ACTIVITY_GROUP);
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.ACTIVITY_GROUP.name());
    }
}
