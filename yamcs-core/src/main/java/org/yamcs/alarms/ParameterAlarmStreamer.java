package org.yamcs.alarms;

import java.util.ArrayList;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

public class ParameterAlarmStreamer extends AlarmStreamer<ParameterValue> {
    static public final String CNAME_LAST_EVENT = "alarmEvent";
    static public final String CNAME_UPDATE_PV = "updatePV";
    static public final String CNAME_TRIGGER = "triggerPV";
    static public final String CNAME_CLEAR = "clearPV";
    static public final String CNAME_SEVERITY_INCREASED = "severityIncreasedPV";

    public ParameterAlarmStreamer(Stream s) {
        super(s, DataType.PARAMETER_VALUE, StandardTupleDefinitions.PARAMETER_ALARM);
    }

    @Override
    protected ArrayList<Object> getTupleKey(AlarmNotificationType notificationType,
            ActiveAlarm<ParameterValue> activeAlarm) {
        ArrayList<Object> al = new ArrayList<>();

        // triggerTime
        al.add(activeAlarm.getTriggerValue().getGenerationTime());
        // parameter
        al.add(activeAlarm.getTriggerValue().getParameter().getQualifiedName());
        // seqNum
        al.add(activeAlarm.getId());

        // alarmEvent
        al.add(notificationType.toString());

        // the AlarmRecorder checks for null
        al.add(activeAlarm.isPending() ? Boolean.TRUE : null);

        return al;
    }

    /**
     * generate a tuple with the violation/value counters and the last value to be saved in the database before shutdown
     */
    @Override
    public void notifyShutdown(ActiveAlarm<ParameterValue> alarm) {
        if (alarm.isPending()) {
            return;
        }
        Tuple t = new Tuple();
        // primary key
        t.addTimestampColumn(AlarmStreamer.CNAME_TRIGGER_TIME, alarm.getTriggerValue().getGenerationTime());
        t.addColumn(StandardTupleDefinitions.PARAMETER_COLUMN,
                alarm.getTriggerValue().getParameter().getQualifiedName());
        t.addColumn(StandardTupleDefinitions.SEQNUM_COLUMN, alarm.getId());

        // values we are interested in
        t.addColumn(CNAME_VIOLATION_COUNT, alarm.getViolations());
        t.addColumn(CNAME_VALUE_COUNT, alarm.getValueCount());
        t.addColumn(CNAME_LAST_VALUE, DataType.PARAMETER_VALUE, alarm.getCurrentValue());

        stream.emitTuple(t);
    }

    @Override
    protected String getColNameLastEvent() {
        return CNAME_LAST_EVENT;
    }

    @Override
    protected String getColNameClear() {
        return CNAME_CLEAR;
    }

    @Override
    protected String getColNameTrigger() {
        return CNAME_TRIGGER;
    }

    @Override
    protected String getColNameSeverityIncreased() {
        return CNAME_SEVERITY_INCREASED;
    }

    @Override
    protected long getUpdateTime(ParameterValue alarmDetail) {
        return alarmDetail.getGenerationTime();
    }

}
