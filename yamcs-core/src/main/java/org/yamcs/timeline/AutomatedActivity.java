package org.yamcs.timeline;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

import java.util.UUID;

import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;

public class AutomatedActivity extends Activity {

    public AutomatedActivity(UUID id) {
        super(id);
    }

    AutomatedActivity(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {
        // TODO Auto-generated method stub

    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.AUTO_ACTIVITY.name());
    }

}
