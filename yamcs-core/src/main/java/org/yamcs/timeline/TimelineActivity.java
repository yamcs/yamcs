package org.yamcs.timeline;

import static org.yamcs.timeline.TimelineItemDb.CNAME_ACTIVITY_DEFINITION;
import static org.yamcs.timeline.TimelineItemDb.CNAME_FAILURE_REASON;
import static org.yamcs.timeline.TimelineItemDb.CNAME_RUNS;
import static org.yamcs.timeline.TimelineItemDb.CNAME_STATUS;
import static org.yamcs.timeline.TimelineItemDb.CNAME_DEPENDENCIES;
import static org.yamcs.timeline.TimelineItemDb.CNAME_DEPENDENCIES_CONDITIONS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.yamcs.activities.protobuf.ActivityDefinition;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.ActivityDependency;
import org.yamcs.protobuf.ActivityDependencyCondition;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.protobuf.activities.ActivityDefinitionInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

public class TimelineActivity extends TimelineItem implements Comparable<TimelineActivity> {
    static Log log = new Log(TimelineActivity.class);

    protected List<Dependency> dependsOn = new ArrayList<>();
    protected ExecutionStatus status = ExecutionStatus.PLANNED;
    protected String failureReason;
    protected List<UUID> runs = new ArrayList<>();
    protected ActivityDefinition activityDefinition;

    public TimelineActivity(UUID id) {
        super(TimelineItemType.ACTIVITY, id.toString());
    }

    protected TimelineActivity(TimelineItemType type, UUID id) {
        super(type, id.toString());
    }

    public TimelineActivity(TimelineItemType type, Tuple tuple) {
        super(type, tuple);
        String dbstatus = tuple.getColumn(CNAME_STATUS);
        this.status = ExecutionStatus.valueOf(dbstatus);

        if (tuple.hasColumn(CNAME_FAILURE_REASON)) {
            this.failureReason = tuple.getColumn(CNAME_FAILURE_REASON);
        }
        if (tuple.hasColumn(CNAME_RUNS)) {
            this.runs = tuple.getColumn(CNAME_RUNS);
        }
        if (tuple.hasColumn(CNAME_ACTIVITY_DEFINITION)) {
            this.activityDefinition = tuple.getColumn(CNAME_ACTIVITY_DEFINITION);
        }

        if (tuple.hasColumn(CNAME_DEPENDENCIES) && tuple.hasColumn(CNAME_DEPENDENCIES_CONDITIONS)) {
            List<UUID> deps = tuple.getColumn(CNAME_DEPENDENCIES);
            List<String> depsCond = tuple.getColumn(CNAME_DEPENDENCIES_CONDITIONS);
            if (deps.size() == depsCond.size()) {
                for (int i = 0; i < deps.size(); i++) {
                    var cond = ActivityDependencyCondition.valueOf(depsCond.get(i));
                    this.dependsOn.add(new Dependency(deps.get(i), cond));
                }
            } else {
                log.warn("Encountred invalid activity; dependencies: {}, dependencies conditions: {}", deps, depsCond);
            }
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

    public void setActivityDefinition(ActivityDefinition activityDefinition) {
        this.activityDefinition = activityDefinition;
    }

    public ActivityDefinition getActivityDefinition() {
        return activityDefinition;
    }

    public List<UUID> getRuns() {
        return runs;
    }

    public void addRun(UUID runId) {
        runs.add(runId);
    }

    public void addDependsOn(ActivityDependency d) {
        UUID uuid = UUID.fromString(d.getId());
        dependsOn.add(new Dependency(uuid, d.getCondition()));
    }

    @Override
    protected void addToProto(boolean detail, Builder protob) {
        protob.setStatus(status);
        if (failureReason != null) {
            protob.setFailureReason(failureReason);
        }
        if (activityDefinition != null) {
            var b = ActivityDefinitionInfo.newBuilder()
                    .setType(activityDefinition.getType())
                    .setArgs(activityDefinition.getArgs());
            protob.setActivityDefinition(b);
        }
        runs.forEach(runId -> protob.addRuns(runId.toString()));
        dependsOn.forEach(d -> protob.addDependsOn(
                ActivityDependency.newBuilder().setId(d.id().toString()).setCondition(d.condition).build()));

    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_STATUS, status.name());
        if (failureReason != null) {
            tuple.addColumn(CNAME_FAILURE_REASON, failureReason);
        }
        if (activityDefinition != null) {
            tuple.addColumn(CNAME_ACTIVITY_DEFINITION, DataType.protobuf(ActivityDefinition.class), activityDefinition);
        }

        if (runs.isEmpty()) {
            tuple.addColumn(CNAME_RUNS, DataType.array(DataType.UUID), null);
        } else {
            tuple.addColumn(CNAME_RUNS, DataType.array(DataType.UUID), runs);
        }
        if (dependsOn.isEmpty()) {
            tuple.addColumn(CNAME_DEPENDENCIES, DataType.array(DataType.UUID), null);
            tuple.addColumn(CNAME_DEPENDENCIES_CONDITIONS, DataType.array(DataType.ENUM), null);
        } else {
            tuple.addColumn(CNAME_DEPENDENCIES, DataType.array(DataType.UUID),
                    dependsOn.stream().map(c -> c.id()).toList());
            tuple.addColumn(CNAME_DEPENDENCIES_CONDITIONS, DataType.array(DataType.ENUM),
                    dependsOn.stream().map(c -> c.condition().name()).toList());
        }
    }

    @Override
    public int compareTo(TimelineActivity other) {
        if (this == other) {
            return 0;
        }

        var rc = Long.compareUnsigned(start, other.start);
        // Fallback to something unique, to make it deterministic
        return (rc != 0) ? rc : id.compareTo(other.getId());
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TimelineActivity)) {
            return false;
        }
        var other = (TimelineActivity) obj;
        return id.equals(other.id);
    }

    @Override
    public String toString() {
        return String.format("[id=%s, start=%s]", id, TimeEncoding.toString(start));
    }

    /**
     * Represents a dependency relationship between two activities.
     * <p>
     * An activity can depend on another activity, and this class specifies the dependent activity's unique identifier
     * and the condition that determines whether it should start.
     */
    static record Dependency(UUID id, ActivityDependencyCondition condition) {
    }

}
