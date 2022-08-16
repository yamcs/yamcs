package org.yamcs.timeline.db;

import java.util.UUID;

import org.yamcs.protobuf.timeline.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ActivityGroup extends Activity {

    public ActivityGroup(UUID id) {
        super(TimelineItemType.ACTIVITY_GROUP, id);
        this.autoRun = true;
    }

    ActivityGroup(Tuple tuple) {
        super(TimelineItemType.ACTIVITY_GROUP, tuple);
    }

}
