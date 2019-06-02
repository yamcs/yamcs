package org.yamcs.web.rest.processor;

import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Alarms.AlarmNotificationType;
import org.yamcs.protobuf.Alarms.AlarmSeverity;
import org.yamcs.protobuf.Alarms.AlarmType;
import org.yamcs.protobuf.Alarms.EventAlarmData;
import org.yamcs.protobuf.Alarms.ParameterAlarmData;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler;
import org.yamcs.web.rest.mdb.XtceToGpbAssembler.DetailLevel;
import org.yamcs.xtce.Parameter;

public class ProcessorHelper {
    static final AlarmSeverity[] PARAM_ALARM_SEVERITY = new AlarmSeverity[20];
    static final AlarmSeverity[] EVENT_ALARM_SEVERITY = new AlarmSeverity[8];
    
    static {
        PARAM_ALARM_SEVERITY[MonitoringResult.WATCH_VALUE] =AlarmSeverity.WATCH;
        PARAM_ALARM_SEVERITY[MonitoringResult.WARNING_VALUE] =AlarmSeverity.WARNING;
        PARAM_ALARM_SEVERITY[MonitoringResult.DISTRESS_VALUE] =AlarmSeverity.DISTRESS;
        PARAM_ALARM_SEVERITY[MonitoringResult.CRITICAL_VALUE] =AlarmSeverity.CRITICAL;
        PARAM_ALARM_SEVERITY[MonitoringResult.SEVERE_VALUE] =AlarmSeverity.SEVERE;
    
        EVENT_ALARM_SEVERITY[EventSeverity.WATCH_VALUE] = AlarmSeverity.WATCH;
        EVENT_ALARM_SEVERITY[EventSeverity.WARNING_VALUE] = AlarmSeverity.WARNING;
        EVENT_ALARM_SEVERITY[EventSeverity.DISTRESS_VALUE] = AlarmSeverity.DISTRESS;
        EVENT_ALARM_SEVERITY[EventSeverity.CRITICAL_VALUE] = AlarmSeverity.CRITICAL;
        EVENT_ALARM_SEVERITY[EventSeverity.SEVERE_VALUE] = AlarmSeverity.SEVERE;
        EVENT_ALARM_SEVERITY[EventSeverity.ERROR_VALUE] = AlarmSeverity.CRITICAL;
        
    }

    public static final ParameterAlarmData toParameterAlarmData(AlarmNotificationType notificationType, ActiveAlarm<ParameterValue> activeAlarm) {
        Parameter parameter = activeAlarm.triggerValue.getParameter();
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(parameter.getQualifiedName())
                .build();
        ParameterAlarmData.Builder alarmb = ParameterAlarmData.newBuilder();
        alarmb.setNotificationType(notificationType);
        alarmb.setSeqNum(activeAlarm.id);
        alarmb.setTriggerValue(activeAlarm.triggerValue.toGpb(parameterId));
        alarmb.setMostSevereValue(activeAlarm.mostSevereValue.toGpb(parameterId));
        alarmb.setCurrentValue(activeAlarm.currentValue.toGpb(parameterId));
        alarmb.setViolations(activeAlarm.violations);
        alarmb.setValueCount(activeAlarm.valueCount);

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
            acknowledgeb.setAcknowledgeTime(TimeEncoding.toProtobufTimestamp(activeAlarm.acknowledgeTime));
            acknowledgeb.setYamcsAcknowledgeTime(activeAlarm.acknowledgeTime);
            acknowledgeb.setAcknowledgeTimeUTC(TimeEncoding.toString(activeAlarm.acknowledgeTime));
            alarmb.setAcknowledgeInfo(acknowledgeb.build());
        }

        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.SUMMARY);
        alarmb.setParameter(pinfo);

        return alarmb.build();
    }
    
    public static final EventAlarmData toEventAlarmData(AlarmNotificationType notificationType, ActiveAlarm<Event> activeAlarm) {
        EventAlarmData.Builder alarmb = EventAlarmData.newBuilder();
        alarmb.setNotificationType(notificationType);
        alarmb.setSeqNum(activeAlarm.id);
        alarmb.setTriggerValue(activeAlarm.triggerValue);
        alarmb.setMostSevereValue(activeAlarm.mostSevereValue);
        alarmb.setCurrentValue(activeAlarm.currentValue);
        alarmb.setViolations(activeAlarm.violations);
        alarmb.setValueCount(activeAlarm.valueCount);

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
            acknowledgeb.setAcknowledgeTime(TimeEncoding.toProtobufTimestamp(activeAlarm.acknowledgeTime));
            alarmb.setAcknowledgeInfo(acknowledgeb.build());
        }
        return alarmb.build();
    }
    
    public static final <T> AlarmData toSummaryAlarmData(AlarmNotificationType notificationType, ActiveAlarm<T> activeAlarm) {
        AlarmData.Builder alarmb = AlarmData.newBuilder();
        
        alarmb.setNotificationType(notificationType);
        alarmb.setSeqNum(activeAlarm.id);
        alarmb.setViolations(activeAlarm.violations);
        if(activeAlarm.mostSevereValue instanceof ParameterValue) {
            alarmb.setType(AlarmType.PARAMETER);
            ParameterValue pv = (ParameterValue) activeAlarm.mostSevereValue;
            alarmb.setId(getAlarmId(pv));
            alarmb.setSeverity(getParameterAlarmSeverity(pv.getMonitoringResult()));
        } else if(activeAlarm.mostSevereValue instanceof Event) {
            alarmb.setType(AlarmType.EVENT);
            Event ev = (Event) activeAlarm.mostSevereValue;
            alarmb.setId(getAlarmId(ev));
            alarmb.setSeverity(getEventAlarmSeverity(ev.getSeverity()));
        }
        
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
            acknowledgeb.setAcknowledgeTime(TimeEncoding.toProtobufTimestamp(activeAlarm.acknowledgeTime));
            alarmb.setAcknowledgeInfo(acknowledgeb.build());
        }
        return alarmb.build();
        
    }

    public static AlarmSeverity getParameterAlarmSeverity(MonitoringResult mr) {
        return PARAM_ALARM_SEVERITY[mr.getNumber()];
    }

    public static AlarmSeverity getEventAlarmSeverity(EventSeverity evSeverity) {
        return EVENT_ALARM_SEVERITY[evSeverity.getNumber()];
    }
    
    static NamedObjectId getAlarmId(ParameterValue pv) {
        return NamedObjectId.newBuilder().setNamespace(pv.getParameter().getSubsystemName())
                .setName(pv.getParameter().getName()).build();
    }
    
    public static NamedObjectId getAlarmId(Event ev) {
        return NamedObjectId.newBuilder().setNamespace(ev.getSource())
        .setName(ev.getType()).build();
    }
}
