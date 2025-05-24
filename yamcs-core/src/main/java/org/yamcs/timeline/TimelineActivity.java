package org.yamcs.timeline;

import static org.yamcs.protobuf.StartCondition.ON_COMPLETION;
import static org.yamcs.timeline.TimelineItemDb.CNAME_ACTIVITY_DEFINITION;
import static org.yamcs.timeline.TimelineItemDb.CNAME_AUTO_START;
import static org.yamcs.timeline.TimelineItemDb.CNAME_FAILURE_REASON;
import static org.yamcs.timeline.TimelineItemDb.CNAME_PREDECESSORS;
import static org.yamcs.timeline.TimelineItemDb.CNAME_PREDECESSORS_START_CONDITIONS;
import static org.yamcs.timeline.TimelineItemDb.CNAME_RUNS;
import static org.yamcs.timeline.TimelineItemDb.CNAME_STATUS;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.yamcs.activities.protobuf.ActivityDefinition;
import org.yamcs.protobuf.ExecutionStatus;
import org.yamcs.protobuf.PredecessorInfo;
import org.yamcs.protobuf.StartCondition;
import org.yamcs.protobuf.TimelineItem.Builder;
import org.yamcs.protobuf.TimelineItemType;
import org.yamcs.protobuf.activities.ActivityDefinitionInfo;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

public class TimelineActivity extends TimelineItem implements Comparable<TimelineActivity> {
    public static final List<ExecutionStatus> ONGOING_STATES = List.of(
            ExecutionStatus.IN_PROGRESS,
            ExecutionStatus.PAUSED,
            ExecutionStatus.WAITING_FOR_INPUT);

    public static final List<ExecutionStatus> TERMINAL_STATES = List.of(
            ExecutionStatus.SUCCEEDED,
            ExecutionStatus.ABORTED,
            ExecutionStatus.FAILED,
            ExecutionStatus.SKIPPED,
            ExecutionStatus.CANCELED);

    public static final List<ExecutionStatus> FUTURE_STATES = List.of(
            ExecutionStatus.PLANNED,
            ExecutionStatus.READY,
            ExecutionStatus.WAITING_ON_DEPENDENCY);

    protected List<Predecessor> predecessors = new ArrayList<>();
    // Not persisted, only to detect status changes
    protected ExecutionStatus prevStatus = ExecutionStatus.PLANNED;
    protected ExecutionStatus status = ExecutionStatus.PLANNED;
    protected String failureReason;
    protected List<UUID> runs = new ArrayList<>();
    protected ActivityDefinition activityDefinition;
    protected boolean autoStart;

    public TimelineActivity(UUID id) {
        super(TimelineItemType.ACTIVITY, id.toString());
    }

    protected TimelineActivity(TimelineItemType type, UUID id) {
        super(type, id.toString());
    }

    public TimelineActivity(TimelineItemType type, Tuple tuple) {
        super(type, tuple);
        String dbstatus = tuple.getColumn(CNAME_STATUS);
        this.prevStatus = this.status = ExecutionStatus.valueOf(dbstatus);

        if (tuple.hasColumn(CNAME_FAILURE_REASON)) {
            this.failureReason = tuple.getColumn(CNAME_FAILURE_REASON);
        }
        if (tuple.hasColumn(CNAME_RUNS)) {
            this.runs = tuple.getColumn(CNAME_RUNS);
        }
        if (tuple.hasColumn(CNAME_ACTIVITY_DEFINITION)) {
            this.activityDefinition = tuple.getColumn(CNAME_ACTIVITY_DEFINITION);
        }
        if (tuple.hasColumn(CNAME_AUTO_START)) {
            this.autoStart = tuple.getColumn(CNAME_AUTO_START);
        }
        if (tuple.hasColumn(CNAME_PREDECESSORS) && tuple.hasColumn(CNAME_PREDECESSORS_START_CONDITIONS)) {
            List<UUID> ids = tuple.getColumn(CNAME_PREDECESSORS);
            List<StartCondition> startConditions = new ArrayList<>();

            if (tuple.hasColumn(CNAME_PREDECESSORS_START_CONDITIONS)) {
                List<String> conditionValues = tuple.getColumn(CNAME_PREDECESSORS_START_CONDITIONS);
                if (conditionValues.size() == ids.size()) {
                    for (var conditionValue : conditionValues) {
                        var condition = StartCondition.valueOf(conditionValue);
                        startConditions.add(condition);
                    }
                }
            }

            for (int i = 0; i < ids.size(); i++) {
                var id = ids.get(i);
                var condition = startConditions.isEmpty() ? ON_COMPLETION : startConditions.get(i);
                this.predecessors.add(new Predecessor(id, condition));
            }
        }
    }

    public boolean isTerminated() {
        return TERMINAL_STATES.contains(status);
    }

    public void setStatus(ExecutionStatus status) {
        this.prevStatus = this.status;
        this.status = status;
    }

    public ExecutionStatus getPrevStatus() {
        return prevStatus;
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

    public UUID getLastRun() {
        if (runs.size() > 0) {
            return runs.get(runs.size() - 1);
        } else {
            return null;
        }
    }

    public List<UUID> getRuns() {
        return runs;
    }

    public void addRun(UUID runId) {
        runs.add(runId);
    }

    public void addPredecessor(Predecessor predecessor) {
        predecessors.add(predecessor);
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
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
            if (activityDefinition.hasDescription()) {
                b.setDescription(activityDefinition.getDescription());
            }
            protob.setActivityDefinition(b);
        }
        protob.setAutoStart(autoStart);
        runs.forEach(runId -> protob.addRuns(runId.toString()));
        predecessors.forEach(d -> protob.addPredecessors(
                PredecessorInfo.newBuilder()
                        .setItemId(d.itemId().toString())
                        .setStartCondition(d.startCondition())
                        .build()));

    }

    @Override
    protected void addToTuple(Tuple tuple) {
        tuple.addEnumColumn(CNAME_STATUS, status.name());
        tuple.addColumn(CNAME_AUTO_START, autoStart);

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
        if (predecessors.isEmpty()) {
            tuple.addColumn(CNAME_PREDECESSORS, DataType.array(DataType.UUID), null);
            tuple.addColumn(CNAME_PREDECESSORS_START_CONDITIONS, DataType.array(DataType.ENUM), null);
        } else {
            tuple.addColumn(CNAME_PREDECESSORS, DataType.array(DataType.UUID),
                    predecessors.stream().map(c -> c.itemId()).toList());
            tuple.addColumn(CNAME_PREDECESSORS_START_CONDITIONS, DataType.array(DataType.ENUM),
                    predecessors.stream().map(c -> c.startCondition().name()).toList());
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
}
