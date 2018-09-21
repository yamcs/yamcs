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
import org.yamcs.web.GpbExtensionRegistry;
import org.yamcs.web.HttpException;
import org.yamcs.web.HttpServer;
import org.yamcs.web.InternalServerErrorException;
import org.yamcs.web.rest.RestHandler;
import org.yamcs.web.rest.RestRequest;
import org.yamcs.web.rest.RestRequest.IntervalResult;
import org.yamcs.web.rest.RestStreams;
import org.yamcs.web.rest.Route;
import org.yamcs.web.rest.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.csvreader.CsvWriter;
import com.google.common.collect.BiMap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;

public class ArchiveEventRestHandler extends RestHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveEventRestHandler.class);

    private ConcurrentMap<String, EventProducer> eventProducerMap = new ConcurrentHashMap<>();
    private AtomicInteger eventSequenceNumber = new AtomicInteger();
    private GpbExtensionRegistry gpbExtensionRegistry;

    @Route(path = "/api/archive/:instance/events", method = "GET")
    public void listEvents(RestRequest req) throws HttpException {
        String instance = verifyInstance(req, req.getRouteParam("instance"));
        verifyEventArchiveSupport(instance);

        checkSystemPrivilege(req, SystemPrivilege.ReadEvents);

        long pos = req.getQueryParameterAsLong("pos", 0);
        int limit = req.getQueryParameterAsInt("limit", 100);
        boolean desc = req.asksDescending(true);
        String severity = req.getQueryParameter("severity", "INFO").toUpperCase();

        Set<String> sourceSet = new HashSet<>();
        for (String names : req.getQueryParameterList("source", Collections.emptyList())) {
            for (String name : names.split(",")) {
                sourceSet.add(name);
            }
        }

        PacketPageToken nextToken = null;
        if (req.hasQueryParameter("next")) {
            String next = req.getQueryParameter("next");
            nextToken = PacketPageToken.decode(next);
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
        if (nextToken != null) {
            // TODO this currently ignores the source column (also part of the key)
            // Requires string comparison in StreamSQL, and an even more complicated query condition...
            if (desc) {
                sqlb.where("(gentime < ? or (gentime = ? and seqNum < ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            } else {
                sqlb.where("(gentime > ? or (gentime = ? and seqNum > ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            }
        }

        sqlb.descend(desc);
        sqlb.limit(pos, limit);

        if (req.asksFor(MediaType.CSV)) {
            ByteBuf buf = req.getChannelHandlerContext().alloc().buffer();
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new ByteBufOutputStream(buf)));
            CsvWriter w = new CsvWriter(bw, '\t');
            try {
                w.writeRecord(ArchiveHelper.getEventCSVHeader(getExtensionRegistry()));
            } catch (IOException e) {
                throw new InternalServerErrorException(e);
            }

            RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    try {
                        w.writeRecord(ArchiveHelper.tupleToCSVEvent(tuple, getExtensionRegistry()));
                    } catch (IOException e) {
                        // TODO maybe support passing up as rest exception using custom listeners
                        log.error("Could not write CSV record ", e);
                    }
                }

                @Override
                public void streamClosed(Stream stream) {
                    w.close();
                    completeOK(req, MediaType.CSV, buf);
                }
            });

        } else {
            sqlb.limit(pos, limit + 1); // one more to detect hasMore

            ListEventsResponse.Builder responseb = ListEventsResponse.newBuilder();
            RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

                Event last;
                int count;

                @Override
                public void onTuple(Stream stream, Tuple tuple) {
                    if (++count <= limit) {
                        Event incoming = (Event) tuple.getColumn("body");
                        Event event = getExtensionRegistry().getExtendedEvent(incoming);

                        Event.Builder eventb = Event.newBuilder(event);
                        eventb.setGenerationTimeUTC(TimeEncoding.toString(eventb.getGenerationTime()));
                        eventb.setReceptionTimeUTC(TimeEncoding.toString(eventb.getReceptionTime()));
                        responseb.addEvent(eventb.build());
                        last = event;
                    }
                }

                @Override
                public void streamClosed(Stream stream) {
                    if (count > limit) {
                        EventPageToken token = new EventPageToken(last.getGenerationTime(), last.getSource(),
                                last.getSeqNumber());
                        responseb.setContinuationToken(token.encodeAsString());
                    }
                    completeOK(req, responseb.build());
                }
            });
        }
    }

    @Route(path = "/api/archive/:instance/events", method = "POST")
    @Route(path = "/api/archive/:instance/events2", method = "POST") // TODO remove if no longer used by clients
    public void postEvent(RestRequest req) throws HttpException {
        checkSystemPrivilege(req, SystemPrivilege.WriteEvents);

        String instance = verifyInstance(req, req.getRouteParam("instance"));
        CreateEventRequest request = req.bodyAsMessage(CreateEventRequest.newBuilder()).build();

        if (!request.hasMessage()) {
            throw new BadRequestException("Message is required");
        }

        Event.Builder eventb = Event.newBuilder();
        eventb.setCreatedBy(req.getUser().getUsername());
        eventb.setMessage(request.getMessage());

        if (request.hasType()) {
            eventb.setType(request.getType());
        }

        if (request.hasSource()) {
            eventb.setSource(request.getSource());
            if (eventb.hasSeqNumber()) { // 'should' be linked to source
                eventb.setSeqNumber(eventb.getSeqNumber());
            } else {
                eventb.setSeqNumber(eventSequenceNumber.getAndIncrement());
            }
        } else {
            eventb.setSource("User");
            eventb.setSeqNumber(eventSequenceNumber.getAndIncrement());
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
        checkSystemPrivilege(req, SystemPrivilege.ReadEvents);

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

    private GpbExtensionRegistry getExtensionRegistry() {
        if (gpbExtensionRegistry == null) {
            List<HttpServer> services = YamcsServer.getGlobalServices(HttpServer.class);
            gpbExtensionRegistry = services.get(0).getGpbExtensionRegistry();
        }
        return gpbExtensionRegistry;
    }
}
