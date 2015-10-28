package org.yamcs.web.rest;

import org.yamcs.YProcessor;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.Rest.ListProcessorsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcsManagement;
import org.yamcs.protobuf.YamcsManagement.ProcessorInfo;
import org.yamcs.protobuf.YamcsManagement.ProcessorManagementRequest;
import org.yamcs.protobuf.YamcsManagement.ProcessorRequest;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

/**
 * Handles requests related to processors
 */
public class ProcessorRequestHandler extends RestRequestHandler {
    
    private static ProcessorParameterRequestHandler parameterHandler = new ProcessorParameterRequestHandler();
    private static ProcessorCommandRequestHandler commandHandler = new ProcessorCommandRequestHandler();
    private static ProcessorAlarmRequestHandler alarmHandler = new ProcessorAlarmRequestHandler();
    private static ProcessorCommandQueueRequestHandler cqueueHandler = new ProcessorCommandQueueRequestHandler();
    
    @Override
    public String getPath() {
        return "processors";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            if (req.isGET()) {
                return listProcessors(req);
            } else if (req.isPOST()) {
                return handleProcessorManagementRequest(req);    
            } else {
                throw new MethodNotAllowedException(req);
            }
        } else {
            // Check instance
            String instance = req.getPathSegment(pathOffset);
            if (!YamcsServer.hasInstance(instance)) {
                throw new NotFoundException(req, "No instance '" + instance + "'");
            }
            req.addToContext(RestRequest.CTX_INSTANCE, instance);
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            req.addToContext(MissionDatabaseRequestHandler.CTX_MDB, mdb);
            
            if (!req.hasPathSegment(pathOffset + 1)) {
                req.assertGET();
                return listProcessorsForInstance(req, instance);
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
            return patchProcessor(req, processor);
        } else {
            switch (req.getPathSegment(pathOffset)) {
            case "parameters":
                return parameterHandler.handleRequest(req, pathOffset + 1);
            case "commands":
                return commandHandler.handleRequest(req, pathOffset + 1);
            case "alarms":
                return alarmHandler.handleRequest(req, pathOffset + 1);
            case "cqueues":
                return cqueueHandler.handleRequest(req, pathOffset + 1);
            default:
                throw new NotFoundException(req);
            }
        }
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
        
    private RestResponse patchProcessor(RestRequest req, YProcessor yproc) throws RestException {
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
    
    private RestResponse handleProcessorManagementRequest(RestRequest req) throws RestException {
        req.assertPOST();
        ProcessorManagementRequest yprocReq = req.bodyAsMessage(SchemaYamcsManagement.ProcessorManagementRequest.MERGE).build();

        if(!yprocReq.hasInstance()) throw new BadRequestException("No instance has been specified");
        if(!yprocReq.hasName()) throw new BadRequestException("No processor name has been specified");
        
        switch(yprocReq.getOperation()) {
        case CONNECT_TO_PROCESSOR:
            ManagementService mservice = ManagementService.getInstance();
            try {
                mservice.connectToProcessor(yprocReq, req.authToken);
                return new RestResponse(req);
            } catch (YamcsException e) {
                throw new BadRequestException(e.getMessage());
            }
        
        case CREATE_PROCESSOR:
            if(!yprocReq.hasType()) throw new BadRequestException("No processor type has been specified");
            mservice = ManagementService.getInstance();
            try {
                mservice.createProcessor(yprocReq, req.authToken);
                return new RestResponse(req);
            } catch (YamcsException e) {
                throw new BadRequestException(e.getMessage());
            }
        
        default:
            throw new BadRequestException("Invalid operation "+yprocReq.getOperation()+" specified");
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
        String apiURL = req.getApiURL();
        b.setUrl(apiURL + "/processors/" + instance + "/" + name);
        b.setParametersUrl(apiURL + "/processors/" + instance + "/" + name + "/parameters{/namespace}{/name}");
        b.setCommandsUrl(apiURL + "/processors/" + instance + "/" + name + "/commands{/namespace}{/name}");
        b.setCommandQueuesUrl(apiURL + "/processors/" + instance + "/" + name + "/cqueues{/name}");
        b.setAlarmsUrl(apiURL + "/processors/" + instance + "/" + name + "/alarms{/id}");
        return b.build();
    }
}
