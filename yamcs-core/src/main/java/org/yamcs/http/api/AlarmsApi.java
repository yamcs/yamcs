package org.yamcs.http.api;

import static org.yamcs.alarms.AlarmStreamer.CNAME_CLEARED_BY;
import static org.yamcs.alarms.AlarmStreamer.CNAME_CLEARED_TIME;
import static org.yamcs.alarms.AlarmStreamer.CNAME_CLEAR_MSG;
import static org.yamcs.alarms.AlarmStreamer.CNAME_SHELVED_BY;
import static org.yamcs.alarms.AlarmStreamer.CNAME_SHELVED_MSG;
import static org.yamcs.alarms.AlarmStreamer.CNAME_SHELVED_TIME;
import static org.yamcs.alarms.AlarmStreamer.CNAME_TRIGGER_TIME;

import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.yamcs.Processor;
import org.yamcs.StandardTupleDefinitions;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmListener;
import org.yamcs.alarms.AlarmSequenceException;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.AlarmStreamer;
import org.yamcs.alarms.EventAlarmServer;
import org.yamcs.alarms.EventAlarmStreamer;
import org.yamcs.alarms.EventId;
import org.yamcs.alarms.ParameterAlarmStreamer;
import org.yamcs.api.Observer;
import org.yamcs.archive.AlarmRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.XtceToGpbAssembler.DetailLevel;
import org.yamcs.http.audit.AuditLog;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AcknowledgeInfo;
import org.yamcs.protobuf.AlarmData;
import org.yamcs.protobuf.AlarmNotificationType;
import org.yamcs.protobuf.AlarmSeverity;
import org.yamcs.protobuf.AlarmType;
import org.yamcs.protobuf.ClearInfo;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.EventAlarmData;
import org.yamcs.protobuf.Mdb.ParameterInfo;
import org.yamcs.protobuf.ParameterAlarmData;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.protobuf.ShelveInfo;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.alarms.AbstractAlarmsApi;
import org.yamcs.protobuf.alarms.AcknowledgeAlarmRequest;
import org.yamcs.protobuf.alarms.ClearAlarmRequest;
import org.yamcs.protobuf.alarms.EditAlarmRequest;
import org.yamcs.protobuf.alarms.GlobalAlarmStatus;
import org.yamcs.protobuf.alarms.ListAlarmsRequest;
import org.yamcs.protobuf.alarms.ListAlarmsResponse;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsRequest;
import org.yamcs.protobuf.alarms.ListProcessorAlarmsResponse;
import org.yamcs.protobuf.alarms.ShelveAlarmRequest;
import org.yamcs.protobuf.alarms.SubscribeAlarmsRequest;
import org.yamcs.protobuf.alarms.SubscribeGlobalStatusRequest;
import org.yamcs.protobuf.alarms.UnshelveAlarmRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.protobuf.Db;

import com.google.gson.Gson;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;

public class AlarmsApi extends AbstractAlarmsApi<Context> {

    private static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    private static final AlarmSeverity[] PARAM_ALARM_SEVERITY = new AlarmSeverity[20];
    private static final AlarmSeverity[] EVENT_ALARM_SEVERITY = new AlarmSeverity[8];
    public static Map<org.yamcs.alarms.AlarmNotificationType, AlarmNotificationType> protoNotificationType = new EnumMap<>(
            org.yamcs.alarms.AlarmNotificationType.class);

    static {
        PARAM_ALARM_SEVERITY[MonitoringResult.WATCH_VALUE] = AlarmSeverity.WATCH;
        PARAM_ALARM_SEVERITY[MonitoringResult.WARNING_VALUE] = AlarmSeverity.WARNING;
        PARAM_ALARM_SEVERITY[MonitoringResult.DISTRESS_VALUE] = AlarmSeverity.DISTRESS;
        PARAM_ALARM_SEVERITY[MonitoringResult.CRITICAL_VALUE] = AlarmSeverity.CRITICAL;
        PARAM_ALARM_SEVERITY[MonitoringResult.SEVERE_VALUE] = AlarmSeverity.SEVERE;

        EVENT_ALARM_SEVERITY[EventSeverity.WATCH_VALUE] = AlarmSeverity.WATCH;
        EVENT_ALARM_SEVERITY[EventSeverity.WARNING_VALUE] = AlarmSeverity.WARNING;
        EVENT_ALARM_SEVERITY[EventSeverity.WARNING_NEW_VALUE] = AlarmSeverity.WARNING;
        EVENT_ALARM_SEVERITY[EventSeverity.DISTRESS_VALUE] = AlarmSeverity.DISTRESS;
        EVENT_ALARM_SEVERITY[EventSeverity.CRITICAL_VALUE] = AlarmSeverity.CRITICAL;
        EVENT_ALARM_SEVERITY[EventSeverity.SEVERE_VALUE] = AlarmSeverity.SEVERE;
        EVENT_ALARM_SEVERITY[EventSeverity.ERROR_VALUE] = AlarmSeverity.CRITICAL;

        protoNotificationType.put(org.yamcs.alarms.AlarmNotificationType.ACKNOWLEDGED,
                AlarmNotificationType.ACKNOWLEDGED);
        protoNotificationType.put(org.yamcs.alarms.AlarmNotificationType.CLEARED, AlarmNotificationType.CLEARED);
        protoNotificationType.put(org.yamcs.alarms.AlarmNotificationType.RESET, AlarmNotificationType.RESET);
        protoNotificationType.put(org.yamcs.alarms.AlarmNotificationType.RTN, AlarmNotificationType.RTN);
        protoNotificationType.put(org.yamcs.alarms.AlarmNotificationType.SHELVED, AlarmNotificationType.SHELVED);
        protoNotificationType.put(org.yamcs.alarms.AlarmNotificationType.TRIGGERED, AlarmNotificationType.TRIGGERED);
        protoNotificationType.put(org.yamcs.alarms.AlarmNotificationType.UNSHELVED, AlarmNotificationType.UNSHELVED);
    }

    private AuditLog auditLog;

    public AlarmsApi(AuditLog auditLog) {
        this.auditLog = auditLog;
        auditLog.addPrivilegeChecker(getClass().getSimpleName(), user -> {
            return user.hasSystemPrivilege(SystemPrivilege.ReadAlarms);
        });
    }

    /**
     * List the alarms including the old ones not active anymore
     */
    @Override
    public void listAlarms(Context ctx, ListAlarmsRequest request, Observer<ListAlarmsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadAlarms);
        String instance = InstancesApi.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        AlarmsPageToken nextToken = null;
        if (request.hasNext()) {
            nextToken = AlarmsPageToken.decode(request.getNext());
        }

        SqlBuilder sqlbParam = new SqlBuilder(AlarmRecorder.PARAMETER_ALARM_TABLE_NAME);
        SqlBuilder sqlbEvent = new SqlBuilder(AlarmRecorder.EVENT_ALARM_TABLE_NAME);

        if (request.hasStart()) {
            sqlbParam.whereColAfterOrEqual(CNAME_TRIGGER_TIME, request.getStart());
            sqlbEvent.whereColAfterOrEqual(CNAME_TRIGGER_TIME, request.getStart());
        }
        if (request.hasStop()) {
            sqlbParam.whereColBefore(CNAME_TRIGGER_TIME, request.getStop());
            sqlbEvent.whereColBefore(CNAME_TRIGGER_TIME, request.getStop());
        }
        if (nextToken != null) {
            // TODO this currently ignores the parameter/eventSource column (also part of the key)
            if (desc) {
                sqlbParam.where("(triggerTime < ? or (triggerTime = ? and seqNum < ?))",
                        nextToken.triggerTime, nextToken.triggerTime, nextToken.seqNum);
                sqlbEvent.where("(triggerTime < ? or (triggerTime = ? and seqNum < ?))",
                        nextToken.triggerTime, nextToken.triggerTime, nextToken.seqNum);
            } else {
                sqlbParam.where("(triggerTime > ? or (triggerTime = ? and seqNum > ?))",
                        nextToken.triggerTime, nextToken.triggerTime, nextToken.seqNum);
                sqlbEvent.where("(triggerTime > ? or (triggerTime = ? and seqNum > ?))",
                        nextToken.triggerTime, nextToken.triggerTime, nextToken.seqNum);
            }
        }

        if (request.hasName()) {
            String alarmName = request.getName();
            if (!alarmName.startsWith("/")) {
                alarmName = "/" + alarmName;
            }
            sqlbParam.where("parameter = ?", alarmName);
            sqlbEvent.where("eventSource = ?", alarmName);
        }

        sqlbParam.descend(desc);
        sqlbEvent.descend(desc);

        var responseb = ListAlarmsResponse.newBuilder();
        // Add 1 to the limit, to detect need for continuation token
        String q = "MERGE (" + sqlbParam.toString() + "), (" + sqlbEvent.toString() + ") USING " + CNAME_TRIGGER_TIME
                + " ORDER DESC LIMIT " + pos + "," + (limit + 1L);

        List<Object> sqlArgs = new ArrayList<>(sqlbParam.getQueryArguments());
        sqlArgs.addAll(sqlbEvent.getQueryArguments());
        StreamFactory.stream(instance, q, sqlArgs, new StreamSubscriber() {

            Tuple last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (++count <= limit) {
                    AlarmData alarm = tupleToAlarmData(tuple);
                    responseb.addAlarms(alarm);
                    last = tuple;
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    var triggerTime = last.getTimestampColumn(AlarmStreamer.CNAME_TRIGGER_TIME);
                    var seqNum = last.getIntColumn(AlarmStreamer.CNAME_SEQ_NUM);
                    var token = new AlarmsPageToken(triggerTime, seqNum);
                    responseb.setContinuationToken(token.encodeAsString());
                }
                observer.complete(responseb.build());
            }
        });
    }

    /**
     * List the active alarms
     */
    @Override
    public void listProcessorAlarms(Context ctx, ListProcessorAlarmsRequest request,
            Observer<ListProcessorAlarmsResponse> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadAlarms);
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        ListProcessorAlarmsResponse.Builder responseb = ListProcessorAlarmsResponse.newBuilder();
        if (processor.hasAlarmServer()) {
            AlarmServer<Parameter, org.yamcs.parameter.ParameterValue> alarmServer = processor
                    .getParameterProcessorManager()
                    .getAlarmServer();
            for (ActiveAlarm<org.yamcs.parameter.ParameterValue> alarm : alarmServer.getActiveAlarms().values()) {
                responseb.addAlarms(toAlarmData(AlarmNotificationType.ACTIVE, alarm, true));
            }
        }
        EventAlarmServer eventAlarmServer = processor.getEventAlarmServer();
        if (eventAlarmServer != null) {
            for (ActiveAlarm<Db.Event> alarm : eventAlarmServer.getActiveAlarms().values()) {
                responseb.addAlarms(toAlarmData(AlarmNotificationType.ACTIVE, alarm, true));
            }
        }
        observer.complete(responseb.build());
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
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

        String state = request.getState();
        String comment = request.hasComment() ? request.getComment() : null;

        // TODO permissions on AlarmServer
        String username = ctx.user.getName();

        AlarmServer alarmServer;

        try {
            if (activeAlarm.getTriggerValue() instanceof ParameterValue) {
                alarmServer = verifyParameterAlarmServer(processor);
            } else if (activeAlarm.getTriggerValue() instanceof Db.Event) {
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

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void acknowledgeAlarm(Context ctx, AcknowledgeAlarmRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAlarms);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        String alarmName = request.getAlarm();
        if (!alarmName.startsWith("/")) {
            alarmName = "/" + alarmName;
        }
        int seqNum = request.getSeqnum();

        ActiveAlarm<?> activeAlarm = verifyAlarm(processor, alarmName, seqNum);
        String comment = request.hasComment() ? request.getComment() : null;
        String username = ctx.user.getName();
        try {
            AlarmServer alarmServer;
            if (activeAlarm.getTriggerValue() instanceof ParameterValue) {
                alarmServer = verifyParameterAlarmServer(processor);
            } else if (activeAlarm.getTriggerValue() instanceof Db.Event) {
                alarmServer = verifyEventAlarmServer(processor);
            } else {
                throw new InternalServerErrorException("Can't find alarm server for alarm instance");
            }

            alarmServer.acknowledge(activeAlarm, username, processor.getCurrentTime(), comment);
            logAlarmAction(ctx, request, processor, activeAlarm, "acknowledged");
            observer.complete(Empty.getDefaultInstance());
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void shelveAlarm(Context ctx, ShelveAlarmRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAlarms);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        String alarmName = request.getAlarm();
        if (!alarmName.startsWith("/")) {
            alarmName = "/" + alarmName;
        }
        int seqNum = request.getSeqnum();

        ActiveAlarm<?> activeAlarm = verifyAlarm(processor, alarmName, seqNum);
        String comment = request.hasComment() ? request.getComment() : null;
        String username = ctx.user.getName();
        try {
            AlarmServer alarmServer;
            if (activeAlarm.getTriggerValue() instanceof ParameterValue) {
                alarmServer = verifyParameterAlarmServer(processor);
            } else if (activeAlarm.getTriggerValue() instanceof Db.Event) {
                alarmServer = verifyEventAlarmServer(processor);
            } else {
                throw new InternalServerErrorException("Can't find alarm server for alarm instance");
            }

            long shelveDuration = request.hasShelveDuration() ? request.getShelveDuration() : -1;
            alarmServer.shelve(activeAlarm, username, comment, shelveDuration);
            logAlarmAction(ctx, request, processor, activeAlarm, "shelved");
            observer.complete(Empty.getDefaultInstance());
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void unshelveAlarm(Context ctx, UnshelveAlarmRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAlarms);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        String alarmName = request.getAlarm();
        if (!alarmName.startsWith("/")) {
            alarmName = "/" + alarmName;
        }
        int seqNum = request.getSeqnum();

        ActiveAlarm<?> activeAlarm = verifyAlarm(processor, alarmName, seqNum);
        String username = ctx.user.getName();
        try {
            AlarmServer alarmServer;
            if (activeAlarm.getTriggerValue() instanceof ParameterValue) {
                alarmServer = verifyParameterAlarmServer(processor);
            } else if (activeAlarm.getTriggerValue() instanceof Db.Event) {
                alarmServer = verifyEventAlarmServer(processor);
            } else {
                throw new InternalServerErrorException("Can't find alarm server for alarm instance");
            }

            alarmServer.unshelve(activeAlarm, username);
            logAlarmAction(ctx, request, processor, activeAlarm, "unshelved");
            observer.complete(Empty.getDefaultInstance());
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private void logAlarmAction(Context ctx, Message request, Processor processor, ActiveAlarm<?> alarm,
            String action) {
        if (alarm.getTriggerValue() instanceof ParameterValue) {
            String parameter = ((ParameterValue) alarm.getTriggerValue()).getParameterQualifiedName();
            auditLog.addRecord(ctx, request, String.format(
                    "Alarm for parameter '%s' %s for processor '%s'",
                    parameter, action, processor.getName()));
        } else if (alarm.getTriggerValue() instanceof Db.Event) {
            Db.Event event = (Db.Event) alarm.getTriggerValue();
            String alarmName = event.getSource();
            if (event.hasType()) {
                alarmName += "/" + event.getType();
            }
            auditLog.addRecord(ctx, request, String.format(
                    "Alarm for event '%s' %s for processor '%s'",
                    alarmName, action, processor.getName()));
        } else {
            throw new IllegalStateException("Unexpected alarm type");
        }
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void clearAlarm(Context ctx, ClearAlarmRequest request, Observer<Empty> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlAlarms);

        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
        String alarmName = request.getAlarm();
        if (!alarmName.startsWith("/")) {
            alarmName = "/" + alarmName;
        }
        int seqNum = request.getSeqnum();

        ActiveAlarm<?> activeAlarm = verifyAlarm(processor, alarmName, seqNum);
        String comment = request.hasComment() ? request.getComment() : null;
        String username = ctx.user.getName();
        try {
            AlarmServer alarmServer;
            if (activeAlarm.getTriggerValue() instanceof ParameterValue) {
                alarmServer = verifyParameterAlarmServer(processor);
            } else if (activeAlarm.getTriggerValue() instanceof Db.Event) {
                alarmServer = verifyEventAlarmServer(processor);
            } else {
                throw new InternalServerErrorException("Can't find alarm server for alarm instance");
            }

            alarmServer.clear(activeAlarm, username, processor.getCurrentTime(), comment);
            logAlarmAction(ctx, request, processor, activeAlarm, "cleared");
            observer.complete(Empty.getDefaultInstance());
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void subscribeAlarms(Context ctx, SubscribeAlarmsRequest request, Observer<AlarmData> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadAlarms);
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());

        List<AlarmServer<?, ?>> alarmServers = new ArrayList<>();
        if (processor.hasAlarmServer()) {
            alarmServers.add(processor.getParameterProcessorManager().getAlarmServer());
        }
        if (processor.getEventAlarmServer() != null) {
            alarmServers.add(processor.getEventAlarmServer());
        }

        boolean sendDetail = true;

        AlarmListener listener = new AlarmListener() {

            @Override
            public void notifyUpdate(org.yamcs.alarms.AlarmNotificationType notificationType, ActiveAlarm activeAlarm) {
                AlarmNotificationType type = protoNotificationType.get(notificationType);
                AlarmData alarmData = toAlarmData(type, activeAlarm, sendDetail);
                observer.next(alarmData);
            }

            @Override
            public void notifySeverityIncrease(ActiveAlarm activeAlarm) {
                AlarmData alarmData = toAlarmData(AlarmNotificationType.SEVERITY_INCREASED, activeAlarm, sendDetail);
                observer.next(alarmData);
            }

            @Override
            public void notifyValueUpdate(ActiveAlarm activeAlarm) {
                AlarmData alarmData = toAlarmData(AlarmNotificationType.VALUE_UPDATED, activeAlarm, sendDetail);
                observer.next(alarmData);
            }
        };

        observer.setCancelHandler(() -> {
            alarmServers.forEach(alarmServer -> alarmServer.removeAlarmListener(listener));
        });
        for (AlarmServer<?, ?> alarmServer : alarmServers) {
            for (ActiveAlarm<?> activeAlarm : alarmServer.getActiveAlarms().values()) {
                AlarmData alarmData = toAlarmData(AlarmNotificationType.ACTIVE, activeAlarm, sendDetail);
                observer.next(alarmData);
            }
            alarmServer.addAlarmListener(listener);
        }
    }

    @Override
    public void subscribeGlobalStatus(Context ctx, SubscribeGlobalStatusRequest request,
            Observer<GlobalAlarmStatus> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadAlarms);
        Processor processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());

        List<AlarmServer<?, ?>> alarmServers = new ArrayList<>();
        if (processor.hasAlarmServer()) {
            alarmServers.add(processor.getParameterProcessorManager().getAlarmServer());
        }
        if (processor.getEventAlarmServer() != null) {
            alarmServers.add(processor.getEventAlarmServer());
        }

        AtomicReference<GlobalAlarmStatus> oldStatusRef = new AtomicReference<>();
        ScheduledFuture<?> future = timer.scheduleAtFixedRate(() -> {
            int unacknowledgedCount = 0;
            boolean unacknowledgedActive = false;
            int acknowledgedCount = 0;
            boolean acknowledgedActive = false;
            int shelvedCount = 0;
            boolean shelvedActive = false;

            for (AlarmServer<?, ?> alarmServer : alarmServers) {
                for (ActiveAlarm<?> alarm : alarmServer.getActiveAlarms().values()) {
                    if (alarm.isTriggered() || !alarm.isAcknowledged()) {
                        if (alarm.isShelved()) {
                            shelvedCount++;
                            shelvedActive |= !alarm.isProcessOK();
                        } else if (alarm.isAcknowledged()) {
                            acknowledgedCount++;
                            acknowledgedActive |= !alarm.isProcessOK();
                        } else {
                            unacknowledgedCount++;
                            unacknowledgedActive |= !alarm.isProcessOK();
                        }
                    }
                }
            }

            GlobalAlarmStatus status = GlobalAlarmStatus.newBuilder()
                    .setUnacknowledgedCount(unacknowledgedCount)
                    .setUnacknowledgedActive(unacknowledgedActive)
                    .setAcknowledgedCount(acknowledgedCount)
                    .setAcknowledgedActive(acknowledgedActive)
                    .setShelvedCount(shelvedCount)
                    .setShelvedActive(shelvedActive)
                    .build();

            GlobalAlarmStatus oldStatus = oldStatusRef.get();
            if (!status.equals(oldStatus)) {
                observer.next(status);
                oldStatusRef.set(status);
            }
        }, 0, 1, TimeUnit.SECONDS);
        observer.setCancelHandler(() -> future.cancel(false));
    }

    /**
     * Finds the appropriate alarm server for the alarm.
     * <p>
     * FIXME why not one namespace and a single server?
     */
    public static ActiveAlarm<?> verifyAlarm(Processor processor, String alarmName, int id)
            throws HttpException {
        try {
            if (processor.hasAlarmServer()) {
                AlarmServer<Parameter, ParameterValue> parameterAlarmServer = processor.getParameterProcessorManager()
                        .getAlarmServer();
                Mdb mdb = MdbFactory.getInstance(processor.getInstance());
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
                    ActiveAlarm<Db.Event> activeAlarm = eventAlarmServer.getActiveAlarm(eventId, id);
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
            return processor.getParameterProcessorManager().getAlarmServer();
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
        Parameter parameter = activeAlarm.getTriggerValue().getParameter();
        NamedObjectId parameterId = NamedObjectId.newBuilder()
                .setName(parameter.getQualifiedName())
                .build();
        ParameterAlarmData.Builder alarmb = ParameterAlarmData.newBuilder();
        alarmb.setTriggerValue(activeAlarm.getTriggerValue().toGpb(parameterId));
        alarmb.setMostSevereValue(activeAlarm.getMostSevereValue().toGpb(parameterId));
        alarmb.setCurrentValue(activeAlarm.getCurrentValue().toGpb(parameterId));

        ParameterInfo pinfo = XtceToGpbAssembler.toParameterInfo(parameter, DetailLevel.SUMMARY);
        alarmb.setParameter(pinfo);

        return alarmb.build();
    }

    private static final EventAlarmData toEventAlarmData(ActiveAlarm<Db.Event> activeAlarm) {
        EventAlarmData.Builder alarmb = EventAlarmData.newBuilder();
        alarmb.setTriggerEvent(EventsApi.fromDbEvent(activeAlarm.getTriggerValue()));
        alarmb.setMostSevereEvent(EventsApi.fromDbEvent(activeAlarm.getMostSevereValue()));
        alarmb.setCurrentEvent(EventsApi.fromDbEvent(activeAlarm.getCurrentValue()));

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

        alarmb.setViolations(activeAlarm.getViolations());
        alarmb.setCount(activeAlarm.getValueCount());

        if (activeAlarm.getMostSevereValue() instanceof ParameterValue) {
            alarmb.setType(AlarmType.PARAMETER);
            ParameterValue pv = (ParameterValue) activeAlarm.getMostSevereValue();
            alarmb.setId(getAlarmId(pv));
            alarmb.setSeverity(getParameterAlarmSeverity(pv.getMonitoringResult()));
            ParameterValue triggerPv = (ParameterValue) activeAlarm.getTriggerValue();
            Timestamp triggerTime = TimeEncoding.toProtobufTimestamp(triggerPv.getGenerationTime());
            alarmb.setTriggerTime(triggerTime);
            if (detail) {
                @SuppressWarnings("unchecked")
                ParameterAlarmData parameterDetail = toParameterAlarmData(
                        (ActiveAlarm<ParameterValue>) activeAlarm);
                alarmb.setParameterDetail(parameterDetail);
            }
        } else if (activeAlarm.getMostSevereValue() instanceof Db.Event) {
            alarmb.setType(AlarmType.EVENT);
            Db.Event ev = (Db.Event) activeAlarm.getMostSevereValue();
            alarmb.setId(getAlarmId(ev));
            alarmb.setSeverity(getEventAlarmSeverity(ev.getSeverity()));
            Timestamp triggerTime = TimeEncoding.toProtobufTimestamp(ev.getGenerationTime());
            alarmb.setTriggerTime(triggerTime);
            if (detail) {
                @SuppressWarnings("unchecked")
                EventAlarmData eventDetail = toEventAlarmData(
                        (ActiveAlarm<Db.Event>) activeAlarm);
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
                String username = activeAlarm.getUsernameThatAcknowledged();
                if (activeAlarm.isAutoAcknowledge()) {
                    username = "autoAcknowledged";
                }

                acknowledgeb.setAcknowledgedBy(username);
                if (activeAlarm.getAckMessage() != null) {
                    acknowledgeb.setAcknowledgeMessage(activeAlarm.getAckMessage());
                }
                acknowledgeb.setAcknowledgeTime(TimeEncoding.toProtobufTimestamp(activeAlarm.getAcknowledgeTime()));
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

    public static NamedObjectId getAlarmId(Db.Event ev) {
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

    private static ParameterAlarmData tupleToParameterAlarmData(Tuple tuple) {
        ParameterAlarmData.Builder alarmb = ParameterAlarmData.newBuilder();

        ParameterValue pval = (ParameterValue) tuple.getColumn(ParameterAlarmStreamer.CNAME_TRIGGER);
        String paraFqn = (String) tuple.getColumn(StandardTupleDefinitions.PARAMETER_COLUMN);

        NamedObjectId id = NamedObjectId.newBuilder().setName(paraFqn).build();
        alarmb.setTriggerValue(pval.toGpb(id));

        if (tuple.hasColumn(ParameterAlarmStreamer.CNAME_SEVERITY_INCREASED)) {
            pval = (ParameterValue) tuple.getColumn(ParameterAlarmStreamer.CNAME_SEVERITY_INCREASED);
            alarmb.setMostSevereValue(pval.toGpb(id));
        }

        return alarmb.build();
    }

    private static EventAlarmData tupleToEventAlarmData(Tuple tuple) {
        EventAlarmData.Builder eventb = EventAlarmData.newBuilder();

        Db.Event event = (Db.Event) tuple.getColumn(EventAlarmStreamer.CNAME_TRIGGER);
        eventb.setTriggerEvent(EventsApi.fromDbEvent(event));

        if (tuple.hasColumn(EventAlarmStreamer.CNAME_SEVERITY_INCREASED)) {
            event = (Db.Event) tuple.getColumn(EventAlarmStreamer.CNAME_SEVERITY_INCREASED);
            eventb.setMostSevereEvent(EventsApi.fromDbEvent(event));
        }

        return eventb.build();
    }

    private static AlarmData tupleToAlarmData(Tuple tuple) {
        AlarmData.Builder alarmb = AlarmData.newBuilder();
        alarmb.setSeqNum((int) tuple.getColumn(AlarmStreamer.CNAME_SEQ_NUM));
        setAckInfo(alarmb, tuple);
        setClearInfo(alarmb, tuple);
        setShelveInfo(alarmb, tuple);

        if (tuple.hasColumn(AlarmStreamer.CNAME_UPDATE_TIME)) {
            long updateTime = tuple.getTimestampColumn(AlarmStreamer.CNAME_UPDATE_TIME);
            alarmb.setUpdateTime(TimeEncoding.toProtobufTimestamp(updateTime));
        }
        if (tuple.hasColumn(AlarmStreamer.CNAME_VALUE_COUNT)) {
            int valueCount = tuple.getIntColumn(AlarmStreamer.CNAME_VALUE_COUNT);
            alarmb.setCount(valueCount);
        }
        if (tuple.hasColumn(AlarmStreamer.CNAME_VIOLATION_COUNT)) {
            int violationCount = tuple.getIntColumn(AlarmStreamer.CNAME_VIOLATION_COUNT);
            alarmb.setViolations(violationCount);
        }

        if (tuple.hasColumn(StandardTupleDefinitions.PARAMETER_COLUMN)) {
            String paraFqn = (String) tuple.getColumn(StandardTupleDefinitions.PARAMETER_COLUMN);

            alarmb.setType(AlarmType.PARAMETER);
            ParameterValue pval = (ParameterValue) tuple.getColumn(ParameterAlarmStreamer.CNAME_TRIGGER);
            alarmb.setId(NamedObjectId.newBuilder().setName(paraFqn).build());
            alarmb.setTriggerTime(TimeEncoding.toProtobufTimestamp(pval.getGenerationTime()));

            if (tuple.hasColumn(ParameterAlarmStreamer.CNAME_SEVERITY_INCREASED)) {
                pval = (ParameterValue) tuple.getColumn(ParameterAlarmStreamer.CNAME_SEVERITY_INCREASED);
            }
            alarmb.setSeverity(AlarmsApi.getParameterAlarmSeverity(pval.getMonitoringResult()));

            ParameterAlarmData parameterAlarmData = tupleToParameterAlarmData(tuple);
            alarmb.setParameterDetail(parameterAlarmData);
        } else {
            alarmb.setType(AlarmType.EVENT);
            Db.Event ev = (Db.Event) tuple.getColumn(EventAlarmStreamer.CNAME_TRIGGER);
            alarmb.setTriggerTime(TimeEncoding.toProtobufTimestamp(ev.getGenerationTime()));
            alarmb.setId(AlarmsApi.getAlarmId(ev));

            if (tuple.hasColumn(EventAlarmStreamer.CNAME_SEVERITY_INCREASED)) {
                ev = (Db.Event) tuple.getColumn(EventAlarmStreamer.CNAME_SEVERITY_INCREASED);
            }
            alarmb.setSeverity(AlarmsApi.getEventAlarmSeverity(ev.getSeverity()));

            EventAlarmData eventAlarmData = tupleToEventAlarmData(tuple);
            alarmb.setEventDetail(eventAlarmData);
        }

        return alarmb.build();
    }

    private static void setAckInfo(AlarmData.Builder alarmb, Tuple tuple) {
        if (tuple.hasColumn("acknowledgedBy")) {
            AcknowledgeInfo.Builder ackb = AcknowledgeInfo.newBuilder();
            ackb.setAcknowledgedBy((String) tuple.getColumn("acknowledgedBy"));
            if (tuple.hasColumn("acknowledgeMessage")) {
                ackb.setAcknowledgeMessage((String) tuple.getColumn("acknowledgeMessage"));
            }
            long acknowledgeTime = (Long) tuple.getColumn("acknowledgeTime");
            ackb.setAcknowledgeTime(TimeEncoding.toProtobufTimestamp(acknowledgeTime));
            alarmb.setAcknowledgeInfo(ackb);
        }
    }

    private static void setClearInfo(AlarmData.Builder alarmb, Tuple tuple) {
        if (!tuple.hasColumn(CNAME_CLEARED_TIME)) {
            return;
        }
        ClearInfo.Builder clib = ClearInfo.newBuilder();
        clib.setClearTime(TimeEncoding.toProtobufTimestamp((Long) tuple.getColumn(CNAME_CLEARED_TIME)));

        if (tuple.hasColumn(CNAME_CLEARED_BY)) {
            clib.setClearedBy((String) tuple.getColumn(CNAME_CLEARED_BY));
        }

        if (tuple.hasColumn(CNAME_CLEAR_MSG)) {
            clib.setClearMessage((String) tuple.getColumn(CNAME_CLEAR_MSG));
        }
        alarmb.setClearInfo(clib.build());
    }

    private static void setShelveInfo(AlarmData.Builder alarmb, Tuple tuple) {
        if (!tuple.hasColumn(CNAME_SHELVED_TIME)) {
            return;
        }
        ShelveInfo.Builder clib = ShelveInfo.newBuilder();
        clib.setShelveTime(TimeEncoding.toProtobufTimestamp((Long) tuple.getColumn(CNAME_SHELVED_TIME)));

        if (tuple.hasColumn(CNAME_SHELVED_BY)) {
            clib.setShelvedBy((String) tuple.getColumn(CNAME_SHELVED_BY));
        }

        if (tuple.hasColumn(CNAME_SHELVED_MSG)) {
            clib.setShelveMessage((String) tuple.getColumn(CNAME_SHELVED_MSG));
        }

        alarmb.setShelveInfo(clib.build());
    }

    /**
     * Stateless continuation token for paged requests on the alarms table
     */
    private static class AlarmsPageToken {

        long triggerTime;
        int seqNum;

        AlarmsPageToken(long triggerTime, int seqNum) {
            this.triggerTime = triggerTime;
            this.seqNum = seqNum;
        }

        static AlarmsPageToken decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded));
            return new Gson().fromJson(decoded, AlarmsPageToken.class);
        }

        String encodeAsString() {
            String json = new Gson().toJson(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        }
    }
}
