package org.yamcs.web.rest.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.management.ManagementGpbHelper;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
import org.yamcs.protobuf.Rest.EditProcessorRequest;
import org.yamcs.protobuf.Rest.ListClientsResponse;
import org.yamcs.protobuf.Rest.ListProcessorsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.Yamcs.CommandHistoryReplayRequest;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.PpReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.YamcsManagement.ClientInfo;
import org.yamcs.protobuf.YamcsManagement.ClientInfo.ClientState;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.Option;
import org.yamcs.web.rest.Route;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import io.netty.channel.ChannelFuture;


public class ProcessorRestHandler extends RestHandler {

    @Route(path = "/api/processors/:instance/:processor/clients", method = "GET")
    public ChannelFuture listClientsForProcessor(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (processor.getInstance().equals(client.getInstance())
                    && processor.getName().equals(client.getProcessorName())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        return sendOK(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }

    @Route(path = "/api/processors", method = "GET")
    public ChannelFuture listProcessors(RestRequest req) throws HttpException {
        ListProcessorsResponse.Builder response = ListProcessorsResponse.newBuilder();
        for (YProcessor processor : YProcessor.getProcessors()) {
            response.addProcessor(toProcessorInfo(processor, req, true));
        }
        return sendOK(req, response.build(), SchemaRest.ListProcessorsResponse.WRITE);
    }

    @Route(path = "/api/processors/:instance", method = "GET")
    public ChannelFuture listProcessorsForInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        ListProcessorsResponse.Builder response = ListProcessorsResponse.newBuilder();
        for (YProcessor processor : YProcessor.getProcessors(instance)) {
            response.addProcessor(toProcessorInfo(processor, req, true));
        }
        return sendOK(req, response.build(), SchemaRest.ListProcessorsResponse.WRITE);
    }
    
    @Route(path = "/api/processors/:instance/:processor", method = "GET")
    public ChannelFuture getProcessor(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        
        ProcessorInfo pinfo = toProcessorInfo(processor, req, true);
        return sendOK(req, pinfo, SchemaYamcsManagement.ProcessorInfo.WRITE);
    }

    @Route(path = "/api/processors/:instance/:processor", method = { "PATCH", "PUT", "POST" })
    public ChannelFuture editProcessor(RestRequest req) throws HttpException {
        YProcessor processor = verifyProcessor(req, req.getRouteParam("instance"), req.getRouteParam("processor"));
        if (!processor.isReplay()) {
            throw new BadRequestException("Cannot update a non-replay processor");
        }
        
        EditProcessorRequest request = req.bodyAsMessage(SchemaRest.EditProcessorRequest.MERGE).build();

        String newState = null;
        if (request.hasState()) newState = request.getState();
        if (req.hasQueryParameter("state")) newState = req.getQueryParameter("state");
        if (newState != null) {
            switch (newState.toLowerCase()) {
            case "running":
                processor.resume();
                break;
            case "paused":
                processor.pause();
                break;
            }
        }

        long seek = TimeEncoding.INVALID_INSTANT;
        if (request.hasSeek()) seek = RestRequest.parseTime(request.getSeek());
        if (req.hasQueryParameter("seek")) seek = req.getQueryParameterAsDate("seek");
        if (seek != TimeEncoding.INVALID_INSTANT) {
            processor.seek(seek);
        }

        String speed = null;
        if (request.hasSpeed()) speed = request.getSpeed().toLowerCase();
        if (req.hasQueryParameter("speed")) speed = req.getQueryParameter("speed").toLowerCase();
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

        return sendOK(req);
    }

    @Route(path = "/api/processors/:instance", method = "POST")
    public ChannelFuture createProcessorForInstance(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        
        CreateProcessorRequest request = req.bodyAsMessage(SchemaRest.CreateProcessorRequest.MERGE).build();

        String name = null;
        long start = TimeEncoding.INVALID_INSTANT;
        long stop = TimeEncoding.INVALID_INSTANT;
        String type = "Archive"; // API currently only supports standard replays
        String speed = "1x";
        boolean loop = false;
        boolean persistent = false;
        Set<Integer> clientIds = new HashSet<>();
        List<String> paraPatterns = new ArrayList<>();
        List<String> ppGroups = new ArrayList<>();
        List<String> packetNames = new ArrayList<>();
        boolean cmdhist = false;

        if (request.hasName()) name = request.getName();
        if (request.hasStart()) start = RestRequest.parseTime(request.getStart());
        if (request.hasStop()) stop = RestRequest.parseTime(request.getStop());
        if (request.hasSpeed()) speed = request.getSpeed().toLowerCase();
        if (request.hasLoop()) loop = request.getLoop();
        if (request.hasCmdhist()) cmdhist = request.getCmdhist();
        if (request.hasPersistent()) persistent = request.getPersistent();
        clientIds.addAll(request.getClientIdList());
        paraPatterns.addAll(request.getParanameList());
        ppGroups.addAll(request.getPpgroupList());
        packetNames.addAll(request.getPacketnameList());

        // Query params get priority
        if (req.hasQueryParameter("name")) name = req.getQueryParameter("name");
        if (req.hasQueryParameter("start")) start = req.getQueryParameterAsDate("start");
        if (req.hasQueryParameter("stop")) stop = req.getQueryParameterAsDate("stop");
        if (req.hasQueryParameter("speed")) speed = req.getQueryParameter("speed").toLowerCase();
        if (req.hasQueryParameter("loop")) loop = req.getQueryParameterAsBoolean("loop");
        if (req.hasQueryParameter("paraname")) paraPatterns.addAll(req.getQueryParameterList("paraname"));
        if (req.hasQueryParameter("ppgroup")) ppGroups.addAll(req.getQueryParameterList("ppgroup"));
        if (req.hasQueryParameter("packetname")) packetNames.addAll(req.getQueryParameterList("packetname"));
        if (req.hasQueryParameter("cmdhist")) cmdhist = req.getQueryParameterAsBoolean("cmdhist");
        if (req.hasQueryParameter("persistent")) persistent = req.getQueryParameterAsBoolean("persistent");
        if (req.hasQueryParameter("clientId")) clientIds.addAll(request.getClientIdList());

        // Only these must be user-provided
        if (name == null) throw new BadRequestException("No processor name was specified");
        if (start == TimeEncoding.INVALID_INSTANT) throw new BadRequestException("No start time was specified");

        // Make internal processor request
        ProcessorManagementRequest.Builder reqb = ProcessorManagementRequest.newBuilder();
        reqb.setInstance(instance);
        reqb.setName(name);
        reqb.setType(type);
        reqb.setPersistent(persistent);
        reqb.addAllClientId(clientIds);

        ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
        rrb.setEndAction(loop ? EndAction.LOOP : EndAction.STOP);
        rrb.setStart(start);
        if (stop != TimeEncoding.INVALID_INSTANT) {
            rrb.setStop(stop);
        }

        // Replay Speed
        if ("afap".equals(speed)) {
            rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        } else if (speed.endsWith("x")) {
            try {
                float factor = Float.parseFloat(speed.substring(0, speed.length() - 1));
                rrb.setSpeed(ReplaySpeed.newBuilder()
                        .setType(ReplaySpeedType.REALTIME)
                        .setParam(factor));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Speed factor is not a valid number");
            }
        } else {
            try {
                int fixedDelay = Integer.parseInt(speed);
                rrb.setSpeed(ReplaySpeed.newBuilder()
                        .setType(ReplaySpeedType.FIXED_DELAY)
                        .setParam(fixedDelay));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Fixed delay is not an integer");
            }
        }

        // Resolve parameter inclusion patterns
        // IMO this should actually all be done by the replay server itself. Not just in REST.
        Set<NamedObjectId> includedParameters = new LinkedHashSet<>(); // Preserve order (in case it matters)
        for (String pattern : paraPatterns) {
            if (!pattern.startsWith("/")) pattern = "/" + pattern; // only xtce
            boolean resolved = false;

            // Is it a namespace? Include parameters directly at that level.
            for (String namespace : mdb.getNamespaces()) {
                if (pattern.equals(namespace)) {
                    mdb.getSpaceSystem(namespace).getParameters().forEach(p -> {
                        includedParameters.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
                    });

                    resolved = true;
                    break;
                }
            }
            if (resolved) continue;

            // Is it a parameter name? Do index lookup
            Parameter p = mdb.getParameter(pattern);
            if (p != null) {
                includedParameters.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
                continue;
            }

            // Now, the slow approach. Match on qualified names, with support for star-wildcards
            Pattern regex = Pattern.compile(pattern.replace("*", ".*"));
            for (Parameter para : mdb.getParameters()) {
                if (regex.matcher(para.getQualifiedName()).matches()) {
                    includedParameters.add(NamedObjectId.newBuilder().setName(para.getQualifiedName()).build());
                }
            }

            // Currently does not error when an invalid pattern is specified. Seems like the right thing now.
        }
        if (!includedParameters.isEmpty()) {
            rrb.setParameterRequest(ParameterReplayRequest.newBuilder().addAllNameFilter(includedParameters));
        }

        // PP groups are just passed. Not sure if we should keep support for this. Parameters are not filterable
        // on containers either, so I don't see why these get special treatment. Would prefer they are handled
        // in the above paraPatterns loop instead.
        if (!ppGroups.isEmpty()) {
            rrb.setPpRequest(PpReplayRequest.newBuilder().addAllGroupNameFilter(ppGroups));
        }

        // Packet names are also just passed. We may want to try something fancier here with wildcard support.
        if (!packetNames.isEmpty()) {
            if (packetNames.size() == 1 && packetNames.get(0).equals("*")) {
                rrb.setPacketRequest(PacketReplayRequest.newBuilder());
            } else {
                List<NamedObjectId> packetIds = new ArrayList<>();
                packetNames.forEach(packetName -> {
                    packetIds.add(NamedObjectId.newBuilder().setName(packetName).build());
                });
                rrb.setPacketRequest(PacketReplayRequest.newBuilder().addAllNameFilter(packetIds));
            }
        }

        if (cmdhist) {
            rrb.setCommandHistoryRequest(CommandHistoryReplayRequest.newBuilder());
        }

        reqb.setReplaySpec(rrb);
        ManagementService mservice = ManagementService.getInstance();
        try {
            mservice.createProcessor(reqb.build(), req.getAuthToken());
            return sendOK(req);
        } catch (YamcsException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    public static ProcessorInfo toProcessorInfo(YProcessor processor, RestRequest req, boolean detail) {
        ProcessorInfo.Builder b;
        if (detail) {
            ProcessorInfo pinfo = ManagementGpbHelper.toProcessorInfo(processor);
            b = ProcessorInfo.newBuilder(pinfo);
        } else {
            b = ProcessorInfo.newBuilder().setName(processor.getName());
        }

        String instance = processor.getInstance();
        String name = processor.getName();
        if (!req.getOptions().contains(Option.NO_LINK)) {
            String apiURL = req.getApiURL();
            b.setUrl(apiURL + "/processors/" + instance + "/" + name);
            b.setClientsUrl(apiURL + "/processors/" + instance + "/" + name + "/clients");
            b.setParametersUrl(apiURL + "/processors/" + instance + "/" + name + "/parameters{/namespace}{/name}");
            b.setCommandsUrl(apiURL + "/processors/" + instance + "/" + name + "/commands{/namespace}{/name}");
            b.setCommandQueuesUrl(apiURL + "/processors/" + instance + "/" + name + "/cqueues{/name}");
        }
        return b.build();
    }
}
