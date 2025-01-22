package org.yamcs.alarms;

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

        if (AlarmNotificationType.valueOf(ev) == AlarmNotificationType.TRIGGERED) {
            return ParameterAlarmServer.tupleToActiveAlarm(parameter, tuple);
        } else {
            return null;
        }
    }

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
    protected String getColNameLastEvent() {
        return ParameterAlarmStreamer.CNAME_LAST_EVENT;
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
