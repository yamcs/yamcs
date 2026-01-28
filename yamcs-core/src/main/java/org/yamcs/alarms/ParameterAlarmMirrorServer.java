package org.yamcs.alarms;

import static org.yamcs.alarms.AlarmStreamer.CNAME_LAST_VALUE;
import static org.yamcs.alarms.AlarmStreamer.CNAME_VALUE_COUNT;
import static org.yamcs.alarms.AlarmStreamer.CNAME_VIOLATION_COUNT;

import java.util.Map;

import org.yamcs.StandardTupleDefinitions;
import org.yamcs.archive.AlarmRecorder;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Tuple;

class ParameterAlarmMirrorServer extends AbstractAlarmMirrorServer<Parameter, ParameterValue> {
    Mdb mdb;

    ParameterAlarmMirrorServer(String yamcsInstance, double alarmLoadDays) {
        super(yamcsInstance, alarmLoadDays);
        mdb = MdbFactory.getInstance(yamcsInstance);
    }

    @Override
    Parameter getSubject(Tuple tuple) {
        String pname = tuple.getColumn(StandardTupleDefinitions.PARAMETER_COLUMN);
        var parameter = mdb.getParameter(pname);

        if (parameter == null) {
            log.info("Not processing alarm for {} because the parameter was not found in the MDB", pname);
            return null;
        }
        return parameter;
    }

    @Override
    protected ActiveAlarm<ParameterValue> createNewAlarm(Parameter parameter, Tuple tuple) {
        String ev = tuple.getColumn(ParameterAlarmStreamer.CNAME_LAST_EVENT);
        if (ev == null) {
            return null;
        }

        var notificationType = AlarmNotificationType.valueOf(ev);
        if (notificationType == AlarmNotificationType.TRIGGERED
                || notificationType == AlarmNotificationType.TRIGGERED_PENDING) {
            return ParameterAlarmServer.tupleToActiveAlarm(parameter, tuple);
        } else {
            return null;
        }
    }

    @Override
    protected void addActiveAlarmFromTuple(Mdb mdb, Tuple tuple, Map<Parameter, ActiveAlarm<ParameterValue>> alarms) {
        String pname = tuple.getColumn(StandardTupleDefinitions.PARAMETER_COLUMN);
        var parameter = mdb.getParameter(pname);

        if (parameter == null) {
            log.info("Not adding alarm for {} because parameter was not found in the MDB", pname);
            return;
        }
        alarms.put(parameter, ParameterAlarmServer.tupleToActiveAlarm(parameter, tuple));
    }

    @Override
    protected void processValueUpdate(Parameter parameter, ActiveAlarm<ParameterValue> activeAlarm, Tuple tuple) {
        ParameterValue pv = tuple.getColumn(ParameterAlarmStreamer.CNAME_LAST_VALUE);
        pv.setParameter(parameter);

        activeAlarm.setViolations(tuple.getIntColumn(CNAME_VIOLATION_COUNT));
        activeAlarm.setValueCount(tuple.getIntColumn(CNAME_VALUE_COUNT));
        activeAlarm.setCurrentValue(tuple.getColumn(CNAME_LAST_VALUE));
    }

    @Override
    protected void processSeverityIncrease(Parameter parameter, ActiveAlarm<ParameterValue> activeAlarm, Tuple tuple) {
        ParameterValue pv = tuple.getColumn(ParameterAlarmStreamer.CNAME_SEVERITY_INCREASED);
        pv.setParameter(parameter);
        activeAlarm.setMostSevereValue(pv);
    }

    @Override
    protected String getColNameLastEvent() {
        return ParameterAlarmStreamer.CNAME_LAST_EVENT;
    }

    @Override
    protected String getColNameSeverityIncreased() {
        return ParameterAlarmStreamer.CNAME_SEVERITY_INCREASED;
    }

    @Override
    protected Parameter getSubject(ParameterValue pv) {
        return pv.getParameter();
    }

    @Override
    protected String alarmTableName() {
        return AlarmRecorder.PARAMETER_ALARM_TABLE_NAME;
    }
}
