package org.yamcs.web.rest;

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.alarms.CouldNotClearAlarmException;
import org.yamcs.protobuf.Alarms.Alarm;
import org.yamcs.protobuf.Rest.ClearAlarmResponse;
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
            if (req.isGET()) {
                return getAlarms(req, processor);
            } else {
                throw new MethodNotAllowedException(req);   
            }
        } else {
            int alarmId;
            try {
                alarmId = Integer.parseInt(req.getPathSegment(pathOffset));
            } catch (NumberFormatException e) {
                throw new NotFoundException(req);
            }
            
            // The rest of the path should be the qualified parameter name
            String[] pathSegments = req.getPathSegments();
            StringBuilder fqname = new StringBuilder();
            for (int i = pathOffset + 1; i < pathSegments.length; i++) {
                fqname.append("/").append(pathSegments[i]);
            }
            NamedObjectId id = NamedObjectId.newBuilder().setName(fqname.toString()).build();
            
            Parameter p = processor.getXtceDb().getParameter(id);
            if (p == null) {
                throw new NotFoundException(req);
            }
            if(req.isDELETE()) {
                return clearAlarm(req, alarmId, p, processor);
            } else {
                throw new MethodNotAllowedException(req);
            }
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
     * Clears an alarm
     * <p>
     * DELETE /(instance)/api/alarms/(alarmId)/my/sample/qualified/parameter
     */
    private RestResponse clearAlarm(RestRequest req, int alarmId, Parameter p, YProcessor processor) throws RestException {
        if (!processor.hasAlarmServer()) {
            throw new BadRequestException("Alarms are not enabled for this instance");
        }
        
        ClearAlarmResponse.Builder responseb = ClearAlarmResponse.newBuilder();
        AlarmServer alarmServer = processor.getParameterRequestManager().getAlarmServer();
        try {
            // TODO permissions on AlarmServer
            alarmServer.acknowledge(p, alarmId, req.getUsername());
        } catch (CouldNotClearAlarmException e) {
            log.debug("Did not clear alarm " + alarmId + ". " + e.getMessage());
            responseb.setErrorMessage(e.getMessage());
        }
        return new RestResponse(req, responseb.build(), SchemaRest.ClearAlarmResponse.WRITE);
    }
}
