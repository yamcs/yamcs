package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class AutomatedActivity extends Activity {

    public AutomatedActivity(UUID id) {
        super(id);
    }

    AutomatedActivity(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {
        protob.setType(TimelineItemType.AUTO_ACTIVITY);
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.AUTO_ACTIVITY.name());
    }
}
