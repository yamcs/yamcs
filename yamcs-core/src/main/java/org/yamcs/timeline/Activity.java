package org.yamcs.timeline;

import java.util.List;
import java.util.UUID;

import org.yamcs.yarch.Tuple;

public abstract class Activity extends TimelineItem {
    static enum ExecutionStatus {
        PLANNED, IN_PROGRESS, COMPLETED, ABORTED, FAILED;
    }

    List<Dependence> dependsOn;
    ExecutionStatus executionStatus;

    String failureReason;

    public Activity(UUID id) {
        super(id);
    }

    Activity(Tuple tuple) {
        super(tuple);
    }

    static class Dependence {
        UUID id;

    }

}
