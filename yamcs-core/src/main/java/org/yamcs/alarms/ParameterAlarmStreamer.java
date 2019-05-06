package org.yamcs.alarms;

import java.util.ArrayList;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public class ParameterAlarmStreamer implements AlarmListener<ParameterValue> {
    static public final DataType PARAMETER_DATA_TYPE = DataType
            .protobuf(org.yamcs.protobuf.Pvalue.ParameterValue.class.getName());

    Stream stream;

    public ParameterAlarmStreamer(Stream s) {
        this.stream = s;
    }

    private ArrayList<Object> getTupleKey(ActiveAlarm<ParameterValue> activeAlarm, AlarmNotificationType e) {
        ArrayList<Object> al = new ArrayList<>(7);

        // triggerTime
        al.add(activeAlarm.triggerValue.getGenerationTime());
        // parameter
        al.add(activeAlarm.triggerValue.getParameter().getQualifiedName());
        // seqNum
        al.add(activeAlarm.id);
        // event
        al.add(e.name());

        return al;
    }

    @Override
    public void notifyTriggered(ActiveAlarm<ParameterValue> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.PARAMETER_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.TRIGGERED);

        tdef.addColumn("triggerPV", PARAMETER_DATA_TYPE);
        NamedObjectId id = NamedObjectId.newBuilder()
                .setName(activeAlarm.triggerValue.getParameter().getQualifiedName()).build();
        al.add(activeAlarm.triggerValue.toGpb(id));

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifySeverityIncrease(ActiveAlarm<ParameterValue> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.PARAMETER_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.SEVERITY_INCREASED);

        tdef.addColumn("severityIncreasedPV", PARAMETER_DATA_TYPE);
        NamedObjectId id = NamedObjectId.newBuilder()
                .setName(activeAlarm.mostSevereValue.getParameter().getQualifiedName()).build();
        al.add(activeAlarm.mostSevereValue.toGpb(id));

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifyValueUpdate(ActiveAlarm<ParameterValue> activeAlarm) {
        // do not send parameter updates
    }

    @Override
    public void notifyAcknowledged(ActiveAlarm<ParameterValue> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.PARAMETER_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.ACKNOWLEDGED);

        tdef.addColumn("acknowledgedBy", DataType.STRING);
        String username = activeAlarm.usernameThatAcknowledged;
        if (activeAlarm.autoAcknowledge) {
            username = "autoAcknowledged";
        }
        al.add(username);

        if (activeAlarm.message != null) {
            tdef.addColumn("acknowledgeMessage", DataType.STRING);
            al.add(activeAlarm.message);
        }

        tdef.addColumn("acknowledgeTime", DataType.TIMESTAMP);
        al.add(activeAlarm.acknowledgeTime);

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifyCleared(ActiveAlarm<ParameterValue> activeAlarm) {
        TupleDefinition tdef = StandardTupleDefinitions.PARAMETER_ALARM.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.CLEARED);

        tdef.addColumn("clearedPV", PARAMETER_DATA_TYPE);
        NamedObjectId id = NamedObjectId.newBuilder()
                .setName(activeAlarm.currentValue.getParameter().getQualifiedName()).build();
        al.add(activeAlarm.currentValue.toGpb(id));

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }
}
