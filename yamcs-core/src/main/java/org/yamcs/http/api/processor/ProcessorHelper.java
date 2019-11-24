package org.yamcs.http.api.processor;

import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.EventId;
import org.yamcs.http.api.XtceToGpbAssembler;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Alarms.AlarmNotificationType;
import org.yamcs.protobuf.Alarms.AlarmSeverity;
import org.yamcs.protobuf.Alarms.AlarmType;
import org.yamcs.protobuf.Alarms.ClearInfo;
import org.yamcs.protobuf.Alarms.EventAlarmData;
import org.yamcs.protobuf.Alarms.ParameterAlarmData;
import org.yamcs.protobuf.Alarms.ShelveInfo;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;

import com.google.protobuf.Timestamp;

public class ProcessorHelper {
    static final AlarmSeverity[] PARAM_ALARM_SEVERITY = new AlarmSeverity[20];
    static final AlarmSeverity[] EVENT_ALARM_SEVERITY = new AlarmSeverity[8];

    static {
        PARAM_ALARM_SEVERITY[MonitoringResult.WATCH_VALUE] = AlarmSeverity.WATCH;
        PARAM_ALARM_SEVERITY[MonitoringResult.WARNING_VALUE] = AlarmSeverity.WARNING;
        PARAM_ALARM_SEVERITY[MonitoringResult.DISTRESS_VALUE] = AlarmSeverity.DISTRESS;
        PARAM_ALARM_SEVERITY[MonitoringResult.CRITICAL_VALUE] = AlarmSeverity.CRITICAL;
        PARAM_ALARM_SEVERITY[MonitoringResult.SEVERE_VALUE] = AlarmSeverity.SEVERE;

        EVENT_ALARM_SEVERITY[EventSeverity.WATCH_VALUE] = AlarmSeverity.WATCH;
        EVENT_ALARM_SEVERITY[EventSeverity.WARNING_VALUE] = AlarmSeverity.WARNING;
        EVENT_ALARM_SEVERITY[EventSeverity.DISTRESS_VALUE] = AlarmSeverity.DISTRESS;
        EVENT_ALARM_SEVERITY[EventSeverity.CRITICAL_VALUE] = AlarmSeverity.CRITICAL;
        EVENT_ALARM_SEVERITY[EventSeverity.SEVERE_VALUE] = AlarmSeverity.SEVERE;
        EVENT_ALARM_SEVERITY[EventSeverity.ERROR_VALUE] = AlarmSeverity.CRITICAL;

    }

    private static final ParameterAlarmData toParameterAlarmData(ActiveAlarm<ParameterValue> activeAlarm) {
        Parameter parameter = activeAlarm.triggerValue.getParameter();
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(parameter.getQualifiedName())
                .build();
        ParameterAlarmData.Builder alarmb = ParameterAlarmData.newBuilder();
        alarmb.setTriggerValue(activeAlarm.triggerValue.toGpb(parameterId));
        alarmb.setMostSevereValue(activeAlarm.mostSevereValue.toGpb(parameterId));
        alarmb.setCurrentValue(activeAlarm.currentValue.toGpb(parameterId));

        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.SUMMARY);
        alarmb.setParameter(pinfo);

        return alarmb.build();
    }

    private static final EventAlarmData toEventAlarmData(ActiveAlarm<Event> activeAlarm) {
        EventAlarmData.Builder alarmb = EventAlarmData.newBuilder();
        alarmb.setTriggerEvent(activeAlarm.triggerValue);
        alarmb.setMostSevereEvent(activeAlarm.mostSevereValue);
        alarmb.setCurrentEvent(activeAlarm.currentValue);

        return alarmb.build();
    }

    public static final <T> AlarmData toAlarmData(AlarmNotificationType notificationType,
            ActiveAlarm<T> activeAlarm, boolean detail) {
        AlarmData.Builder alarmb = AlarmData.newBuilder();

        alarmb.setNotificationType(notificationType);
        alarmb.setSeqNum(activeAlarm.getId());

        alarmb.setAcknowledged(activeAlarm.isAcknowledged());
        alarmb.setProcessOK(activeAlarm.isProcessOK());
        alarmb.setTriggered(activeAlarm.isTriggered());

        alarmb.setViolations(activeAlarm.violations);
        alarmb.setCount(activeAlarm.valueCount);

        if (activeAlarm.mostSevereValue instanceof ParameterValue) {
            alarmb.setType(AlarmType.PARAMETER);
            ParameterValue pv = (ParameterValue) activeAlarm.mostSevereValue;
            alarmb.setId(getAlarmId(pv));
            alarmb.setSeverity(getParameterAlarmSeverity(pv.getMonitoringResult()));
            ParameterValue triggerPv = (ParameterValue) activeAlarm.triggerValue;
            Timestamp triggerTime = TimeEncoding.toProtobufTimestamp(triggerPv.getGenerationTime());
            alarmb.setTriggerTime(triggerTime);
            if (detail) {
                @SuppressWarnings("unchecked")
                ParameterAlarmData parameterDetail = toParameterAlarmData(
                        (ActiveAlarm<ParameterValue>) activeAlarm);
                alarmb.setParameterDetail(parameterDetail);
            }
        } else if (activeAlarm.mostSevereValue instanceof Event) {
            alarmb.setType(AlarmType.EVENT);
            Event ev = (Event) activeAlarm.mostSevereValue;
            alarmb.setId(getAlarmId(ev));
            alarmb.setSeverity(getEventAlarmSeverity(ev.getSeverity()));
            Timestamp triggerTime = TimeEncoding.toProtobufTimestamp(ev.getGenerationTime());
            alarmb.setTriggerTime(triggerTime);
            if (detail) {
                @SuppressWarnings("unchecked")
                EventAlarmData eventDetail = toEventAlarmData(
                        (ActiveAlarm<Event>) activeAlarm);
                alarmb.setEventDetail(eventDetail);
            }
        }

        if (activeAlarm.isNormal()) {
            long ct = activeAlarm.getClearTime();
            if (ct != TimeEncoding.INVALID_INSTANT) {
                ClearInfo.Builder cib = ClearInfo.newBuilder();
                cib.setClearTime(TimeEncoding.toProtobufTimestamp(ct));
                if (activeAlarm.getUsernameThatCleared() != null) {
                    cib.setClearedBy(activeAlarm.getUsernameThatCleared());
                }
                if (activeAlarm.getClearMessage() != null) {
                    cib.setClearMessage(activeAlarm.getClearMessage());
                }
                alarmb.setClearInfo(cib.build());
            }
        } else {
            if (activeAlarm.isAcknowledged()) {
                AcknowledgeInfo.Builder acknowledgeb = AcknowledgeInfo.newBuilder();
                String username = activeAlarm.usernameThatAcknowledged;
                if (activeAlarm.isAutoAcknowledge()) {
                    username = "autoAcknowledged";
                }

                acknowledgeb.setAcknowledgedBy(username);
                if (activeAlarm.getAckMessage() != null) {
                    acknowledgeb.setAcknowledgeMessage(activeAlarm.getAckMessage());
                }
                acknowledgeb.setAcknowledgeTime(TimeEncoding.toProtobufTimestamp(activeAlarm.acknowledgeTime));
                alarmb.setAcknowledgeInfo(acknowledgeb.build());
            }

            if (activeAlarm.isShelved()) {
                ShelveInfo.Builder sib = ShelveInfo.newBuilder();
                long exp = activeAlarm.getShelveExpiration();
                if (exp != -1) {
                    sib.setShelveExpiration(TimeEncoding.toProtobufTimestamp(exp));
                }
                sib.setShelvedBy(activeAlarm.getShelveUsername());
                sib.setShelveTime(TimeEncoding.toProtobufTimestamp(activeAlarm.getShelveTime()));
                if (activeAlarm.getShelveMessage() != null) {
                    sib.setShelveMessage(activeAlarm.getShelveMessage());
                }
                alarmb.setShelveInfo(sib.build());
            }
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
        String source = ev.getSource();
        if (source.startsWith("/")) {
            return NamedObjectId.newBuilder()
                    .setNamespace(source)
                    .setName(ev.getType())
                    .build();
        } else {
            return NamedObjectId.newBuilder()
                    .setNamespace(EventId.DEFAULT_NAMESPACE + source)
                    .setName(ev.getType())
                    .build();
        }
    }
}
