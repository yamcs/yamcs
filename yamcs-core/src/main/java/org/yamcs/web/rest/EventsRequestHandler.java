package org.yamcs.web.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.protobuf.Events.GetEventsRequest;
import org.yamcs.protobuf.SchemaEvents;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

/**
 * Short-lived operations wrt Events 
 * <p>
 * /(instance)/api/events
 */
public class EventsRequestHandler  implements RestRequestHandler {
    
    private static final Logger log = LoggerFactory.getLogger(EventsRequestHandler.class);
    private static AtomicInteger streamCounter = new AtomicInteger();

    @Override
    public RestResponse handleRequest(RestRequest req, int pathOffset) throws RestException {
        if (!req.hasPathSegment(pathOffset)) {
            req.assertGET();
            return getEvents(req);
        } else {
            throw new NotFoundException(req);
        }
    }
    
    public RestResponse getEvents(RestRequest req) throws RestException {
        GetEventsRequest request;
        if (req.hasBody()) {
            request = req.bodyAsMessage(SchemaEvents.GetEventsRequest.MERGE).build();
        } else {
            request = qsToGetEventsRequest(req);
        }
       
        long start = TimeEncoding.INVALID_INSTANT;        
        if (request.hasStart()) start = request.getStart();
        else if (request.hasUtcStart()) start = TimeEncoding.parse(request.getUtcStart());
        
        long stop = TimeEncoding.INVALID_INSTANT;
        if (request.hasStop()) stop = request.getStop();
        else if (request.hasUtcStop()) stop = TimeEncoding.parse(request.getUtcStop());
        
        if (start > stop)
            throw new BadRequestException("Stop has to be greater than start");
        
        StringBuilder buf = new StringBuilder("select * from events");
        if (start != TimeEncoding.INVALID_INSTANT || stop != TimeEncoding.INVALID_INSTANT) {
            buf.append(" where ");
            if (start != TimeEncoding.INVALID_INSTANT) {
                buf.append("gentime >= " + start);
                if (stop != TimeEncoding.INVALID_INSTANT) buf.append(" and gentime < " + stop);
            } else {
                buf.append("gentime < " + stop);
            }
        }
        
        String sql = buf.toString();
        String contentType = req.deriveTargetContentType();
        streamResponse(req, sql, contentType);
        return null;
    }
    
    /**
     * Sends chunks of max 500 records
     */
    private void streamResponse(RestRequest req, String sql, String contentType) throws RestException {
        YarchDatabase ydb = YarchDatabase.getInstance(req.getYamcsInstance());
        String streamName = "rest_get_events" + streamCounter.incrementAndGet();
        try {
            String streamsql = "create stream " + streamName + " as " + sql;
            log.info("Executing " + streamsql);
            ydb.execute(streamsql);
        } catch (StreamSqlException | ParseException e) {
            throw new InternalServerErrorException(e);
        }
        
        RestUtils.startChunkedTransfer(req, contentType);

        List<Event> events = new ArrayList<>();
        Stream s = ydb.getStream(streamName);
        s.addSubscriber(new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                Event event = (Event) tuple.getColumn("body");
                events.add(event);
                if (events.size() == 500) {
                    try {
                        RestUtils.writeChunk(req, contentType, events, SchemaYamcs.Event.WRITE);
                    } catch (IOException e) {
                        log.error("Skipping chunk", e);
                    }
                    events.clear();
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (!events.isEmpty()) {
                    try {
                        RestUtils.writeChunk(req, contentType, events, SchemaYamcs.Event.WRITE);
                    } catch (IOException e) {
                        log.error("Skipping chunk", e);
                    }
                }
                RestUtils.stopChunkedTransfer(req);
            }            
        });
        
        // All set. Start tuple production
        s.start();
    }
    
    private static GetEventsRequest qsToGetEventsRequest(RestRequest req) throws BadRequestException {
        GetEventsRequest.Builder requestb = GetEventsRequest.newBuilder();
        if (req.hasQueryParameter("start"))
            requestb.setStart(req.getQueryParameterAsLong("start"));
        if (req.hasQueryParameter("stop"))
            requestb.setStop(req.getQueryParameterAsLong("stop"));
        if (req.hasQueryParameter("utcStart"))
            requestb.setUtcStart(req.getQueryParameter("utcStart"));
        if (req.hasQueryParameter("utcStop"))
            requestb.setUtcStop(req.getQueryParameter("utcStop"));
        return requestb.build();
    }
}
