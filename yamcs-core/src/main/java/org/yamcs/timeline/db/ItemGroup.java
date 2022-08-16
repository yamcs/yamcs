package org.yamcs.timeline.db;

import java.util.UUID;

import org.yamcs.protobuf.timeline.TimelineItem.Builder;
import org.yamcs.protobuf.timeline.TimelineItemType;
import org.yamcs.yarch.Tuple;

public class ItemGroup extends AbstractItem {

    public ItemGroup(UUID id) {
        super(TimelineItemType.ITEM_GROUP, id.toString());
    }

    public ItemGroup(Tuple tuple) {
        super(TimelineItemType.ITEM_GROUP, tuple);
    }

    @Override
    protected void addToProto(boolean detail, Builder protob) {
    }

    @Override
    protected void addToTuple(Tuple tuple) {
    }

}
