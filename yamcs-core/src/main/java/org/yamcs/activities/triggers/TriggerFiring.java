package org.yamcs.activities.triggers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.yamcs.client.utils.WellKnownTypes;
import org.yamcs.http.api.GpbWellKnownHelper;
import org.yamcs.protobuf.activities.TriggerFiringInfo;
import org.yamcs.protobuf.activities.TriggerFiringOutcome;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;

import com.google.protobuf.Struct;

/**
 * Record of a decision taken when a trigger fired.
 */
public class TriggerFiring {

    static final String CNAME_TIME = "time";
    static final String CNAME_SEQ = "seq";
    static final String CNAME_ID = "id";
    static final String CNAME_TRIGGER_ID = "trigger_id";
    static final String CNAME_TRIGGER_NAME = "trigger_name";
    static final String CNAME_OUTCOME = "outcome";
    static final String CNAME_CONTEXT = "context";
    static final String CNAME_ACTIVITY_ID = "activity_id";
    static final String CNAME_DECIDED_BY = "decided_by";
    static final String CNAME_MESSAGE = "message";

    private final UUID id;
    private final long time;
    private final int seq;
    private final UUID triggerId;
    private final String triggerName;
    private final Map<String, Object> context;

    private TriggerFiringOutcome outcome;
    private UUID activityId;
    private String decidedBy;
    private String message;

    public TriggerFiring(UUID id, long time, int seq, UUID triggerId, String triggerName,
            Map<String, Object> context) {
        this.id = id;
        this.time = time;
        this.seq = seq;
        this.triggerId = triggerId;
        this.triggerName = triggerName;
        this.context = context;
    }

    public TriggerFiring(Tuple tuple) {
        id = tuple.getColumn(CNAME_ID);
        time = tuple.getTimestampColumn(CNAME_TIME);
        seq = tuple.getIntColumn(CNAME_SEQ);
        triggerId = tuple.getColumn(CNAME_TRIGGER_ID);
        triggerName = tuple.getColumn(CNAME_TRIGGER_NAME);
        outcome = TriggerFiringOutcome.valueOf((String) tuple.getColumn(CNAME_OUTCOME));
        Struct contextStruct = tuple.getColumn(CNAME_CONTEXT);
        context = contextStruct != null
                ? GpbWellKnownHelper.toJava(contextStruct)
                : new LinkedHashMap<>();
        activityId = tuple.getColumn(CNAME_ACTIVITY_ID);
        decidedBy = tuple.getColumn(CNAME_DECIDED_BY);
        message = tuple.getColumn(CNAME_MESSAGE);
    }

    public UUID getId() {
        return id;
    }

    public long getTime() {
        return time;
    }

    public int getSeq() {
        return seq;
    }

    public UUID getTriggerId() {
        return triggerId;
    }

    public String getTriggerName() {
        return triggerName;
    }

    /**
     * Values describing what caused this firing, keyed by placeholder name (for example {@code alarm.subject}).
     */
    public Map<String, Object> getContext() {
        return context;
    }

    public TriggerFiringOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(TriggerFiringOutcome outcome) {
        this.outcome = outcome;
    }

    public UUID getActivityId() {
        return activityId;
    }

    public void setActivityId(UUID activityId) {
        this.activityId = activityId;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Tuple toTuple() {
        var tuple = new Tuple();
        tuple.addTimestampColumn(CNAME_TIME, time);
        tuple.addColumn(CNAME_SEQ, seq);
        tuple.addColumn(CNAME_ID, DataType.UUID, id);
        tuple.addColumn(CNAME_TRIGGER_ID, DataType.UUID, triggerId);
        tuple.addColumn(CNAME_TRIGGER_NAME, triggerName);
        tuple.addColumn(CNAME_OUTCOME, outcome.name());
        tuple.addColumn(CNAME_CONTEXT, DataType.protobuf(Struct.class), WellKnownTypes.toStruct(context));
        tuple.addColumn(CNAME_ACTIVITY_ID, DataType.UUID, activityId);
        tuple.addColumn(CNAME_DECIDED_BY, DataType.STRING, decidedBy);
        tuple.addColumn(CNAME_MESSAGE, DataType.STRING, message);
        return tuple;
    }

    public TriggerFiringInfo toProto() {
        var b = TriggerFiringInfo.newBuilder()
                .setId(id.toString())
                .setTriggerId(triggerId.toString())
                .setTriggerName(triggerName)
                .setTime(TimeEncoding.toProtobufTimestamp(time))
                .setOutcome(outcome)
                .setContext(WellKnownTypes.toStruct(context));
        if (activityId != null) {
            b.setActivityId(activityId.toString());
        }
        if (decidedBy != null) {
            b.setDecidedBy(decidedBy);
        }
        if (message != null) {
            b.setMessage(message);
        }
        return b.build();
    }

    @Override
    public String toString() {
        return "TriggerFiring [" + triggerName + ", " + outcome + ", " + context + "]";
    }
}
