package org.yamcs.web.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.Rest.ListPacketsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.rest.RestUtils.IntervalResult;
import org.yamcs.yarch.Tuple;

public class ArchivePacketRequestHandler extends RestRequestHandler {

    @Override
    protected RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listPackets(req, TimeEncoding.INVALID_INSTANT);
        } else {
            long gentime = req.getPathSegmentAsDate(pathOffset);
            
            pathOffset++;
            if (!req.hasPathSegment(pathOffset)) {
                return listPackets(req, gentime);
            } else {
                int seqnum = req.getPathSegmentAsInt(pathOffset);
                return getPacket(req, gentime, seqnum);
            }
        }
    }
    
    private RestResponse listPackets(RestRequest req, long gentime) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        Set<String> nameSet = new HashSet<>();
        for (String names : req.getQueryParameterList("name", Collections.emptyList())) {
            for (String name : names.split(",")) {
                nameSet.add(name.trim());
            }
        }
        
        StringBuilder sqlb = new StringBuilder("select * from ").append(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval() || gentime != TimeEncoding.INVALID_INSTANT || !nameSet.isEmpty()) {
            sqlb.append(" where ");
            boolean first = true;
            if (ir.hasInterval()) {
                sqlb.append(ir.asSqlCondition("gentime"));
                first = false;
            }
            if (gentime != TimeEncoding.INVALID_INSTANT) {
                if (!first) sqlb.append(" and ");
                sqlb.append("gentime = ").append(gentime);
                first = false;
            }
            if (!nameSet.isEmpty()) {
                if (!first) sqlb.append(" and ");
                sqlb.append("pname in ('").append(String.join("','", nameSet)).append("')");
                first = false;
            }
        }
        if (RestUtils.asksDescending(req, true)) {
            sqlb.append(" order desc");
        }
        
        ListPacketsResponse.Builder responseb = ListPacketsResponse.newBuilder();
        RestStreams.streamAndWait(req, sqlb.toString(), new RestStreamSubscriber(pos, limit) {

            @Override
            public void onTuple(Tuple tuple) {
                TmPacketData pdata = ArchiveHelper.tupleToPacketData(tuple);
                responseb.addPacket(pdata);
            }
        });
        
        return new RestResponse(req, responseb.build(), SchemaRest.ListPacketsResponse.WRITE);
    }
    
    private RestResponse getPacket(RestRequest req, long gentime, int seqnum) throws RestException {
        StringBuilder sqlb = new StringBuilder("select * from ").append(XtceTmRecorder.TABLE_NAME);
        sqlb.append(" where gentime = ").append(gentime).append(" and seqNum = ").append(seqnum);
        List<TmPacketData> packets = new ArrayList<>();
        RestStreams.streamAndWait(req, sqlb.toString(), new RestStreamSubscriber(0, 2) {

            @Override
            public void onTuple(Tuple tuple) {
                TmPacketData pdata = ArchiveHelper.tupleToPacketData(tuple);
                packets.add(pdata);
            }
        });
        
        if (packets.isEmpty()) {
            throw new NotFoundException(req, "No packet for id (" + gentime + ", " + seqnum + ")");
        } else if (packets.size() > 1) {
            throw new InternalServerErrorException("Too many results");
        } else {
            return new RestResponse(req, packets.get(0), SchemaYamcs.TmPacketData.WRITE);
        }
    }
}
