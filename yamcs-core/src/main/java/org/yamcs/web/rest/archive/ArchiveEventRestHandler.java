package org.yamcs.web.rest.archive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.archive.EventRecorder;
import org.yamcs.protobuf.Rest.ListEventsResponse;
import org.yamcs.protobuf.SchemaRest;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.HttpException;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.Tuple;

import com.csvreader.CsvWriter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class ArchiveEventRestHandler extends RestHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ArchiveEventRestHandler.class);

    Map<String, EventProducer> eventProducerMap = new HashMap<>();

    @Route(path = "/api/archive/:instance/events", method = "GET")
    public void listEvents(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        
        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        
        Set<String> sourceSet = new HashSet<>();
        for (String names : req.getQueryParameterList("source", Collections.emptyList())) {
            for (String name : names.split(",")) {
                sourceSet.add(name);
            }
        }
        
        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);
        IntervalResult ir = req.scanForInterval();
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
        }
        if (!sourceSet.isEmpty()) {
            sqlb.where("source in ('" + String.join("','", sourceSet) + "')");
        }
        sqlb.descend(req.asksDescending(true));
        String sql = sqlb.toString();
        
        if (req.asksFor(MediaType.CSV)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)));
            CsvWriter w = new CsvWriter(bw, '\t');
            try {
                w.writeRecord(ArchiveHelper.EVENT_CSV_HEADER);
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }
                
            RestStreams.stream(instance, sql, new RestStreamSubscriber(pos, limit) {
                @Override
                public void processTuple(Stream stream, Tuple tuple) {
                    try {
                        w.writeRecord(ArchiveHelper.tupleToCSVEvent(tuple));
                    } catch (IOException e) {
                        // TODO maybe support passing up as rest exception using custom listeners
                        log.error("Could not write csv record ", e);
                    }
                }

                @Override
                public void streamClosed(Stream stream) {
                    w.close();
                    completeOK(req, MediaType.CSV, buf);
                }
            });
         
        } else {
            ListEventsResponse.Builder responseb = ListEventsResponse.newBuilder();
            RestStreams.stream(instance, sql, new RestStreamSubscriber(pos, limit) {

                @Override
                public void processTuple(Stream stream, Tuple tuple) {
                    Event.Builder event = Event.newBuilder((Event) tuple.getColumn("body"));
                    event.setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()));
                    event.setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()));
                    responseb.addEvent(event);    
                }

                @Override
                public void streamClosed(Stream stream) {
                    completeOK(req, responseb.build(), SchemaRest.ListEventsResponse.WRITE);
                }
            });
        }
    }


    @Route(path = "/api/archive/:instance/events", method = "POST")
    public void issueCommand(RestRequest req) throws HttpException {

        // get event from request
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        Event event = req.bodyAsMessage(SchemaYamcs.Event.MERGE).build();

        // get event producer for this instance
        EventProducer eventProducer = null;
        if(eventProducerMap.containsKey(instance))
            eventProducer = eventProducerMap.get(instance);
        else {
            eventProducer = EventProducerFactory.getEventProducer(instance);
            eventProducerMap.put(instance, eventProducer);
        }

        // update event reception time
        event = event.toBuilder().setReceptionTime(YamcsServer.getTimeService(instance).getMissionTime()).build();

        // send event
        log.debug("Adding event from REST API: " + event.toString());
        eventProducer.sendEvent(event);
        completeOK(req);
    }
}
