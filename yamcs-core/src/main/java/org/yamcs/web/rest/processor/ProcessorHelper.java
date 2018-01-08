package org.yamcs.web.rest.processor;

import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.Privilege;
import org.yamcs.utils.TimeEncoding;

public class ProcessorHelper {

    public static final AlarmData toAlarmData(AlarmData.Type type, ActiveAlarm activeAlarm) {
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(activeAlarm.triggerValue.getParameter().getQualifiedName())
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
            if (username == null) {
                username = (activeAlarm.autoAcknowledge) ? "autoAcknowledged" : Privilege.getDefaultUser();
            }

            acknowledgeb.setAcknowledgedBy(username);
            if (activeAlarm.message != null) {
                acknowledgeb.setAcknowledgeMessage(activeAlarm.message);
            }
            acknowledgeb.setAcknowledgeTime(activeAlarm.acknowledgeTime);
            acknowledgeb.setAcknowledgeTimeUTC(TimeEncoding.toString(activeAlarm.acknowledgeTime));
            alarmb.setAcknowledgeInfo(acknowledgeb.build());
        }

        return alarmb.build();
    }
}
