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
    public static final String CNAME_SEQ_NUM = "seqNum";
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

    public static final String CNAME_UPDATE_TIME = "updateTime";
    public static final String CNAME_VALUE_COUNT = "valueCount";
    public static final String CNAME_VIOLATION_COUNT = "violationCount";

    public AlarmStreamer(Stream s, DataType dataType, TupleDefinition tdefTemplate) {
        this.stream = s;
        this.dataType = dataType;
        this.tdefTemplate = tdefTemplate;
    }

    @Override
    public void notifySeverityIncrease(ActiveAlarm<T> activeAlarm) {
        TupleDefinition tdef = tdefTemplate.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm);
        addCommonColumns(activeAlarm, tdef, al);

        tdef.addColumn(getColNameLastEvent(), DataType.STRING);
        al.add(AlarmNotificationType.SEVERITY_INCREASED.name());

        tdef.addColumn(getColNameSeverityIncreased(), dataType);
        al.add(activeAlarm.getMostSevereValue());

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifyValueUpdate(ActiveAlarm<T> activeAlarm) {
        TupleDefinition tdef = tdefTemplate.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm);
        addCommonColumns(activeAlarm, tdef, al);

        Tuple t = new Tuple(tdef, al);
        stream.emitTuple(t);
    }

    @Override
    public void notifyUpdate(AlarmNotificationType notificationType, ActiveAlarm<T> activeAlarm) {
        TupleDefinition tdef = tdefTemplate.copy();
        ArrayList<Object> al = getTupleKey(activeAlarm);
        addCommonColumns(activeAlarm, tdef, al);

        switch (notificationType) {
        case TRIGGERED:
            tdef.addColumn(getColNameLastEvent(), DataType.STRING);
            al.add(notificationType.name());

            tdef.addColumn(getColNameTrigger(), dataType);
            al.add(activeAlarm.getTriggerValue());
            break;
        case ACKNOWLEDGED:
            tdef.addColumn(getColNameLastEvent(), DataType.STRING);
            al.add(notificationType.name());

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
            tdef.addColumn(getColNameLastEvent(), DataType.STRING);
            al.add(notificationType.name());

            tdef.addColumn(getColNameClear(), dataType);
            al.add(activeAlarm.getCurrentValue());

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
            tdef.addColumn(getColNameLastEvent(), DataType.STRING);
            al.add(notificationType.name());

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

    private void addCommonColumns(ActiveAlarm<T> activeAlarm, TupleDefinition tdef, ArrayList<Object> al) {
        tdef.addColumn(CNAME_UPDATE_TIME, DataType.TIMESTAMP);
        al.add(getUpdateTime(activeAlarm.getCurrentValue()));

        tdef.addColumn(CNAME_VALUE_COUNT, DataType.INT);
        al.add(activeAlarm.getValueCount());

        tdef.addColumn(CNAME_VIOLATION_COUNT, DataType.INT);
        al.add(activeAlarm.getViolations());
    }

    protected abstract String getColNameLastEvent();

    protected abstract String getColNameClear();

    protected abstract String getColNameTrigger();

    protected abstract ArrayList<Object> getTupleKey(ActiveAlarm<T> activeAlarm);

    protected abstract String getColNameSeverityIncreased();

    protected abstract long getUpdateTime(T alarmDetail);
}
