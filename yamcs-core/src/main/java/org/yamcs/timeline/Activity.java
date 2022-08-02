package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.*;

import java.util.List;
import java.util.UUID;

import org.yamcs.protobuf.timeline.TimelineItemType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.protobuf.activities.ExecutionStatus;
import org.yamcs.protobuf.timeline.TimelineItem.Builder;
import org.yamcs.yarch.Tuple;

public abstract class Activity extends TimelineItem {
    List<Dependence> dependsOn;
    ExecutionStatus status = ExecutionStatus.PLANNED;

    String failureReason;

    // if the activity has started this records the time of the start
    protected long actualStart = TimeEncoding.INVALID_INSTANT;

    // if the activity has stopped (either successfully or with failure) this records the stop time
    protected long actualStop = TimeEncoding.INVALID_INSTANT;

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

        if (tuple.hasColumn(CNAME_ACTUAL_START)) {
            this.actualStart = tuple.getColumn(CNAME_ACTUAL_START);
        }

        if (tuple.hasColumn(CNAME_ACTUAL_STOP)) {
            this.actualStop = tuple.getColumn(CNAME_ACTUAL_STOP);
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

    public long getActualStart() {
        return actualStart;
    }

    public void setActualStart(long actualStart) {
        this.actualStart = actualStart;
    }

    public long getActualStop() {
        return actualStop;
    }

    public void setActualStop(long actualStop) {
        this.actualStop = actualStop;
    }

    static class Dependence {
        UUID id;
    }

    @Override
    protected void addToProto(boolean detail, Builder protob) {
        protob.setStatus(status);
        if (failureReason != null) {
            protob.setFailureReason(failureReason);
        }

        if (actualStart != TimeEncoding.INVALID_INSTANT) {
            protob.setActualStart(TimeEncoding.toProtobufTimestamp(actualStart));
        }
        if (actualStop != TimeEncoding.INVALID_INSTANT) {
            protob.setActualStop(TimeEncoding.toProtobufTimestamp(actualStop));
        }
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_STATUS, status.name());
        if (failureReason != null) {
            tuple.addColumn(CNAME_FAILURE_REASON, failureReason);
        }
        if (actualStart != TimeEncoding.INVALID_INSTANT) {
            tuple.addColumn(CNAME_ACTUAL_START, actualStart);
        }
        if (actualStop != TimeEncoding.INVALID_INSTANT) {
            tuple.addColumn(CNAME_ACTUAL_STOP, actualStop);
        }
    }

}
