package org.yamcs.web.rest.processor;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.ProcessorFactory;
import org.yamcs.ServiceWithConfig;
import org.yamcs.YamcsException;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Rest.EditProcessorRequest;
import org.yamcs.protobuf.Rest.ListAlarmsResponse;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListProcessorsResponse;
import org.yamcs.protobuf.Yamcs.ProcessorTypeInfo;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.ForbiddenException;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.ServiceHelper;

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

    @Route(path = "/api/processors/:instance/:processor", method = { "PATCH", "PUT", "POST" })
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

        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (processor.getInstance().equals(client.getInstance())
                    && processor.getName().equals(client.getProcessorName())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        completeOK(req, responseb.build());
    }

    @Route(path = "/api/processors/:instance/:processor/alarms", method = "GET")
    public void listAlarmsForProcessor(RestRequest req) throws HttpException {
        Processor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        if (processor.hasAlarmServer()) {
            AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
            for (ActiveAlarm alarm : alarmServer.getActiveAlarms().values()) {
                responseb.addAlarm(ProcessorHelper.toAlarmData(AlarmData.Type.ACTIVE, alarm));
            }
        }
        completeOK(req, responseb.build());
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
            mservice.createProcessor(reqb.build(), restReq.getUser().getUsername());
            completeOK(restReq);
        } catch (YamcsException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    private void verifyPermissions(boolean persistent, String processorType, Set<Integer> clientIds, User user)
            throws ForbiddenException {
        String username = user.getUsername();
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
            ClientInfo ci = mgrsrv.getClientInfo(id);
            if (ci == null) {
                log.warn("Invalid client id {} specified, ignoring", id);
                it.remove();
            } else {
                if (!username.equals(ci.getUsername())) {
                    log.warn("User {} is not allowed to connect {} to new processor", username, ci.getUsername());
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
