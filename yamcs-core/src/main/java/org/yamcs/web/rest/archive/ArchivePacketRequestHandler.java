package org.yamcs.web.rest.archive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.protobuf.Rest.ListPacketsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.TmPacketData;
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
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class ArchivePacketRequestHandler extends RestRequestHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ArchivePacketRequestHandler.class);

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
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
        
        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (gentime != TimeEncoding.INVALID_INSTANT) {
            sqlb.where("gentime = " + gentime);
        }
        if (!nameSet.isEmpty()) {
            sqlb.where("pname in ('" + String.join("','", nameSet) + "')");
        }        
        sqlb.descend(RestUtils.asksDescending(req, true));
        
        if (req.asksFor(BINARY_MIME_TYPE)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            try (ByteBufOutputStream bufOut = new ByteBufOutputStream(buf)) {
                RestStreams.streamAndWait(req, sqlb.toString(), new RestStreamSubscriber(pos, limit) {
                    
                    @Override
                    public void processTuple(Stream stream, Tuple tuple) {
                        TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                        try {
                            pdata.getPacket().writeTo(bufOut);
                        } catch (IOException e) {
                            log.warn("ignoring packet", e);
                            // should improve to somehow throw upwards
                        }
                    }
                });
                bufOut.close();
                return new RestResponse(req, BINARY_MIME_TYPE, buf);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
        } else {
            ListPacketsResponse.Builder responseb = ListPacketsResponse.newBuilder();
            RestStreams.streamAndWait(req, sqlb.toString(), new RestStreamSubscriber(pos, limit) {
    
                @Override
                public void processTuple(Stream stream, Tuple tuple) {
                    TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                    responseb.addPacket(pdata);
                }
            });
            return new RestResponse(req, responseb.build(), SchemaRest.ListPacketsResponse.WRITE);
        }
    }
    
    private RestResponse getPacket(RestRequest req, long gentime, int seqnum) throws RestException {
        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME)
                .where("gentime = " + gentime, "seqNum = " + seqnum);
        
        List<TmPacketData> packets = new ArrayList<>();
        RestStreams.streamAndWait(req, sqlb.toString(), new RestStreamSubscriber(0, 2) {

            @Override
            public void processTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
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
