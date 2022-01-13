package org.yamcs.alarms;

import java.util.ArrayList;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.TupleDefinition;

public abstract class AlarmStreamer<T> implements AlarmListener<T> {
    protected Stream stream;
    final DataType dataType;
    final TupleDefinition tdefTemplate;
    public static final String CNAME_TRIGGER_TIME = "triggerTime";
    public static final String CNAME_CLEARED_BY = "clearedBy";
    public static final String CNAME_CLEAR_MSG = "clearedMessage";
    public static final String CNAME_CLEARED_TIME = "clearedTime";

    public static final String CNAME_ACK_BY = "acknowledgedBy";
    public static final String CNAME_ACK_MSG = "acknowledgeMessage";
    public static final String CNAME_ACK_TIME = "acknowledgeTime";

    public static final String CNAME_SHELVED_BY = "shelvedBy";
    public static final String CNAME_SHELVED_MSG = "shelvedMessage";
    public static final String CNAME_SHELVED_TIME = "shelvedTime";
    public static final String CNAME_SHELVE_DURATION = "shelvedDuration";

    public AlarmStreamer(Stream s, DataType dataType, TupleDefinition tdefTemplate) {
        this.stream = s;
        this.dataType = dataType;
        this.tdefTemplate = tdefTemplate;
    }

    @Override
    public void notifySeverityIncrease(ActiveAlarm<T> activeAlarm) {
        TupleDefinition tdef = tdefTemplate.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, AlarmNotificationType.SEVERITY_INCREASED);

        tdef.addColumn(getColNameSeverityIncreased(), dataType);
        al.add(getYarchValue(activeAlarm.getMostSevereValue()));

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifyValueUpdate(ActiveAlarm<T> activeAlarm) {
        // do not send parameter updates
    }

    @Override
    public void notifyUpdate(AlarmNotificationType notificationType, ActiveAlarm<T> activeAlarm) {
        TupleDefinition tdef = tdefTemplate.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm, notificationType);

        switch (notificationType) {
        case TRIGGERED:
            tdef.addColumn(getColNameTrigger(), dataType);
            al.add(getYarchValue(activeAlarm.getTriggerValue()));
            break;
        case ACKNOWLEDGED:
            tdef.addColumn(CNAME_ACK_BY, DataType.STRING);
            String username = activeAlarm.getUsernameThatAcknowledged();
            if (activeAlarm.isAutoAcknowledge()) {
                username = "autoAcknowledged";
            }
            al.add(username);

            if (activeAlarm.getAckMessage() != null) {
                tdef.addColumn(CNAME_ACK_MSG, DataType.STRING);
                al.add(activeAlarm.getAckMessage());
            }

            tdef.addColumn(CNAME_ACK_TIME, DataType.TIMESTAMP);
            al.add(activeAlarm.getAcknowledgeTime());

            break;
        case CLEARED:
            tdef.addColumn(getColNameClear(), dataType);
            al.add(getYarchValue(activeAlarm.getCurrentValue()));

            if (activeAlarm.getUsernameThatCleared() != null) {
                tdef.addColumn(CNAME_CLEARED_BY, DataType.STRING);
                al.add(activeAlarm.getUsernameThatCleared());
            }
            if (activeAlarm.getClearMessage() != null) {
                tdef.addColumn(CNAME_CLEAR_MSG, DataType.STRING);
                al.add(activeAlarm.getClearMessage());
            }

            tdef.addColumn(CNAME_CLEARED_TIME, DataType.TIMESTAMP);
            al.add(activeAlarm.getClearTime());
            break;
        case SHELVED:
            tdef.addColumn(CNAME_SHELVED_BY, DataType.STRING);
            username = activeAlarm.getUsernameThatShelved();
            al.add(username);

            if (activeAlarm.getShelveMessage() != null) {
                tdef.addColumn(CNAME_SHELVED_MSG, DataType.STRING);
                al.add(activeAlarm.getShelveMessage());
            }
            if (activeAlarm.getShelveDuration() != -1) {
                tdef.addColumn(CNAME_SHELVE_DURATION, DataType.LONG);
                al.add(activeAlarm.getShelveDuration());
            }

            tdef.addColumn(CNAME_SHELVED_TIME, DataType.TIMESTAMP);
            al.add(activeAlarm.getShelveTime());
            break;
        default:
            break;

        }
        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);

    }

    protected abstract String getColNameClear();

    protected abstract String getColNameTrigger();

    protected abstract Object getYarchValue(T currentValue);

    protected abstract ArrayList<Object> getTupleKey(ActiveAlarm<T> activeAlarm,
            AlarmNotificationType notificationType);

    protected abstract String getColNameSeverityIncreased();
}
