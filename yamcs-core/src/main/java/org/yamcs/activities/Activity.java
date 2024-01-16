package org.yamcs.activities;

import static org.yamcs.activities.ActivityDb.CNAME_ARGS;
import static org.yamcs.activities.ActivityDb.CNAME_COMMENT;
import static org.yamcs.activities.ActivityDb.CNAME_DETAIL;
import static org.yamcs.activities.ActivityDb.CNAME_FAILURE_REASON;
import static org.yamcs.activities.ActivityDb.CNAME_ID;
import static org.yamcs.activities.ActivityDb.CNAME_SEQ;
import static org.yamcs.activities.ActivityDb.CNAME_START;
import static org.yamcs.activities.ActivityDb.CNAME_STARTED_BY;
import static org.yamcs.activities.ActivityDb.CNAME_STATUS;
import static org.yamcs.activities.ActivityDb.CNAME_STOP;
import static org.yamcs.activities.ActivityDb.CNAME_STOPPED_BY;
import static org.yamcs.activities.ActivityDb.CNAME_TYPE;

import java.util.Map;
import java.util.UUID;

import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.http.api.GpbWellKnownHelper;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.Struct;

public class Activity implements Comparable<Activity> {

    private final UUID id;
    private final long start;
    private final int seq;
    private final String type;
    private final Map<String, Object> args;
    private final String startedBy;
    private String detail;
    private String comment;

    private ActivityStatus status = ActivityStatus.RUNNING;

    private long stop = TimeEncoding.INVALID_INSTANT;
    private String failureReason;
    private String stoppedBy;

    public Activity(UUID id, long start, int seq, String type, Map<String, Object> args, User startedBy) {
        this.id = id;
        this.start = start;
        this.seq = seq;
        this.type = type;
        this.args = args;
        this.startedBy = startedBy.getName();
    }

    public Activity(Tuple tuple) {
        id = tuple.getColumn(CNAME_ID);
        start = tuple.getTimestampColumn(CNAME_START);
        seq = tuple.getIntColumn(CNAME_SEQ);
        type = tuple.getColumn(CNAME_TYPE);

        Struct argsStruct = tuple.getColumn(CNAME_ARGS);
        if (argsStruct != null) {
            args = GpbWellKnownHelper.toJava(argsStruct);
        } else {
            args = null;
        }

        startedBy = tuple.getColumn(CNAME_STARTED_BY);
        stoppedBy = tuple.getColumn(CNAME_STOPPED_BY);
        detail = tuple.getColumn(CNAME_DETAIL);
        comment = tuple.getColumn(CNAME_COMMENT);
        failureReason = tuple.getColumn(CNAME_FAILURE_REASON);
        status = ActivityStatus.valueOf(tuple.getColumn(CNAME_STATUS));
        if (tuple.hasColumn(CNAME_STOP)) {
            stop = tuple.getTimestampColumn(CNAME_STOP);
        }
    }

    public UUID getId() {
        return id;
    }

    public ActivityStatus getStatus() {
        return status;
    }

    public void setStatus(ActivityStatus status) {
        this.status = status;
    }

    public long getStart() {
        return start;
    }

    public int getSeq() {
        return seq;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public String getStoppedBy() {
        return stoppedBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public long getStop() {
        return stop;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isStopped() {
        return stop != TimeEncoding.INVALID_INSTANT;
    }

    public void cancel(User user) {
        if (stop != TimeEncoding.INVALID_INSTANT) {
            throw new IllegalStateException("Activity is already stopped");
        }
        this.status = ActivityStatus.CANCELLED;
        this.stop = TimeEncoding.getWallclockTime();
        this.stoppedBy = user != null ? user.getName() : null;
    }

    /**
     * Stop a successful activity
     */
    public void complete(User user) {
        if (stop != TimeEncoding.INVALID_INSTANT) {
            throw new IllegalStateException("Activity is already stopped");
        }
        this.stop = TimeEncoding.getWallclockTime();
        this.stoppedBy = user != null ? user.getName() : null;
        this.status = ActivityStatus.SUCCESSFUL;
    }

    /**
     * Stop an activity. If failureReason is null, the activity is considered successful.
     */
    public void completeExceptionally(String failureReason, User user) {
        if (stop != TimeEncoding.INVALID_INSTANT) {
            throw new IllegalStateException("Activity is already stopped");
        }
        this.stop = TimeEncoding.getWallclockTime();
        this.failureReason = failureReason;
        this.stoppedBy = user != null ? user.getName() : null;
        this.status = ActivityStatus.FAILED;
    }

    public Tuple toTuple() {
        var tuple = new Tuple();
        tuple.addColumn(CNAME_START, start);
        tuple.addColumn(CNAME_SEQ, seq);
        tuple.addColumn(CNAME_ID, DataType.UUID, id);
        tuple.addColumn(CNAME_TYPE, type);
        var argsStruct = args != null ? WellKnownTypes.toStruct(args) : null;
        tuple.addColumn(CNAME_ARGS, DataType.protobuf(Struct.class), argsStruct);
        tuple.addColumn(CNAME_STATUS, status.name());
        tuple.addColumn(CNAME_DETAIL, detail);
        tuple.addColumn(CNAME_STARTED_BY, startedBy);
        tuple.addColumn(CNAME_STOPPED_BY, stoppedBy);
        tuple.addColumn(CNAME_COMMENT, comment);

        if (stop != TimeEncoding.INVALID_INSTANT) {
            tuple.addColumn(CNAME_STOP, stop);
        } else {
            tuple.addColumn(CNAME_STOP, null);
        }

        tuple.addColumn(CNAME_FAILURE_REASON, failureReason);

        return tuple;
    }

    @Override
    public int compareTo(Activity other) {
        var rc = Long.compare(start, other.start);
        return (rc != 0) ? rc : Integer.compare(seq, other.seq);
    }
}
