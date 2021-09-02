package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;

import java.util.UUID;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ItemGroup extends TimelineItem {

    public ItemGroup(UUID id) {
        super(id.toString());
    }

    public ItemGroup(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {
        protob.setType(TimelineItemType.ITEM_GROUP);
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.ITEM_GROUP.name());
    }
}
