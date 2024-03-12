package org.yamcs.activities;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;

/**
 * Keep track of the lifecycle of a stacked command.
 */
public class StackedCommand {

    private String acknowledgment;
    // -1 means: inherit from stack
    private int waitTime = -1;
    private MetaCommand meta;
    private Map<Argument, String> assignments = new HashMap<>();
    private Map<String, Value> extra = new HashMap<>();

    private String comment;

    public StackedCommand() {
    }

    public void setAcknowledgment(String acknowledgment) {
        this.acknowledgment = acknowledgment;
    }

    public String getAcknowledgment() {
        return acknowledgment;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setMetaCommand(MetaCommand meta) {
        this.meta = meta;
    }

    public String getName() {
        return meta.getQualifiedName();
    }

    public String getName(String namespace) {
        for (var entry : meta.getAliasSet().getAliases().entrySet()) {
            if (entry.getKey().equals(namespace)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public MetaCommand getMetaCommand() {
        return meta;
    }

    public void addAssignment(Argument arg, String value) {
        assignments.put(arg, value);
    }

    public Map<Argument, String> getAssignments() {
        return assignments;
    }

    public boolean isAssigned(Argument arg) {
        return assignments.get(arg) != null;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    public void setExtra(String option, Value value) {
        if (value == null) {
            extra.remove(option);
        } else {
            extra.put(option, value);
        }
    }

    public Map<String, Value> getExtra() {
        return extra;
    }

    @Override
    public String toString() {
        var argLine = assignments.entrySet().stream().map(entry -> {
            return entry.getKey().getName() + "=" + entry.getValue();
        }).collect(Collectors.joining(", "));

        return meta.getQualifiedName() + "(" + argLine + ")";
    }
}
