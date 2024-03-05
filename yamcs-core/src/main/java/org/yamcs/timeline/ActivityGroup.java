package org.yamcs.timeline;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ActivityGroup extends TimelineActivity {

    public ActivityGroup(UUID id) {
        super(TimelineItemType.ACTIVITY_GROUP, id);
    }

    ActivityGroup(Tuple tuple) {
        super(TimelineItemType.ACTIVITY_GROUP, tuple);
    }
}
