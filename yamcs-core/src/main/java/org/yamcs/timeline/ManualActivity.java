package org.yamcs.timeline;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.yarch.Tuple;
import static org.yamcs.timeline.TimelineItemDb.*;

public class ManualActivity extends Activity {

    public ManualActivity(UUID id) {
        super(id);
    }

    public ManualActivity(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.MANUAL_ACTIVITY.name());
    }

}
