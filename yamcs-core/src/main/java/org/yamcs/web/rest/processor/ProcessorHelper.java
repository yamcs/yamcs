package org.yamcs.web.rest.processor;

import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Parameter;

public class ProcessorHelper {

    public static final AlarmData toAlarmData(AlarmData.Type type, ActiveAlarm activeAlarm) {
        Parameter parameter = activeAlarm.triggerValue.getParameter();
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(parameter.getQualifiedName())
                .build();
        AlarmData.Builder alarmb = AlarmData.newBuilder();
        alarmb.setType(type);
        alarmb.setSeqNum(activeAlarm.id);
        alarmb.setTriggerValue(activeAlarm.triggerValue.toGpb(parameterId));
        alarmb.setMostSevereValue(activeAlarm.mostSevereValue.toGpb(parameterId));
        alarmb.setCurrentValue(activeAlarm.currentValue.toGpb(parameterId));
        alarmb.setViolations(activeAlarm.violations);

        if (activeAlarm.acknowledged) {
            AcknowledgeInfo.Builder acknowledgeb = AcknowledgeInfo.newBuilder();
            String username = activeAlarm.usernameThatAcknowledged;
            if (activeAlarm.autoAcknowledge) {
                username = "autoAcknowledged";
            }

            acknowledgeb.setAcknowledgedBy(username);
            if (activeAlarm.message != null) {
                acknowledgeb.setAcknowledgeMessage(activeAlarm.message);
            }
            acknowledgeb.setAcknowledgeTime(activeAlarm.acknowledgeTime);
            acknowledgeb.setAcknowledgeTimeUTC(TimeEncoding.toString(activeAlarm.acknowledgeTime));
            alarmb.setAcknowledgeInfo(acknowledgeb.build());
        }

        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.SUMMARY);
        alarmb.setParameter(pinfo);

        return alarmb.build();
    }
}
