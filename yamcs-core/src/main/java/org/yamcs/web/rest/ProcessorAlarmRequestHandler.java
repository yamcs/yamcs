package org.yamcs.web.rest;

import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.alarms.ActiveAlarm;
import org.yamcs.alarms.AlarmServer;
import org.yamcs.protobuf.Alarms.AlarmInfo;
import org.yamcs.protobuf.Rest.ListAlarmsResponse;
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
            throw new NotFoundException(req);
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
    
    public static AlarmInfo toAlarmInfo(Parameter p, ActiveAlarm activeAlarm) {
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
