package org.yamcs.http.api.processor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConnectedClient;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.ServiceWithConfig;
import org.yamcs.YamcsException;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmSequenceException;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.CouldNotAcknowledgeAlarmException;
import org.yamcs.alarms.EventAlarmServer;
import org.yamcs.alarms.EventId;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.ForbiddenException;
import org.yamcs.http.HttpException;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.ServiceHelper;
import org.yamcs.http.api.YamcsToGpbAssembler;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementService;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Alarms.AlarmNotificationType;
import org.yamcs.protobuf.Alarms.EditAlarmRequest;
import org.yamcs.protobuf.Archive.ListAlarmsResponse;
import org.yamcs.protobuf.ClientInfo.ClientState;
import org.yamcs.protobuf.ProcessorInfo;
import org.yamcs.protobuf.ProcessorManagementRequest;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Rest.EditProcessorRequest;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListProcessorsResponse;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.ProcessorTypeInfo;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

public class ProcessorRestHandler extends RestHandler {

    private static final Logger log = LoggerFactory.getLogger(ProcessorRestHandler.class);

    @Route(path = "/api/processor-types", method = "GET")
    public void listProcessorTypes(RestRequest req) throws HttpException {
        ProcessorTypeInfo.Builder response = ProcessorTypeInfo.newBuilder();
        List<String> processorTypes = ProcessorFactory.getProcessorTypes();
        Collections.sort(processorTypes);
        response.addAllType(processorTypes);
        completeOK(req, response.build());
    }

    @Route(path = "/api/processors", method = "GET")
    public void listProcessors(RestRequest req) throws HttpException {
        ListProcessorsResponse.Builder response = ListProcessorsResponse.newBuilder();
        for (Processor processor : Processor.getProcessors()) {
            response.addProcessor(toProcessorInfo(processor, req, true));
        }
        completeOK(req, response.build());
    }

    @Route(path = "/api/processors/:instance", method = "GET")
    public void listProcessorsForInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        ListProcessorsResponse.Builder response = ListProcessorsResponse.newBuilder();
        for (Processor processor : Processor.getProcessors(instance)) {
            response.addProcessor(toProcessorInfo(processor, req, true));
        }
        completeOK(req, response.build());
    }

    @Route(path = "/api/processors/:instance/:processor", method = "GET")
    public void getProcessor(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));

        ProcessorInfo pinfo = toProcessorInfo(processor, req, true);
        completeOK(req, pinfo);
    }

    @Route(path = "/api/processors/:instance/:processor", method = "DELETE")
    public void deleteProcessor(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlProcessor);

        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        if (!processor.isReplay()) {
            throw new BadRequestException("Cannot delete a non-replay processor");
        }

        processor.quit();
        completeOK(req);
    }

    @Route(path = "/api/processors/:instance/:processor", method = "PATCH")
    public void editProcessor(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.ControlProcessor);

        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        if (!processor.isReplay()) {
            throw new BadRequestException("Cannot update a non-replay processor");
        }

        EditProcessorRequest request = req.bodyAsMessage(EditProcessorRequest.newBuilder()).build();

        String newState = null;
        if (request.hasState()) {
            newState = request.getState();
        }
        if (req.hasQueryParameter("state")) {
            newState = req.getQueryParameter("state");
        }
        if (newState != null) {
            switch (newState.toLowerCase()) {
            case "running":
                processor.resume();
                break;
            case "paused":
                processor.pause();
                break;
            default:
                throw new BadRequestException("Invalid processor state '" + newState + "'");
            }
        }

        long seek = TimeEncoding.INVALID_INSTANT;
        if (request.hasSeek()) {
            seek = RestRequest.parseTime(request.getSeek());
        }
        if (req.hasQueryParameter("seek")) {
            seek = req.getQueryParameterAsDate("seek");
        }
        if (seek != TimeEncoding.INVALID_INSTANT) {
            processor.seek(seek);
        }

        String speed = null;
        if (request.hasSpeed()) {
            speed = request.getSpeed().toLowerCase();
        }
        if (req.hasQueryParameter("speed")) {
            speed = req.getQueryParameter("speed").toLowerCase();
        }
        if (speed != null) {
            ReplaySpeed replaySpeed;
            if ("afap".equals(speed)) {
                replaySpeed = ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).build();
            } else if (speed.endsWith("x")) {
                try {
                    float factor = Float.parseFloat(speed.substring(0, speed.length() - 1));
                    replaySpeed = ReplaySpeed.newBuilder()
                            .setType(ReplaySpeedType.REALTIME)
                            .setParam(factor).build();
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Speed factor is not a valid number");
                }

            } else {
                try {
                    int fixedDelay = Integer.parseInt(speed);
                    replaySpeed = ReplaySpeed.newBuilder()
                            .setType(ReplaySpeedType.FIXED_DELAY)
                            .setParam(fixedDelay).build();
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Fixed delay value is not an integer");
                }
            }
            processor.changeSpeed(replaySpeed);
        }
        completeOK(req);
    }

    @Route(path = "/api/processors/:instance/:processor/clients", method = "GET")
    public void listClientsForProcessor(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));

        Set<ConnectedClient> clients = ManagementService.getInstance().getClients();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ConnectedClient client : clients) {
            if (client.getProcessor() == processor) {
                responseb.addClient(YamcsToGpbAssembler.toClientInfo(client, ClientState.CONNECTED));
            }
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/processors/:instance/:processor/alarms", method = "GET")
    public void listAlarmsForProcessor(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        if (processor.hasAlarmServer()) {
            AlarmServer<Parameter, ParameterValue> alarmServer = processor.getParameterRequestManager()
                    .getAlarmServer();
            for (ActiveAlarm<ParameterValue> alarm : alarmServer.getActiveAlarms().values()) {
                responseb.addAlarm(ProcessorHelper.toAlarmData(AlarmNotificationType.ACTIVE, alarm, true));
            }
        }
        EventAlarmServer eventAlarmServer = processor.getEventAlarmServer();
        if (eventAlarmServer != null) {
            for (ActiveAlarm<Event> alarm : eventAlarmServer.getActiveAlarms().values()) {
                responseb.addAlarm(ProcessorHelper.toAlarmData(AlarmNotificationType.ACTIVE, alarm, true));
            }
        }
        completeOK(req, responseb.build());
    }

    @Deprecated
    @Route(path = "/api/processors/:instance/:processor/parameters/:name*/alarms/:seqnum", method = "PATCH")
    public void patchParameterAlarm(RestRequest req) throws HttpException {
        log.warn("Deprecated endpoint. Use /api/processors/:instance/:processor/alarms/:name*/:seqnum instead");
        patchAlarm(req);
    }

    @Deprecated
    @Route(path = "/api/processors/:instance/:processor/events/:name*/alarms/:seqnum", method = "PATCH")
    public void patchEventAlarm(RestRequest req) throws HttpException {
        log.warn("Deprecated endpoint. Use /api/processors/:instance/:processor/alarms/:name*/:seqnum instead");
        patchAlarm(req);
    }

    /**
     * Finds the appropriate alarm server for the alarm.
     * 
     * FIXME why not one namespace and a single server?
     */
    public static ActiveAlarm<?> verifyAlarm(RestRequest req, Processor processor, String alarmName, int id)
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
            throw new NotFoundException(req, "Subject is in state of alarm, but alarm id does not match");
        }

        throw new NotFoundException(req, "No active alarm named '" + alarmName + "'");
    }

    @Route(path = "/api/processors/:instance/:processor/alarms/:name*/:seqnum", method = "PATCH")
    public void patchAlarm(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        String alarmName = req.getRouteParam("name");
        if (!alarmName.startsWith("/")) {
            alarmName = "/" + alarmName;
        }
        int seqNum = req.getIntegerRouteParam("seqnum");

        ActiveAlarm<?> activeAlarm = verifyAlarm(req, processor, alarmName, seqNum);

        String state = null;
        String comment = null;
        EditAlarmRequest request = req.bodyAsMessage(EditAlarmRequest.newBuilder()).build();
        if (request.hasState()) {
            state = request.getState();
        }
        if (request.hasComment()) {
            comment = request.getComment();
        }

        // URI can override body (legacy)
        if (req.hasQueryParameter("state")) {
            state = req.getQueryParameter("state");
        }
        if (req.hasQueryParameter("comment")) {
            comment = req.getQueryParameter("comment");
        }
        if (state == null) {
            throw new BadRequestException("No state specified");
        }

        // TODO permissions on AlarmServer
        String username = req.getUser().getName();

        switch (state.toLowerCase()) {
        case "acknowledged":
            try {
                if (activeAlarm.triggerValue instanceof ParameterValue) {
                    AlarmServer<Parameter, ParameterValue> alarmServer = verifyParameterAlarmServer(processor);
                    @SuppressWarnings("unchecked")
                    ActiveAlarm<ParameterValue> alarm = (ActiveAlarm<ParameterValue>) activeAlarm;
                    alarmServer.acknowledge(alarm, username, processor.getCurrentTime(), comment);
                    completeOK(req);
                } else if (activeAlarm.triggerValue instanceof Event) {
                    EventAlarmServer alarmServer = verifyEventAlarmServer(processor);
                    @SuppressWarnings("unchecked")
                    ActiveAlarm<Event> alarm = (ActiveAlarm<Event>) activeAlarm;
                    alarmServer.acknowledge(alarm, username, processor.getCurrentTime(), comment);
                    completeOK(req);
                } else {
                    throw new InternalServerErrorException("Can't find alarm server for alarm instance");
                }
            } catch (CouldNotAcknowledgeAlarmException e) {
                log.debug("Did not acknowledge alarm {}. {}", seqNum, e.getMessage());
                throw new BadRequestException(e.getMessage());
            }
            break;
        default:
            throw new BadRequestException("Unsupported state '" + state + "'");
        }
    }

    @Route(path = "/api/processors/:instance", method = "POST")
    public void createProcessorForInstance(RestRequest restReq) throws HttpException {
        CreateProcessorRequest request = restReq.bodyAsMessage(CreateProcessorRequest.newBuilder()).build();
        String yamcsInstance = verifyInstance(restReq, restReq.getRouteParam("instance"));

        if (!request.hasName()) {
            throw new BadRequestException("No processor name was specified");
        }
        String processorName = request.getName();

        if (!request.hasType()) {
            throw new BadRequestException("No processor type was specified");
        }
        String processorType = request.getType();

        ProcessorManagementRequest.Builder reqb = ProcessorManagementRequest.newBuilder();
        reqb.setInstance(yamcsInstance);
        reqb.setName(processorName);
        reqb.setType(processorType);
        if (request.hasPersistent()) {
            reqb.setPersistent(request.getPersistent());
        }
        Set<Integer> clientIds = new HashSet<>(request.getClientIdList());
        // this will remove any invalid clientIds from the set
        verifyPermissions(reqb.getPersistent(), processorType, clientIds, restReq.getUser());

        if (request.hasConfig()) {
            reqb.setConfig(request.getConfig());
        }

        reqb.addAllClientId(clientIds);
        ManagementService mservice = ManagementService.getInstance();
        try {
            mservice.createProcessor(reqb.build(), restReq.getUser().getName());
            completeOK(restReq);
        } catch (YamcsException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private void verifyPermissions(boolean persistent, String processorType, Set<Integer> clientIds, User user)
            throws ForbiddenException {
        String username = user.getName();
        if (!user.hasSystemPrivilege(SystemPrivilege.ControlProcessor)) {
            if (persistent) {
                log.warn("User {} is not allowed to create persistent processors", username);
                throw new ForbiddenException("No permission to create persistent processors");
            }
            if (!"Archive".equals(processorType)) {
                log.warn("User {} is not allowed to create processors of type {}", processorType, username);
                throw new ForbiddenException("No permission to create processors of type " + processorType);
            }
            verifyClientsBelongToUser(username, clientIds);
        }
    }

    /**
     * verifies that clients with ids are all belonging to this username. If not, throw a ForbiddenException If there is
     * any invalid id (maybe client disconnected), remove it from the set
     */
    public static void verifyClientsBelongToUser(String username, Set<Integer> clientIds) throws ForbiddenException {
        ManagementService mgrsrv = ManagementService.getInstance();
        for (Iterator<Integer> it = clientIds.iterator(); it.hasNext();) {
            int id = it.next();
            ConnectedClient client = mgrsrv.getClient(id);
            if (client == null) {
                log.warn("Invalid client id {} specified, ignoring", id);
                it.remove();
            } else {
                if (!username.equals(client.getUser().getName())) {
                    log.warn("User {} is not allowed to connect {} to new processor", username, client.getUser());
                    throw new ForbiddenException("Not allowed to connect clients other than your own");
                }
            }
        }
    }

    public static ProcessorInfo toProcessorInfo(Processor processor, RestRequest req, boolean detail) {
        ProcessorInfo.Builder b;
        if (detail) {
            ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
            b = ProcessorInfo.newBuilder(pinfo);
        } else {
            b = ProcessorInfo.newBuilder().setName(processor.getName());
        }

        String instance = processor.getInstance();
        String name = processor.getName();

        for (ServiceWithConfig serviceWithConfig : processor.getServices()) {
            b.addService(ServiceHelper.toServiceInfo(serviceWithConfig, instance, name));
        }
        return b.build();
    }
}
