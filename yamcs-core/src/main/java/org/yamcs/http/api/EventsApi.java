package org.yamcs.http.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.YamcsServer;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.EventRecorder;
import org.yamcs.events.EventProducer;
import org.yamcs.events.EventProducerFactory;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpServer;
import org.yamcs.http.MediaType;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractEventsApi;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.ExportEventsRequest;
import org.yamcs.protobuf.ListEventSourcesRequest;
import org.yamcs.protobuf.ListEventSourcesResponse;
import org.yamcs.protobuf.ListEventsRequest;
import org.yamcs.protobuf.ListEventsResponse;
import org.yamcs.protobuf.StreamEventsRequest;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.csvreader.CsvWriter;
import com.google.common.collect.BiMap;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.ExtensionRegistry.ExtensionInfo;
import com.google.protobuf.InvalidProtocolBufferException;

public class EventsApi extends AbstractEventsApi<Context> {

    private static final Log log = new Log(EventsApi.class);

    private ProtobufRegistry protobufRegistry;
    private ConcurrentMap<String, EventProducer> eventProducerMap = new ConcurrentHashMap<>();
    private AtomicInteger eventSequenceNumber = new AtomicInteger();

    @Override
    public void listEvents(Context ctx, ListEventsRequest request, Observer<ListEventsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);

        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");
        String severity = request.hasSeverity() ? request.getSeverity().toUpperCase() : "INFO";

        EventPageToken nextToken = null;
        if (request.hasNext()) {
            nextToken = EventPageToken.decode(request.getNext());
        }

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getSourceCount() > 0) {
            sqlb.whereColIn("source", request.getSourceList());
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
        if (request.hasQ()) {
            sqlb.where("body.message like ?", "%" + request.getQ() + "%");
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
        sqlb.limit(pos, limit + 1l); // one more to detect hasMore

        ListEventsResponse.Builder responseb = ListEventsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            Event last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (++count <= limit) {
                    Event incoming = (Event) tuple.getColumn("body");
                    Event event;
                    try {
                        event = Event.parseFrom(incoming.toByteArray(),
                                getProtobufRegistry().getExtensionRegistry());
                    } catch (InvalidProtocolBufferException e) {
                        throw new UnsupportedOperationException(e);
                    }

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
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void createEvent(Context ctx, CreateEventRequest request, Observer<Event> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.WriteEvents);

        String instance = ManagementApi.verifyInstance(request.getInstance());

        if (!request.hasMessage()) {
            throw new BadRequestException("Message is required");
        }

        Event.Builder eventb = Event.newBuilder();
        eventb.setCreatedBy(ctx.user.getName());
        eventb.setMessage(request.getMessage());

        if (request.hasType()) {
            eventb.setType(request.getType());
        }

        if (request.hasSource()) {
            eventb.setSource(request.getSource());
            if (request.hasSequenceNumber()) { // 'should' be linked to source
                eventb.setSeqNumber(request.getSequenceNumber());
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
        observer.complete(eventb.build());
    }

    @Override
    public void listEventSources(Context ctx, ListEventSourcesRequest request,
            Observer<ListEventSourcesResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListEventSourcesResponse.Builder responseb = ListEventSourcesResponse.newBuilder();
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
        observer.complete(responseb.build());
    }

    @Override
    public void subscribeEvents(Context ctx, SubscribeEventsRequest request, Observer<Event> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        Stream stream = ydb.getStream(EventRecorder.REALTIME_EVENT_STREAM_NAME);
        if (stream == null) {
            return; // No error, just don't send data
        }

        StreamSubscriber listener = new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                Event event = (Event) tuple.getColumn("body");
                event = Event.newBuilder(event)
                        .setGenerationTimeUTC(TimeEncoding.toString(event.getGenerationTime()))
                        .setReceptionTimeUTC(TimeEncoding.toString(event.getReceptionTime()))
                        .build();
                observer.next(event);
            }

            @Override
            public void streamClosed(Stream stream) {
            }
        };
        observer.setCancelHandler(() -> stream.removeSubscriber(listener));
        stream.addSubscriber(listener);
    }

    @Override
    public void streamEvents(Context ctx, StreamEventsRequest request, Observer<Event> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);
        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getSourceCount() > 0) {
            sqlb.whereColIn("source", request.getSourceList());
        }

        String severity = request.hasSeverity() ? request.getSeverity().toUpperCase() : "INFO";
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

        if (request.hasQ()) {
            sqlb.where("body.message like ?", "%" + request.getQ() + "%");
        }

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                Event incoming = (Event) tuple.getColumn("body");
                Event event;
                try {
                    event = Event.parseFrom(incoming.toByteArray(),
                            getProtobufRegistry().getExtensionRegistry());
                } catch (InvalidProtocolBufferException e) {
                    throw new UnsupportedOperationException(e);
                }

                Event.Builder eventb = Event.newBuilder(event);
                eventb.setGenerationTimeUTC(TimeEncoding.toString(eventb.getGenerationTime()));
                eventb.setReceptionTimeUTC(TimeEncoding.toString(eventb.getReceptionTime()));
                observer.next(eventb.build());
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    @Override
    public void exportEvents(Context ctx, ExportEventsRequest request, Observer<HttpBody> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        EventsApi.verifyEventArchiveSupport(instance);
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getSourceCount() > 0) {
            sqlb.whereColIn("source", request.getSourceList());
        }

        String severity = "INFO";
        if (request.hasSeverity()) {
            severity = request.getSeverity().toUpperCase();
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
        if (request.hasQ()) {
            sqlb.where("body.message like ?", "%" + request.getQ() + "%");
        }

        String sql = sqlb.toString();

        StreamFactory.stream(instance, sql, sqlb.getQueryArguments(), new CsvEventStreamer(observer));
    }

    /**
     * Checks if events are supported for the specified instance. This will succeed in two cases:
     * <ol>
     * <li>EventRecorder is currently enabled
     * <li>EventRecorder has been enabled in the past, but may not be any longer
     * </ol>
     */
    private static void verifyEventArchiveSupport(String instance) throws BadRequestException {
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        TableDefinition table = ydb.getTable(EventRecorder.TABLE_NAME);
        if (table == null) {
            throw new BadRequestException("No event archive support for instance '" + instance + "'");
        }
    }

    private ProtobufRegistry getProtobufRegistry() {
        YamcsServer yamcs = YamcsServer.getServer();
        if (protobufRegistry == null) {
            List<HttpServer> services = yamcs.getGlobalServices(HttpServer.class);
            protobufRegistry = services.get(0).getProtobufRegistry();
        }
        return protobufRegistry;
    }

    private static Event tupleToEvent(Tuple tuple, ProtobufRegistry protobufRegistry) {
        Event incoming = (Event) tuple.getColumn("body");
        Event event;
        try {
            event = Event.parseFrom(incoming.toByteArray(), protobufRegistry.getExtensionRegistry());
        } catch (InvalidProtocolBufferException e) {
            throw new UnsupportedOperationException(e);
        }

        Event.Builder eventb = Event.newBuilder(event);
        eventb.setGenerationTimeUTC(TimeEncoding.toString(eventb.getGenerationTime()));
        eventb.setReceptionTimeUTC(TimeEncoding.toString(eventb.getReceptionTime()));
        return eventb.build();
    }

    /**
     * Stateless continuation token for paged requests on the event table
     */
    private static class EventPageToken {

        long gentime;
        String source;
        int seqNum;

        EventPageToken(long gentime, String source, int seqNum) {
            this.gentime = gentime;
            this.source = source;
            this.seqNum = seqNum;
        }

        static EventPageToken decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded));
            return new Gson().fromJson(decoded, EventPageToken.class);
        }

        String encodeAsString() {
            String json = new Gson().toJson(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        }
    }

    private static class CsvEventStreamer implements StreamSubscriber {

        Observer<HttpBody> observer;
        ProtobufRegistry protobufRegistry;

        CsvEventStreamer(Observer<HttpBody> observer) {
            this.observer = observer;

            YamcsServer yamcs = YamcsServer.getServer();
            List<HttpServer> services = yamcs.getGlobalServices(HttpServer.class);
            protobufRegistry = services.get(0).getProtobufRegistry();

            List<ExtensionInfo> extensionFields = protobufRegistry.getExtensions(Event.getDescriptor());
            String[] rec = new String[5 + extensionFields.size()];
            int i = 0;
            rec[i++] = "Source";
            rec[i++] = "Generation Time";
            rec[i++] = "Reception Time";
            rec[i++] = "Event Type";
            rec[i++] = "Event Text";
            for (ExtensionInfo extension : extensionFields) {
                rec[i++] = "" + extension.descriptor.getName();
            }

            HttpBody metadata = HttpBody.newBuilder()
                    .setContentType(MediaType.CSV.toString())
                    .setFilename("events.csv")
                    .setData(toByteString(rec))
                    .build();

            observer.next(metadata);
        }

        @Override
        public void onTuple(Stream stream, Tuple tuple) {
            if (observer.isCancelled()) {
                stream.close();
                return;
            }

            Event event = tupleToEvent(tuple, protobufRegistry);

            List<ExtensionInfo> extensionFields = protobufRegistry.getExtensions(Event.getDescriptor());

            String[] rec = new String[5 + extensionFields.size()];
            int i = 0;
            rec[i++] = event.getSource();
            rec[i++] = event.getGenerationTimeUTC();
            rec[i++] = event.getReceptionTimeUTC();
            rec[i++] = event.getType();
            rec[i++] = event.getMessage();
            for (ExtensionInfo extension : extensionFields) {
                rec[i++] = "" + event.getField(extension.descriptor);
            }

            HttpBody body = HttpBody.newBuilder()
                    .setData(toByteString(rec))
                    .build();
            observer.next(body);
        }

        private ByteString toByteString(String[] rec) {
            ByteString.Output bout = ByteString.newOutput();
            CsvWriter writer = new CsvWriter(bout, '\t', StandardCharsets.UTF_8);
            try {
                writer.writeRecord(rec);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                writer.close();
            }

            return bout.toByteString();
        }

        @Override
        public void streamClosed(Stream stream) {
            observer.complete();
        }
    }
}
