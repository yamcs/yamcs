package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.*;

import java.util.List;
import java.util.UUID;

import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.yarch.Tuple;

public abstract class Activity extends TimelineItem {
    List<Dependence> dependsOn;
    ExecutionStatus status = ExecutionStatus.PLANNED;

    String failureReason;

    public Activity(TimelineItemType type, UUID id) {
        super(type, id.toString());
    }

    Activity(TimelineItemType type, Tuple tuple) {
        super(type, tuple);
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    @Override
    protected void addToProto(Builder protob) {
        protob.setStatus(status);
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_STATUS, status.name());
    }

    static class Dependence {
        UUID id;
    }
}
