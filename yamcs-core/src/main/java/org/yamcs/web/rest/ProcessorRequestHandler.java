package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest;
import org.yamcs.protobuf.Rest.CreateProcessorRequest;
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
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.RestRequest.Option;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles requests related to processors
 */
public class ProcessorRequestHandler extends RestRequestHandler {

    private static ProcessorParameterRequestHandler parameterHandler = new ProcessorParameterRequestHandler();
    private static ProcessorCommandRequestHandler commandHandler = new ProcessorCommandRequestHandler();
    private static ProcessorCommandQueueRequestHandler cqueueHandler = new ProcessorCommandQueueRequestHandler();

    @Override
    public String getPath() {
        return "processors";
    }

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listProcessors(req);
        } else {
            String instance = req.getPathSegment(pathOffset);
            if (!YamcsServer.hasInstance(instance)) {
                throw new NotFoundException(req, "No instance '" + instance + "'");
            }
            req.addToContext(RestRequest.CTX_INSTANCE, instance);
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            req.addToContext(MDBRequestHandler.CTX_MDB, mdb);

            if (!req.hasPathSegment(pathOffset + 1)) {
                if (req.isGET()) {
                    return listProcessorsForInstance(req, instance);
                } else if (req.isPOST()) {
                    return createProcessorForInstance(req, instance, mdb);
                } else {
                    throw new MethodNotAllowedException(req);
                }
            } else {
                String processorName = req.getPathSegment(pathOffset + 1);
                YProcessor processor = YProcessor.getInstance(instance, processorName);
                if (processor == null) {
                    throw new NotFoundException(req, "No processor '" + processorName + "'");
                }
                req.addToContext(RestRequest.CTX_PROCESSOR, processor);
                return handleProcessorRequest(req, pathOffset + 2, processor);
            }
        }
    }

    private RestResponse handleProcessorRequest(RestRequest req, int pathOffset, YProcessor processor) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            return updateProcessor(req, processor);
        } else {
            switch (req.getPathSegment(pathOffset)) {
            case "parameters":
                return parameterHandler.handleRequest(req, pathOffset + 1);
            case "commands":
                return commandHandler.handleRequest(req, pathOffset + 1);
            case "cqueues":
                return cqueueHandler.handleRequest(req, pathOffset + 1);
            case "clients":
                req.assertGET();
                return listClientsForProcessor(req, processor);
            default:
                throw new NotFoundException(req);
            }
        }
    }

    private RestResponse listClientsForProcessor(RestRequest req, YProcessor processor) throws RestException {
        Set<ClientInfo> clients = ManagementService.getInstance().getClientInfo();
        ListClientsResponse.Builder responseb = ListClientsResponse.newBuilder();
        for (ClientInfo client : clients) {
            if (processor.getInstance().equals(client.getInstance())
                    && processor.getName().equals(client.getProcessorName())) {
                responseb.addClient(ClientInfo.newBuilder(client).setState(ClientState.CONNECTED));
            }
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListClientsResponse.WRITE);
    }

    private RestResponse listProcessors(RestRequest req) throws RestException {
        ListProcessorsResponse.Builder response = ListProcessorsResponse.newBuilder();
        for (YProcessor processor : YProcessor.getChannels()) {
            response.addProcessor(toProcessorInfo(processor, req, true));
        }
        return new RestResponse(req, response.build(), SchemaRest.ListProcessorsResponse.WRITE);
    }

    private RestResponse listProcessorsForInstance(RestRequest req, String yamcsInstance) throws RestException {
        ListProcessorsResponse.Builder response = ListProcessorsResponse.newBuilder();
        for (YProcessor processor : YProcessor.getChannels(yamcsInstance)) {
            response.addProcessor(toProcessorInfo(processor, req, true));
        }
        return new RestResponse(req, response.build(), SchemaRest.ListProcessorsResponse.WRITE);
    }

    private RestResponse updateProcessor(RestRequest req, YProcessor yproc) throws RestException {
        if(req.isPOST())
            return postProcessor(req, yproc);
        else if(req.isPATCH())
            return patchProcessor(req, yproc);
        else {
            throw new MethodNotAllowedException(req);
        }
    }

    private RestResponse patchProcessor(RestRequest req, YProcessor yproc) throws RestException {
        req.assertPATCH();
        Rest.PatchProcessorRequest yprocPatch = req.bodyAsMessage(SchemaRest.PatchProcessorRequest.MERGE).build();

        if(!yproc.isReplay()) {
            throw new BadRequestException("Cannot patch a non replay processor ");
        }

        // patch processor state
        if(yprocPatch.hasState()) {
            if ("RUNNING".equals(yprocPatch.getState())) {
                yproc.resume();
            }
            if ("PAUSED".equals(yprocPatch.getState())) {
                yproc.pause();
            }
        }

        // patch processor seek time
        if(yprocPatch.hasSeekTime())
        {
            yproc.seek(yprocPatch.getSeekTime());
        }

        // patch processor speed
        if(yprocPatch.hasSpeed())
        {

            ReplaySpeed replaySpeed = null;
            if("afap".equals(yprocPatch.getSpeed()))
            {
                replaySpeed = ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP).setParam(1).build();
                yproc.changeSpeed(replaySpeed);
            }
            else if("realtime".equals(yprocPatch.getSpeed()))
            {
                replaySpeed = ReplaySpeed.newBuilder().setType(ReplaySpeedType.REALTIME).setParam(1).build();
                yproc.changeSpeed(replaySpeed);
                yproc.seek(yproc.getCurrentTime());
            }
            else
            {
                float speedValue = 1;
                try{
                    speedValue = Float.parseFloat(yprocPatch.getSpeed().replace("x", ""));
                }
                catch (Exception e)
                {
                    throw  new BadRequestException("Unable to parse replay speed");
                }
                replaySpeed = ReplaySpeed.newBuilder().setType(ReplaySpeedType.FIXED_DELAY).setParam(speedValue).build();
                yproc.changeSpeed(replaySpeed);
            }
        }

        return new RestResponse(req);
    }

    private RestResponse postProcessor(RestRequest req, YProcessor yproc) throws RestException {

        req.assertPOST();
        ProcessorRequest yprocReq = req.bodyAsMessage(SchemaYamcsManagement.ProcessorRequest.MERGE).build();
        switch(yprocReq.getOperation()) {
            case RESUME:
                if(!yproc.isReplay()) {
                    throw new BadRequestException("Cannot resume a non replay processor ");
                }
                yproc.resume();
                break;
            case PAUSE:
                if(!yproc.isReplay()) {
                    throw new BadRequestException("Cannot pause a non replay processor ");
                }
                yproc.pause();
                break;
            case SEEK:
                if(!yproc.isReplay()) {
                    throw new BadRequestException("Cannot seek a non replay processor ");
                }
                if(!yprocReq.hasSeekTime()) {
                    throw new BadRequestException("No seek time specified");
                }
                yproc.seek(yprocReq.getSeekTime());
                break;
            case CHANGE_SPEED:
                if(!yproc.isReplay()) {
                    throw new BadRequestException("Cannot seek a non replay processor ");
                }
                if(!yprocReq.hasReplaySpeed()) {
                    throw new BadRequestException("No replay speed specified");
                }
                yproc.changeSpeed(yprocReq.getReplaySpeed());
                break;
            default:
                throw new BadRequestException("Invalid operation "+yprocReq.getOperation()+" specified");
        }
        return new RestResponse(req);
    }

    private RestResponse createProcessorForInstance(RestRequest req, String yamcsInstance, XtceDb mdb) throws RestException {
        CreateProcessorRequest request = req.bodyAsMessage(SchemaRest.CreateProcessorRequest.MERGE).build();

        String name = null;
        String start = null;
        String stop = null;
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
        if (request.hasStart()) start = request.getStart();
        if (request.hasStop()) stop = request.getStop();
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
        if (req.hasQueryParameter("start")) start = req.getQueryParameter("start");
        if (req.hasQueryParameter("stop")) stop = req.getQueryParameter("stop");
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
        if (start == null) throw new BadRequestException("No start time was specified");

        // Make internal processor request
        ProcessorManagementRequest.Builder reqb = ProcessorManagementRequest.newBuilder();
        reqb.setInstance(yamcsInstance);
        reqb.setName(name);
        reqb.setType(type);
        reqb.setPersistent(persistent);
        reqb.addAllClientId(clientIds);

        ReplayRequest.Builder rrb = ReplayRequest.newBuilder();
        rrb.setEndAction(loop ? EndAction.LOOP : EndAction.STOP);
        rrb.setStart(TimeEncoding.parse(start));
        if (stop != null) {
            rrb.setStop(TimeEncoding.parse(stop));
        }

        // Replay Speed
        if ("afap".equals(speed)) {
            rrb.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        } else if (speed.endsWith("x")) {
            float factor = Float.parseFloat(speed.substring(0, speed.length() - 1));
            rrb.setSpeed(ReplaySpeed.newBuilder()
                    .setType(ReplaySpeedType.REALTIME)
                    .setParam(factor));
        } else {
            int fixedDelay = Integer.parseInt(speed);
            rrb.setSpeed(ReplaySpeed.newBuilder()
                    .setType(ReplaySpeedType.FIXED_DELAY)
                    .setParam(fixedDelay));
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
            mservice.createProcessor(reqb.build(), req.authToken);
            return new RestResponse(req);
        } catch (YamcsException e) {
            throw new BadRequestException(e.getMessage());
        }

    }

    public static ProcessorInfo toProcessorInfo(YProcessor processor, RestRequest req, boolean detail) {
        ProcessorInfo.Builder b;
        if (detail) {
            ProcessorInfo pinfo = ManagementService.getProcessorInfo(processor);
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
