package org.yamcs.timeline;

import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.yarch.Tuple;

import java.util.UUID;

import static org.yamcs.timeline.TimelineItemDb.CNAME_TYPE;

public class ItemGroup extends TimelineItem {

    public ItemGroup(UUID id) {
        super(id);
    }

    public ItemGroup(Tuple tuple) {
        super(tuple);
    }

    @Override
    protected void addToProto(Builder protob) {

    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_TYPE, TimelineItemType.ITEM_GROUP.name());
    }

}
