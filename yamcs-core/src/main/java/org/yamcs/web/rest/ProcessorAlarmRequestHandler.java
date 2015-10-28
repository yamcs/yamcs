package org.yamcs.web.rest;

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.CouldNotAcknowledgeAlarmException;
import org.yamcs.protobuf.Alarms.AlarmInfo;
import org.yamcs.protobuf.Rest.ListAlarmsResponse;
import org.yamcs.protobuf.Rest.PatchAlarmRequest;
import org.yamcs.protobuf.SchemaAlarms;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;

/**
 * Handles incoming requests related to realtime Alarms
 */
public class ProcessorAlarmRequestHandler extends RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(ProcessorAlarmRequestHandler.class.getName());
    
    @Override
    public String getPath() {
        return "alarms";
    }
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        YProcessor processor = req.getFromContext(RestRequest.CTX_PROCESSOR);
        
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listAlarms(req, processor);
        } else {
            req.assertPOST();
            
            int alarmId;
            try {
                alarmId = Integer.parseInt(req.getPathSegment(pathOffset + 1));
            } catch (NumberFormatException e) {
                throw new NotFoundException(req);
            }
            
            // The rest of the path should be the qualified parameter name
            String[] pathSegments = req.getPathSegments();
            StringBuilder fqname = new StringBuilder();
            for (int i = pathOffset + 2; i < pathSegments.length; i++) {
                fqname.append("/").append(pathSegments[i]);
            }
            NamedObjectId id = NamedObjectId.newBuilder().setName(fqname.toString()).build();
            
            Parameter p = processor.getXtceDb().getParameter(id);
            if (p == null) {
                throw new NotFoundException(req);
            }
            
            return patchAlarm(req, alarmId, p, processor);
        }
    }

    /**
     * Lists all active alarms
     */
    private RestResponse listAlarms(RestRequest req, YProcessor processor) throws RestException {
        if (!processor.hasAlarmServer()) {
            throw new BadRequestException("Alarms are not enabled for this instance");
        }
        
        AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        for (Entry<Parameter, ActiveAlarm> entry : alarmServer.getActiveAlarms().entrySet()) {
            AlarmInfo info = toAlarmInfo(entry.getKey(), entry.getValue());
            responseb.addAlarm(info);
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ListAlarmsResponse.WRITE);
    }
    
    /**
     * Updates an alarm
     */
    private RestResponse patchAlarm(RestRequest req, int alarmId, Parameter p, YProcessor processor) throws RestException {
        if (!processor.hasAlarmServer()) {
            throw new BadRequestException("Alarms are not enabled for this instance");
        }
        
        String state = null;
        String comment = null;
        PatchAlarmRequest request = req.bodyAsMessage(SchemaRest.PatchAlarmRequest.MERGE).build();
        if (request.hasState()) state = request.getState();
        if (request.hasComment()) comment = request.getComment();
        
        // URI can override body
        if (req.hasQueryParameter("state")) state = req.getQueryParameter("state");
        if (req.hasQueryParameter("comment")) state = req.getQueryParameter("comment");
        
        switch (state.toLowerCase()) {
        case "acknowledge":
            AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
            try {
                // TODO permissions on AlarmServer
                ActiveAlarm aa = alarmServer.acknowledge(p, alarmId, req.getUsername(), processor.getCurrentTime(), comment);
                AlarmInfo updatedInfo = toAlarmInfo(p, aa);
                return new RestResponse(req, updatedInfo, SchemaAlarms.AlarmInfo.WRITE);   
            } catch (CouldNotAcknowledgeAlarmException e) {
                log.debug("Did not acknowledge alarm " + alarmId + ". " + e.getMessage());
                throw new BadRequestException(e.getMessage());
            }
        default:
            throw new BadRequestException("Unsupported state '" + state + "'");
        }
    }
    
    private static AlarmInfo toAlarmInfo(Parameter p, ActiveAlarm activeAlarm) {
        NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
        AlarmInfo.Builder alarmb = AlarmInfo.newBuilder();
        alarmb.setId(activeAlarm.id);
        alarmb.setTriggerValue(activeAlarm.triggerValue.toGpb(id));
        alarmb.setMostSevereValue(activeAlarm.mostSevereValue.toGpb(id));
        alarmb.setCurrentValue(activeAlarm.currentValue.toGpb(id));
        alarmb.setViolations(activeAlarm.violations);
        return alarmb.build();
    }
}
