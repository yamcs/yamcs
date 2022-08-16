package org.yamcs.timeline.db;

import static org.yamcs.timeline.db.TimelineItemDb.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.yamcs.protobuf.timeline.TimelineItemType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.dbproto.timeline.Dependency;
import org.yamcs.protobuf.activities.ExecutionStatus;
import org.yamcs.protobuf.timeline.TimelineItem.Builder;
import org.yamcs.yarch.Tuple;

public abstract class Activity extends AbstractItem {
    List<Dependency> dependencies;
    ExecutionStatus status = ExecutionStatus.PLANNED;

    String failureReason;

    // if the activity has started this records the time of the start
    protected long actualStart = TimeEncoding.INVALID_INSTANT;

    // if the activity has stopped (either successfully or with failure) this records the stop time
    protected long actualEnd = TimeEncoding.INVALID_INSTANT;

    /**
     * if true, this activity can start before its start time, once all its dependencies are satisfied
     */
    boolean allowEarlyStart;

    /**
     * If true, this activity will start automatically once its time is due
     */
    boolean autoRun;

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

        if (tuple.hasColumn(CNAME_ACTUAL_END)) {
            this.actualEnd = tuple.getColumn(CNAME_ACTUAL_END);
        }

        if (tuple.hasColumn(CNAME_ALLOW_EARLY_START)) {
            this.allowEarlyStart = tuple.getBooleanColumn(CNAME_ALLOW_EARLY_START);
        }

        if (tuple.hasColumn(CNAME_STATUS)) {
            String stname = tuple.getColumn(CNAME_STATUS);
            this.status = ExecutionStatus.valueOf(stname);
        }

        if (tuple.hasColumn(CNAME_AUTO_RUN)) {
            this.autoRun = tuple.getBooleanColumn(CNAME_AUTO_RUN);
        }

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

    public long getActualStart() {
        return actualStart;
    }

    public void setActualStart(long actualStart) {
        this.actualStart = actualStart;
    }

    public long getActualStop() {
        return actualEnd;
    }

    public void setActualStop(long actualStop) {
        this.actualEnd = actualStop;
    }

    @Override
    List<UUID> getDeps() {
        if (dependencies == null || dependencies.isEmpty()) {
            return super.getDeps();
        }
        List<UUID> r = new ArrayList<>();
        if (relativeItemUuid != null) {
            r.add(relativeItemUuid);
        }

        dependencies.forEach(d -> r.add(fromProtoUuid(d.getActivityId())));
        return r;
    }

    private static UUID fromProtoUuid(org.yamcs.dbproto.timeline.UUID uuid) {
        return new UUID(uuid.getH(), uuid.getL());
    }

    @Override
    protected void addToProto(boolean detail, Builder protob) {
        protob.setActivityStatus(status);
        if (detail && failureReason != null) {
            protob.setFailureReason(failureReason);
        }

        if (actualStart != TimeEncoding.INVALID_INSTANT) {
            protob.setActualStart(TimeEncoding.toProtobufTimestamp(actualStart));
        }
        if (actualEnd != TimeEncoding.INVALID_INSTANT) {
            protob.setActualEnd(TimeEncoding.toProtobufTimestamp(actualEnd));
        }

        if (detail) {
            protob.setAutoRun(autoRun);
            protob.setAllowEarlyStart(allowEarlyStart);
        }
    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_STATUS, status.name());

        if (actualStart != TimeEncoding.INVALID_INSTANT) {
            tuple.addColumn(CNAME_ACTUAL_START, actualStart);
        }
        if (actualEnd != TimeEncoding.INVALID_INSTANT) {
            tuple.addColumn(CNAME_ACTUAL_END, actualEnd);
        }

        tuple.addColumn(CNAME_ALLOW_EARLY_START, allowEarlyStart);
        tuple.addColumn(CNAME_AUTO_RUN, autoRun);

        if (failureReason != null) {
            tuple.addColumn(CNAME_FAILURE_REASON, failureReason);
        }
    }

}
