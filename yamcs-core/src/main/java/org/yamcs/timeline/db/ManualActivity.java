package org.yamcs.timeline.db;

import java.util.UUID;

import org.yamcs.protobuf.timeline.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ManualActivity extends Activity {
    public ManualActivity(UUID id) {
        super(TimelineItemType.MANUAL_ACTIVITY, id);
        this.autoRun = false;
    }

    public ManualActivity(Tuple tuple) {
        super(TimelineItemType.MANUAL_ACTIVITY, tuple);
    }

}
