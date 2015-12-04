package org.yamcs.web.rest.archive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.archive.EventRecorder;
import org.yamcs.protobuf.Rest.ListEventsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.Yamcs.Event;
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

import com.csvreader.CsvWriter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class ArchiveEventRequestHandler extends RestRequestHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ArchiveEventRequestHandler.class);

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return listEvents(req);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    private RestResponse listEvents(RestRequest req) throws RestException {
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        Set<String> sourceSet = new HashSet<>();
        for (String names : req.getQueryParameterList("source", Collections.emptyList())) {
            for (String name : names.split(",")) {
                sourceSet.add(name);
            }
        }
        
        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);
        IntervalResult ir = RestUtils.scanForInterval(req);
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!sourceSet.isEmpty()) {
            sqlb.where("source in ('" + String.join("','", sourceSet) + "')");
        }
        sqlb.descend(RestUtils.asksDescending(req, true));
        String sql = sqlb.toString();
        
        if (req.asksFor(CSV_MIME_TYPE)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)));
            CsvWriter w = new CsvWriter(bw, '\t');
            try {
                w.writeRecord(ArchiveHelper.EVENT_CSV_HEADER);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
                
            RestStreams.streamAndWait(req, sql, new RestStreamSubscriber(pos, limit) {

                @Override
                public void processTuple(Stream stream, Tuple tuple) {
                    try {
                        w.writeRecord(ArchiveHelper.tupleToCSVEvent(tuple));
                    } catch (IOException e) {
                        // TODO maybe support passing up as rest exception using custom listeners
                        log.error("Could not write csv record ", e);
                    }
                }
            });
            w.close();
            return new RestResponse(req, CSV_MIME_TYPE, buf);
        } else {
            ListEventsResponse.Builder responseb = ListEventsResponse.newBuilder();
            RestStreams.streamAndWait(req, sql, new RestStreamSubscriber(pos, limit) {

                @Override
                public void processTuple(Stream stream, Tuple tuple) {
                    Event.Builder event = Event.newBuilder((Event) tuple.getColumn("body"));
                    event.setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()));
                    event.setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()));
                    responseb.addEvent(event);    
                }
            });
            
            return new RestResponse(req, responseb.build(), SchemaRest.ListEventsResponse.WRITE);
        }
    }
}
