package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ManualActivity extends Activity {

    public ManualActivity(UUID id) {
        super(id);
    }

    public ManualActivity(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {
        protob.setType(TimelineItemType.MANUAL_ACTIVITY);
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.MANUAL_ACTIVITY.name());
    }
}
