package org.yamcs.web.rest.archive;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.archive.AlarmRecorder;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Rest.ListAlarmsResponse;
import org.yamcs.protobuf.SchemaAlarms;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.InternalServerErrorException;
import org.yamcs.web.rest.NotFoundException;
import org.yamcs.web.rest.RestException;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequestHandler;
import org.yamcs.web.rest.RestResponse;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.RestUtils;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper;
import org.yamcs.web.rest.mdb.MissionDatabaseHelper.MatchResult;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

public class ArchiveAlarmRequestHandler extends RestRequestHandler {

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listAlarms(req, null, TimeEncoding.INVALID_INSTANT);
        } else {
            MatchResult<Parameter> mr = MissionDatabaseHelper.matchParameterName(req, pathOffset);
            if (!mr.matches()) {
                throw new NotFoundException(req);
            }
            pathOffset = mr.getPathOffset();
            if (req.hasPathSegment(pathOffset)) {
                long triggerTime = req.getPathSegmentAsDate(pathOffset);
                if (req.hasPathSegment(pathOffset + 1)) {
                    int sequenceNumber = req.getPathSegmentAsInt(pathOffset + 1);
                    if (req.hasPathSegment(pathOffset + 2)) {
                        throw new NotFoundException(req);
                    } else {
                        return getAlarm(req, mr.getMatch(), triggerTime, sequenceNumber);
                    }
                } else {
                    return listAlarms(req, mr.getMatch().getQualifiedName(), triggerTime);
                }
            } else {
                return listAlarms(req, mr.getMatch().getQualifiedName(), TimeEncoding.INVALID_INSTANT);
            }
        }
    }
    
    private RestResponse listAlarms(RestRequest req, String parameterName, long triggerTime) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        SqlBuilder sqlb = new SqlBuilder(AlarmRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("triggerTime"));    
        }
        if (parameterName != null) {
            sqlb.where("parameter = '" + parameterName + "'");
        }
        if (triggerTime != TimeEncoding.INVALID_INSTANT) {
            sqlb.where("triggerTime = " + triggerTime);
        }
        sqlb.descend(RestUtils.asksDescending(req, true));
        
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        RestStreams.streamAndWait(req, sqlb.toString(), new RestStreamSubscriber(pos, limit) {

            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple);
                responseb.addAlarm(alarm);
            }
        });
        
        return new RestResponse(req, responseb.build(), SchemaRest.ListAlarmsResponse.WRITE);
    }
    
    private RestResponse getAlarm(RestRequest req, Parameter p, long triggerTime, int seqnum) throws RestException {
        String sql = new SqlBuilder(AlarmRecorder.TABLE_NAME)
                .where("triggerTime = " + triggerTime)
                .where("seqNum = " + seqnum)
                .where("parameter = '" + p.getQualifiedName() + "'")
                .toString();
        
        List<AlarmData> alarms = new ArrayList<>();
        RestStreams.streamAndWait(req, sql, new RestStreamSubscriber(0, 2) {

            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple);
                alarms.add(alarm);
            }
        });
        
        if (alarms.isEmpty()) {
            throw new NotFoundException(req, "No alarm for id (" + p.getQualifiedName() + ", " + triggerTime + ", " + seqnum + ")");
        } else if (alarms.size() > 1) {
            throw new InternalServerErrorException("Too many results");
        } else {
            return new RestResponse(req, alarms.get(0), SchemaAlarms.AlarmData.WRITE);
        }
    }
}
