package org.yamcs.timeline;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class AutomatedActivity extends Activity {

    public AutomatedActivity(UUID id) {
        super(TimelineItemType.AUTO_ACTIVITY, id);
    }

    AutomatedActivity(Tuple tuple) {
        super(TimelineItemType.AUTO_ACTIVITY, tuple);
    }

}
