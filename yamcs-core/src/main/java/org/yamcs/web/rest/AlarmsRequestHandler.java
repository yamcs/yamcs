package org.yamcs.web.rest;

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.CouldNotAcknowledgeAlarmException;
import org.yamcs.protobuf.Alarms.Alarm;
import org.yamcs.protobuf.Rest.AcknowledgeAlarmRequest;
import org.yamcs.protobuf.Rest.AcknowledgeAlarmResponse;
import org.yamcs.protobuf.Rest.GetAlarmsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.xtce.Parameter;

/**
 * Handles incoming requests related to realtime Alarms.
 * <p>
 * /(instance)/api/alarms
 */
public class AlarmsRequestHandler implements RestRequestHandler {
    final static Logger log = LoggerFactory.getLogger(AlarmsRequestHandler.class.getName());
    
    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        YProcessor processor = YProcessor.getInstance(req.yamcsInstance, "realtime");
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return getAlarms(req, processor);
        } else if (req.getPathSegment(pathOffset).equals("acknowledge")) {
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
            
            return acknowledgeAlarm(req, alarmId, p, processor);
        } else {
            throw new NotFoundException(req);
        }
    }

    /**
     * Lists all active alarms
     * <p>
     * GET /(instance)/api/alarms
     */
    private RestResponse getAlarms(RestRequest req, YProcessor processor) throws RestException {
        if (!processor.hasAlarmServer()) {
            throw new BadRequestException("Alarms are not enabled for this instance");
        }
        
        AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
        GetAlarmsResponse.Builder responseb = GetAlarmsResponse.newBuilder();
        for (Entry<Parameter, ActiveAlarm> entry : alarmServer.getActiveAlarms().entrySet()) {
            NamedObjectId id = NamedObjectId.newBuilder().setName(entry.getKey().getQualifiedName()).build();
            Alarm.Builder alarmb = Alarm.newBuilder();
            alarmb.setId(entry.getValue().id);
            alarmb.setTriggerValue(entry.getValue().triggerValue.toGpb(id));
            alarmb.setMostSevereValue(entry.getValue().mostSevereValue.toGpb(id));
            alarmb.setCurrentValue(entry.getValue().currentValue.toGpb(id));
            alarmb.setViolations(entry.getValue().violations);
            responseb.addAlarms(alarmb);
        }
        return new RestResponse(req, responseb.build(), SchemaRest.GetAlarmsResponse.WRITE);
    }
    
    /**
     * Acknowledges an alarm
     * <p>
     * POST /(instance)/api/alarms/acknowledge/(alarmId)/my/sample/qualified/parameter
     */
    private RestResponse acknowledgeAlarm(RestRequest req, int alarmId, Parameter p, YProcessor processor) throws RestException {
        if (!processor.hasAlarmServer()) {
            throw new BadRequestException("Alarms are not enabled for this instance");
        }
        
        AcknowledgeAlarmRequest request = req.bodyAsMessage(SchemaRest.AcknowledgeAlarmRequest.MERGE).build();
        
        AcknowledgeAlarmResponse.Builder responseb = AcknowledgeAlarmResponse.newBuilder();
        AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
        try {
            // TODO permissions on AlarmServer
            alarmServer.acknowledge(p, alarmId, req.getUsername(), processor.getCurrentTime(), request.getMessage());
        } catch (CouldNotAcknowledgeAlarmException e) {
            log.debug("Did not acknowledge alarm " + alarmId + ". " + e.getMessage());
            responseb.setErrorMessage(e.getMessage());
        }
        return new RestResponse(req, responseb.build(), SchemaRest.AcknowledgeAlarmResponse.WRITE);
    }
}
