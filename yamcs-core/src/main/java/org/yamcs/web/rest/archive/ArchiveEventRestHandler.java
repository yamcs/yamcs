package org.yamcs.web.rest.archive;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.MediaType;
import org.yamcs.archive.EventRecorder;
import org.yamcs.protobuf.Archive.EventSourceInfo;
import org.yamcs.protobuf.Rest.CreateEventRequest;
import org.yamcs.protobuf.Rest.ListEventsResponse;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpServer;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreamSubscriber;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.csvreader.CsvWriter;
import com.google.common.collect.BiMap;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.InvalidProtocolBufferException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class ArchiveEventRestHandler extends RestHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveEventRestHandler.class);

    private ConcurrentMap<String, EventProducer> eventProducerMap = new ConcurrentHashMap<>();
    private AtomicInteger eventSequenceNumber = new AtomicInteger();
    private ExtensionRegistry gpbExtensionRegistry;

    @Route(path = "/api/archive/:instance/events", method = "GET")
    public void listEvents(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        verifyEventArchiveSupport(instance);

        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayReadEvents);

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        String severity = req.getQueryParameter("severity", "INFO").toUpperCase();

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
            sqlb.whereColIn("source", sourceSet);
        }
        switch (severity) {
        case "INFO":
            break;
        case "WATCH":
            sqlb.where("body.severity != 'INFO'");
            break;
        case "WARNING":
            sqlb.whereColIn("body.severity", Arrays.asList("WARNING", "DISTRESS", "CRITICAL", "SEVERE", "ERROR"));
            break;
        case "DISTRESS":
            sqlb.whereColIn("body.severity", Arrays.asList("DISTRESS", "CRITICAL", "SEVERE", "ERROR"));
            break;
        case "CRITICAL":
            sqlb.whereColIn("body.severity", Arrays.asList("CRITICAL", "SEVERE", "ERROR"));
            break;
        case "SEVERE":
            sqlb.whereColIn("body.severity", Arrays.asList("SEVERE", "ERROR"));
            break;
        default:
            sqlb.whereColIn("body.severity = ?", Arrays.asList(severity));
        }
        if (req.hasQueryParameter("q")) {
            sqlb.where("body.message like ?", "%" + req.getQueryParameter("q") + "%");
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

            RestStreams.stream(instance, sql, sqlb.getQueryArguments(), new RestStreamSubscriber(pos, limit) {
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
            RestStreams.stream(instance, sql, sqlb.getQueryArguments(), new RestStreamSubscriber(pos, limit) {

                @Override
                public void processTuple(Stream stream, Tuple tuple) {
                    try {
                        Event incoming = (Event) tuple.getColumn("body");
                        Event event = Event.parseFrom(incoming.toByteArray(), getExtensionRegistry());

                        Event.Builder eventb = Event.newBuilder(event);
                        eventb.setGenerationTimeUTC(TimeEncoding.toString(eventb.getGenerationTime()));
                        eventb.setReceptionTimeUTC(TimeEncoding.toString(eventb.getReceptionTime()));
                        responseb.addEvent(eventb.build());
                    } catch (InvalidProtocolBufferException e) {
                        log.error("Invalid GPB message", e);
                    }
                }

                @Override
                public void streamClosed(Stream stream) {
                    completeOK(req, responseb.build());
                }
            });
        }
    }

    @Deprecated // To be removed once all official clients use postEvents2 logic
    @Route(path = "/api/archive/:instance/events", method = "POST")
    public void postEvent(RestRequest req) throws HttpException {
        log.warn("Deprecated use of legacy API. "
                + "Use new API at /api/archive/:instance/events2 instead of /api/archive/:instance/events");

        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayWriteEvents);

        // get event from request
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        Event event = req.bodyAsMessage(Event.newBuilder()).build();

        // get event producer for this instance
        EventProducer eventProducer = eventProducerMap.computeIfAbsent(instance, x -> {
            return EventProducerFactory.getEventProducer(x);
        });

        // update event reception time
        event = event.toBuilder().setReceptionTime(YamcsServer.getTimeService(instance).getMissionTime()).build();

        // send event
        log.debug("Adding event from REST API: {}", event.toString());
        eventProducer.sendEvent(event);
        completeOK(req);
    }

    // TODO rename the path to /api/archive/:instance/events once all official clients are migrated to this new API.
    @Route(path = "/api/archive/:instance/events2", method = "POST")
    public void postEvent2(RestRequest req) throws HttpException {

        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayWriteEvents);

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        CreateEventRequest request = req.bodyAsMessage(CreateEventRequest.newBuilder()).build();

        if (!request.hasMessage()) {
            throw new BadRequestException("Message is required");
        }

        Event.Builder eventb = Event.newBuilder();
        eventb.setSource("User");
        eventb.setCreatedBy(req.getUsername());
        eventb.setSeqNumber(eventSequenceNumber.getAndIncrement());
        eventb.setMessage(request.getMessage());

        if (request.hasType()) {
            eventb.setType(request.getType());
        }

        long missionTime = YamcsServer.getTimeService(instance).getMissionTime();
        if (request.hasTime()) {
            long eventTime = TimeEncoding.parse(request.getTime());
            eventb.setGenerationTime(eventTime);
            eventb.setReceptionTime(missionTime);
        } else {
            eventb.setGenerationTime(missionTime);
            eventb.setReceptionTime(missionTime);
        }

        if (request.hasSeverity()) {
            EventSeverity severity = EventSeverity.valueOf(request.getSeverity().toUpperCase());
            if (severity == null) {
                throw new BadRequestException("Unsupported severity: " + request.getSeverity());
            }
            eventb.setSeverity(severity);
        } else {
            eventb.setSeverity(EventSeverity.INFO);
        }

        EventProducer eventProducer = eventProducerMap.computeIfAbsent(instance, x -> {
            return EventProducerFactory.getEventProducer(x);
        });

        // Distribute event (without augmented fields, or they'll get stored)
        Event event = eventb.build();
        log.debug("Adding event: {}", event.toString());
        eventProducer.sendEvent(event);

        // Send back the (augmented) event in response
        eventb = Event.newBuilder(event);
        eventb.setGenerationTimeUTC(TimeEncoding.toString(eventb.getGenerationTime()));
        eventb.setReceptionTimeUTC(TimeEncoding.toString(eventb.getReceptionTime()));
        completeOK(req, eventb.build());
    }

    /**
     * Shows the distinct sources that occur in the events table. Theoretically the user could also retrieve this
     * information via the table-related API, but then users without MayReadTables privilege, would not be able to call
     * it.
     */
    @Route(path = "/api/archive/:instance/events/sources", method = "GET")
    public void listSources(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        verifyEventArchiveSupport(instance);
        verifyAuthorization(req.getAuthToken(), SystemPrivilege.MayReadEvents);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        EventSourceInfo.Builder responseb = EventSourceInfo.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(EventRecorder.TABLE_NAME);
        BiMap<String, Short> enumValues = tableDefinition.getEnumValues("source");
        if (enumValues != null) {
            List<String> unsortedSources = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                unsortedSources.add(entry.getKey());
            }
            Collections.sort(unsortedSources);
            responseb.addAllSource(unsortedSources);
        }
        completeOK(req, responseb.build());
    }

    /**
     * Checks if events are supported for the specified instance. This will succeed in two cases:
     * <ol>
     * <li>EventRecorder is currently enabled
     * <li>EventRecorder has been enabled in the past, but may not be any longer
     * </ol>
     */
    public static void verifyEventArchiveSupport(String instance) throws BadRequestException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = ydb.getTable(EventRecorder.TABLE_NAME);
        if (table == null) {
            throw new BadRequestException("No event archive support for instance '" + instance + "'");
        }
    }

    private ExtensionRegistry getExtensionRegistry() {
        if (gpbExtensionRegistry == null) {
            HttpServer httpServer = YamcsServer.getGlobalService(HttpServer.class);
            gpbExtensionRegistry = httpServer.getGpbExtensionRegistry();
        }
        return gpbExtensionRegistry;
    }
}
