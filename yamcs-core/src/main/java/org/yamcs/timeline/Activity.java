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
        String dbstatus = tuple.getColumn(CNAME_STATUS);
        this.status = ExecutionStatus.valueOf(dbstatus);

        if (tuple.hasColumn(CNAME_FAILURE_REASON)) {
            this.failureReason = tuple.getColumn(CNAME_FAILURE_REASON);
        }
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    @Override
    protected void addToProto(boolean detail, Builder protob) {
        protob.setStatus(status);
        if (failureReason != null) {
            protob.setFailureReason(failureReason);
        }
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_STATUS, status.name());
        if (failureReason != null) {
            tuple.addColumn(CNAME_FAILURE_REASON, failureReason);
        }
    }

    static class Dependence {
        UUID id;
    }

}
