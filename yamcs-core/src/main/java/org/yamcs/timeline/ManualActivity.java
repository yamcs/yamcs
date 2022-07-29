package org.yamcs.timeline;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ManualActivity extends Activity {
    public ManualActivity(UUID id) {
        super(TimelineItemType.MANUAL_ACTIVITY, id);
    }

    public ManualActivity(Tuple tuple) {
        super(TimelineItemType.MANUAL_ACTIVITY, tuple);
    }

}
