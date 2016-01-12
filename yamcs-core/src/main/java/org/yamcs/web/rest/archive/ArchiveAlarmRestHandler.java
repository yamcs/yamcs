package org.yamcs.web.rest.archive;

import org.yamcs.archive.AlarmRecorder;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Rest.ListAlarmsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.web.HttpException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

import io.netty.channel.ChannelFuture;

public class ArchiveAlarmRestHandler extends RestHandler {

    @Route(path="/api/archive/:instance/alarms", method="GET")
    @Route(path="/api/archive/:instance/alarms/:parameter*", method="GET")
    //@Route(path="/api/archive/:instance/alarms/:parameter*/:triggerTime?", method="GET") // same comment as below
    public ChannelFuture listAlarms(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
                
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        SqlBuilder sqlb = new SqlBuilder(AlarmRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("triggerTime"));    
        }
        if (req.hasRouteParam("parameter")) {
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            Parameter p = verifyParameter(req, mdb, req.getRouteParam("parameter"));
            sqlb.where("parameter = '" + p.getQualifiedName() + "'");
        }
        /*if (req.hasRouteParam("triggerTime")) {
            sqlb.where("triggerTime = " + req.getDateRouteParam("triggerTime"));
        }*/
        sqlb.descend(req.asksDescending(true));
        
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        RestStreams.streamAndWait(instance, sqlb.toString(), new RestStreamSubscriber(pos, limit) {

            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple);
                responseb.addAlarm(alarm);
            }
        });
        
        return sendOK(req, responseb.build(), SchemaRest.ListAlarmsResponse.WRITE);
    }
    
    /*
     Commented out because in its current form the handling is ambiguous to the previous
     operation. Perhaps should use queryparams instead. and have parameter* always be terminal
    @Route(path="/api/archive/:instance/alarms/:parameter*   /:triggerTime/:seqnum", method="GET")
    public ChannelFuture getAlarm(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = verifyParameter(req, mdb, req.getRouteParam("parameter"));
        
        long triggerTime = req.getDateRouteParam("triggerTime");
        int seqNum = req.getIntegerRouteParam("seqnum");
        
        String sql = new SqlBuilder(AlarmRecorder.TABLE_NAME)
                .where("triggerTime = " + triggerTime)
                .where("seqNum = " + seqNum)
                .where("parameter = '" + p.getQualifiedName() + "'")
                .toString();
        
        List<AlarmData> alarms = new ArrayList<>();
        RestStreams.streamAndWait(instance, sql, new RestStreamSubscriber(0, 2) {

            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple);
                alarms.add(alarm);
            }
        });
        
        if (alarms.isEmpty()) {
            throw new NotFoundException(req, "No alarm for id (" + p.getQualifiedName() + ", " + triggerTime + ", " + seqNum + ")");
        } else if (alarms.size() > 1) {
            throw new InternalServerErrorException("Too many results");
        } else {
            return sendOK(req, alarms.get(0), SchemaAlarms.AlarmData.WRITE);
        }
    }*/
}
