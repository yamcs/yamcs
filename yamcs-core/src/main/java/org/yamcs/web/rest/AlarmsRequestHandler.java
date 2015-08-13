package org.yamcs.web.rest;

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.AlarmServer;
import org.yamcs.AlarmServer.ActiveAlarm;
import org.yamcs.YProcessor;
import org.yamcs.protobuf.Alarms.Alarm;
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
        if (!req.isGET()) {
            throw new MethodNotAllowedException(req);
        }
        return getAlarms(req);
    }

    /**
     * Lists all active alarms
     * <p>
     * GET /(instance)/api/alarms
     */
    private RestResponse getAlarms(RestRequest req) throws RestException {
        YProcessor processor = YProcessor.getInstance(req.yamcsInstance, "realtime");
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
}
