package org.yamcs.activities;

import static org.yamcs.activities.ActivityLogDb.CNAME_ACTIVITY_ID;
import static org.yamcs.activities.ActivityLogDb.CNAME_LEVEL;
import static org.yamcs.activities.ActivityLogDb.CNAME_MESSAGE;
import static org.yamcs.activities.ActivityLogDb.CNAME_SOURCE;
import static org.yamcs.activities.ActivityLogDb.CNAME_TIME;

import java.util.Objects;
import java.util.UUID;

import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

public class ActivityLog {

    public static final String SOURCE_SERVICE = "SERVICE";
    public static final String SOURCE_ACTIVITY = "ACTIVITY";

    private final long time;
    private final UUID activityId;
    private final String source;
    private final ActivityLogLevel level;
    private final String message;

    public ActivityLog(long time, UUID activityId, String source, ActivityLogLevel level, String message) {
        this.time = time;
        this.activityId = Objects.requireNonNull(activityId);
        this.source = Objects.requireNonNull(source);
        this.level = Objects.requireNonNull(level);
        this.message = Objects.requireNonNull(message);
    }

    public ActivityLog(Tuple tuple) {
        this.time = tuple.getTimestampColumn(CNAME_TIME);
        this.activityId = tuple.getColumn(CNAME_ACTIVITY_ID);
        this.source = tuple.getColumn(CNAME_SOURCE);
        this.level = ActivityLogLevel.valueOf(tuple.getColumn(CNAME_LEVEL));
        this.message = tuple.getColumn(CNAME_MESSAGE);
    }

    public long getTime() {
        return time;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public String getSource() {
        return source;
    }

    public ActivityLogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public Tuple toTuple() {
        var tuple = new Tuple();
        tuple.addColumn(CNAME_TIME, time);
        tuple.addColumn(CNAME_ACTIVITY_ID, DataType.UUID, activityId);
        tuple.addColumn(CNAME_SOURCE, source);
        tuple.addColumn(CNAME_LEVEL, level.name());
        tuple.addColumn(CNAME_MESSAGE, message);
        return tuple;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", TimeEncoding.toString(time), message);
    }
}
