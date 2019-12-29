package org.yamcs.http.api;

import org.yamcs.Processor;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmSequenceException;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.EventAlarmServer;
import org.yamcs.alarms.EventId;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AbstractAlarmsApi;
import org.yamcs.protobuf.Alarms.AcknowledgeInfo;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Alarms.AlarmNotificationType;
import org.yamcs.protobuf.Alarms.AlarmSeverity;
import org.yamcs.protobuf.Alarms.AlarmType;
import org.yamcs.protobuf.Alarms.ClearInfo;
import org.yamcs.protobuf.Alarms.EditAlarmRequest;
import org.yamcs.protobuf.Alarms.EventAlarmData;
import org.yamcs.protobuf.Alarms.ListAlarmsRequest;
import org.yamcs.protobuf.Alarms.ListAlarmsResponse;
import org.yamcs.protobuf.Alarms.ParameterAlarmData;
import org.yamcs.protobuf.Alarms.ShelveInfo;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;

public class AlarmsApi extends AbstractAlarmsApi<Context> {

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

    @Override
    public void listAlarms(Context ctx, ListAlarmsRequest request, Observer<ListAlarmsResponse> observer) {
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        if (processor.hasAlarmServer()) {
            AlarmServer<Parameter, org.yamcs.parameter.ParameterValue> alarmServer = processor
                    .getParameterRequestManager()
                    .getAlarmServer();
            for (ActiveAlarm<org.yamcs.parameter.ParameterValue> alarm : alarmServer.getActiveAlarms().values()) {
                responseb.addAlarms(toAlarmData(AlarmNotificationType.ACTIVE, alarm, true));
            }
        }
        EventAlarmServer eventAlarmServer = processor.getEventAlarmServer();
        if (eventAlarmServer != null) {
            for (ActiveAlarm<Event> alarm : eventAlarmServer.getActiveAlarms().values()) {
                responseb.addAlarms(toAlarmData(AlarmNotificationType.ACTIVE, alarm, true));
            }
        }
        observer.complete(responseb.build());
    }

    @Override
    public void editAlarm(Context ctx, EditAlarmRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAlarms);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        String alarmName = request.getName();
        if (!alarmName.startsWith("/")) {
            alarmName = "/" + alarmName;
        }
        int seqNum = request.getSeqnum();

        ActiveAlarm<?> activeAlarm = verifyAlarm(processor, alarmName, seqNum);

        if (!request.hasState()) {
            throw new BadRequestException("No state specified");
        }

        String state = null;
        String comment = null;

        if (request.hasState()) {
            state = request.getState();
        }
        if (request.hasComment()) {
            comment = request.getComment();
        }

        // TODO permissions on AlarmServer
        String username = ctx.user.getName();

        AlarmServer alarmServer;

        try {
            if (activeAlarm.triggerValue instanceof ParameterValue) {
                alarmServer = verifyParameterAlarmServer(processor);
            } else if (activeAlarm.triggerValue instanceof Event) {
                alarmServer = verifyEventAlarmServer(processor);
            } else {
                throw new InternalServerErrorException("Can't find alarm server for alarm instance");
            }
            switch (state.toLowerCase()) {
            case "acknowledged":
                alarmServer.acknowledge(activeAlarm, username, processor.getCurrentTime(), comment);
                break;
            case "shelved":
                long shelveDuration = request.hasShelveDuration() ? request.getShelveDuration() : -1;
                alarmServer.shelve(activeAlarm, username, comment, shelveDuration);
                break;
            case "unshelved":
                alarmServer.unshelve(activeAlarm, username);
                break;
            case "cleared":
                alarmServer.clear(activeAlarm, username, processor.getCurrentTime(), comment);
                break;
            default:
                throw new BadRequestException("Unsupported state '" + state + "'");
            }
            observer.complete(Empty.getDefaultInstance());

        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    /**
     * Finds the appropriate alarm server for the alarm.
     * 
     * FIXME why not one namespace and a single server?
     */
    public static ActiveAlarm<?> verifyAlarm(Processor processor, String alarmName, int id)
            throws HttpException {
        try {
            if (processor.hasAlarmServer()) {
                AlarmServer<Parameter, ParameterValue> parameterAlarmServer = processor.getParameterRequestManager()
                        .getAlarmServer();
                XtceDb mdb = XtceDbFactory.getInstance(processor.getInstance());
                Parameter parameter = mdb.getParameter(alarmName);
                if (parameter != null) {
                    ActiveAlarm<ParameterValue> activeAlarm = parameterAlarmServer.getActiveAlarm(parameter, id);
                    if (activeAlarm != null) {
                        return activeAlarm;
                    }
                }
            }
            EventAlarmServer eventAlarmServer = processor.getEventAlarmServer();
            if (eventAlarmServer != null) {
                try {
                    EventId eventId = new EventId(alarmName);
                    ActiveAlarm<Event> activeAlarm = eventAlarmServer.getActiveAlarm(eventId, id);
                    if (activeAlarm != null) {
                        return activeAlarm;
                    }
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
            }
        } catch (AlarmSequenceException e) {
            throw new NotFoundException("Subject is in state of alarm, but alarm id does not match");
        }

        throw new NotFoundException("No active alarm named '" + alarmName + "'");
    }

    static AlarmServer<Parameter, ParameterValue> verifyParameterAlarmServer(Processor processor)
            throws BadRequestException {
        if (!processor.hasAlarmServer()) {
            String instance = processor.getInstance();
            String processorName = processor.getName();
            throw new BadRequestException(
                    "Alarms are not enabled for processor '" + instance + "/" + processorName + "'");
        } else {
            return processor.getParameterRequestManager().getAlarmServer();
        }
    }

    static EventAlarmServer verifyEventAlarmServer(Processor processor) throws BadRequestException {
        if (!processor.hasAlarmServer()) {
            String instance = processor.getInstance();
            String processorName = processor.getName();
            throw new BadRequestException(
                    "Alarms are not enabled for processor '" + instance + "/" + processorName + "'");
        } else {
            return processor.getEventAlarmServer();
        }
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
