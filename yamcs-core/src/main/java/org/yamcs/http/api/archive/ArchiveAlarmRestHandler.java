package org.yamcs.http.api.archive;

import org.yamcs.archive.AlarmRecorder;
import org.yamcs.http.HttpException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.http.api.RestRequest;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.http.api.RestStreams;
import org.yamcs.http.api.Route;
import org.yamcs.http.api.SqlBuilder;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.ListAlarmsResponse;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;

public class ArchiveAlarmRestHandler extends RestHandler {

    @Route(rpc = "StreamArchive.ListAlarms")
    public void listAlarms(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);

        SqlBuilder sqlbParam = new SqlBuilder(AlarmRecorder.PARAMETER_ALARM_TABLE_NAME);
        SqlBuilder sqlbEvent = new SqlBuilder(AlarmRecorder.EVENT_ALARM_TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlbParam.where(ir.asSqlCondition("triggerTime"));
            sqlbEvent.where(ir.asSqlCondition("triggerTime"));
        }

        /*
         * if (req.hasRouteParam("triggerTime")) { sqlb.where("triggerTime = " + req.getDateRouteParam("triggerTime"));
         * }
         */
        sqlbParam.descend(req.asksDescending(true));
        sqlbEvent.descend(req.asksDescending(true));
        sqlbParam.limit(pos, limit);
        sqlbEvent.limit(pos, limit);

        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        String q = "MERGE (" + sqlbParam.toString() + "), (" + sqlbEvent.toString() + ") USING triggerTime ORDER DESC";
        RestStreams.stream(instance, q, sqlbParam.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple, true);
                responseb.addAlarm(alarm);
            }

            @Override
            public void streamClosed(Stream stream) {
                completeOK(req, responseb.build());
            }
        });
    }

    @Route(rpc = "StreamArchive.ListParameterAlarms")
    public void listParameterAlarms(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean detail = req.getQueryParameterAsBoolean("detail", false);

        SqlBuilder sqlb = new SqlBuilder(AlarmRecorder.PARAMETER_ALARM_TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("triggerTime"));
        }

        if (req.hasRouteParam("parameter")) {
            XtceDb mdb = XtceDbFactory.getInstance(instance);
            Parameter p = verifyParameter(req, mdb, req.getRouteParam("parameter"));
            sqlb.where("parameter = ?", p.getQualifiedName());
        }
        /*
         * if (req.hasRouteParam("triggerTime")) { sqlb.where("triggerTime = " + req.getDateRouteParam("triggerTime"));
         * }
         */
        sqlb.descend(req.asksDescending(true));
        sqlb.limit(pos, limit);
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple, detail);
                responseb.addAlarm(alarm);
            }

            @Override
            public void streamClosed(Stream stream) {
                completeOK(req, responseb.build());
            }
        });
    }
}
