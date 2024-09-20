package org.yamcs.http.api;

import static org.yamcs.StandardTupleDefinitions.GENTIME_COLUMN;
import static org.yamcs.StandardTupleDefinitions.SOURCE_COLUMN;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
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
import org.yamcs.http.MediaType;
import org.yamcs.logging.Log;
import org.yamcs.protobuf.AbstractEventsApi;
import org.yamcs.protobuf.CreateEventRequest;
import org.yamcs.protobuf.Event;
import org.yamcs.protobuf.Event.EventSeverity;
import org.yamcs.protobuf.ExportEventsRequest;
import org.yamcs.protobuf.ListEventSourcesRequest;
import org.yamcs.protobuf.ListEventSourcesResponse;
import org.yamcs.protobuf.ListEventsRequest;
import org.yamcs.protobuf.ListEventsResponse;
import org.yamcs.protobuf.StreamEventsRequest;
import org.yamcs.protobuf.SubscribeEventsRequest;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.yarch.SqlBuilder;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.protobuf.Db;

import com.csvreader.CsvWriter;
import com.google.common.collect.BiMap;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Timestamps;

public class EventsApi extends AbstractEventsApi<Context> {

    static final String INFO = "INFO";
    static final String WATCH = "WATCH";
    static final String WARNING = "WARNING";
    static final String DISTRESS = "DISTRESS";
    static final String CRITICAL = "CRITICAL";
    static final String SEVERE = "SEVERE";
    static final String ERROR = "ERROR";

    private static final Log log = new Log(EventsApi.class);

    private ConcurrentMap<String, EventProducer> eventProducerMap = new ConcurrentHashMap<>();
    private AtomicInteger eventSequenceNumber = new AtomicInteger();

    @Override
    public void listEvents(Context ctx, ListEventsRequest request, Observer<ListEventsResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);

        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        Long pos = request.hasPos() ? request.getPos() : null;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");
        String severity = request.hasSeverity() ? request.getSeverity().toUpperCase() : INFO;

        if (pos != null) {
            log.warn("DEPRECATION WARNING: Do not use pos, use continuationToken instead");
        }

        EventPageToken nextToken = null;
        if (request.hasNext()) {
            nextToken = EventPageToken.decode(request.getNext());
        }

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual(GENTIME_COLUMN, request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore(GENTIME_COLUMN, request.getStop());
        }

        if (request.getSourceCount() > 0) {
            sqlb.whereColIn(SOURCE_COLUMN, request.getSourceList());
        }

        if (request.hasQ()) {
            sqlb.where("body.message like ?", "%" + request.getQ() + "%");
        }
        if (nextToken != null) {
            // TODO this currently ignores the source column (also part of the key)
            if (desc) {
                sqlb.where("(gentime < ? or (gentime = ? and seqNum < ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            } else {
                sqlb.where("(gentime > ? or (gentime = ? and seqNum > ?))",
                        nextToken.gentime, nextToken.gentime, nextToken.seqNum);
            }
        }
        addSeverityFilter(sqlb, severity);

        sqlb.descend(desc);

        if (pos != null) {
            sqlb.limit(pos, limit + 1l); // one more to detect hasMore
        }

        var filter = request.hasFilter()
                ? EventFilterFactory.create(request.getFilter())
                : null;

        var responseb = ListEventsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            Db.Event last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (filter != null && !filter.matches(tuple)) {
                    return;
                }

                if (++count <= limit) {
                    Db.Event incoming = (Db.Event) tuple.getColumn("body");
                    var event = fromDbEvent(incoming);
                    responseb.addEvents(event);
                    responseb.addEvent(event);
                    last = incoming;
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    var token = new EventPageToken(last.getGenerationTime(), last.getSource(),
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

        String instance = InstancesApi.verifyInstance(request.getInstance());

        if (!request.hasMessage()) {
            throw new BadRequestException("Message is required");
        }

        Db.Event.Builder eventb = Db.Event.newBuilder();
        eventb.setCreatedBy(ctx.user.getName());
        eventb.setMessage(request.getMessage());
        eventb.putAllExtra(request.getExtraMap());

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
            long eventTime = TimeEncoding.fromProtobufTimestamp(request.getTime());
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
            if (severity == EventSeverity.ERROR) {
                log.warn("DEPRECATION WARNING: Do not create events with ERROR level, "
                        + "this will be removed in a future release.");
            }
            eventb.setSeverity(severity);
        } else {
            eventb.setSeverity(EventSeverity.INFO);
        }

        EventProducer eventProducer = eventProducerMap.computeIfAbsent(instance, x -> {
            return EventProducerFactory.getEventProducer(x);
        });

        // Distribute event (without augmented fields, or they'll get stored)
        Db.Event event = eventb.build();
        log.debug("Adding event: {}", event);
        eventProducer.sendEvent(event);

        // Send back the event in response
        observer.complete(fromDbEvent(event));
    }

    @Override
    public void listEventSources(Context ctx, ListEventSourcesRequest request,
            Observer<ListEventSourcesResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListEventSourcesResponse.Builder responseb = ListEventSourcesResponse.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(EventRecorder.TABLE_NAME);
        BiMap<String, Short> enumValues = tableDefinition.getEnumValues(SOURCE_COLUMN);
        if (enumValues != null) {
            List<String> unsortedSources = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                unsortedSources.add(entry.getKey());
            }
            Collections.sort(unsortedSources, String.CASE_INSENSITIVE_ORDER);
            responseb.addAllSources(unsortedSources);
            responseb.addAllSource(unsortedSources);
        }
        observer.complete(responseb.build());
    }

    @Override
    public Observer<SubscribeEventsRequest> subscribeEvents(Context ctx, Observer<Event> observer) {
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);
        var clientObserver = new SubscribeEventsObserver(observer);
        observer.setCancelHandler(() -> clientObserver.complete());
        return clientObserver;
    }

    @Override
    public void streamEvents(Context ctx, StreamEventsRequest request, Observer<Event> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);
        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual(GENTIME_COLUMN, request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore(GENTIME_COLUMN, request.getStop());
        }

        if (request.getSourceCount() > 0) {
            sqlb.whereColIn(SOURCE_COLUMN, request.getSourceList());
        }

        String severity = request.hasSeverity() ? request.getSeverity().toUpperCase() : INFO;
        addSeverityFilter(sqlb, severity);

        if (request.hasQ()) {
            sqlb.where("body.message like ?", "%" + request.getQ() + "%");
        }

        var filter = request.hasFilter()
                ? EventFilterFactory.create(request.getFilter())
                : null;

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (filter != null && !filter.matches(tuple)) {
                    return;
                }

                Db.Event incoming = (Db.Event) tuple.getColumn("body");
                Event event = fromDbEvent(incoming);
                observer.next(event);
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    @Override
    public void exportEvents(Context ctx, ExportEventsRequest request, Observer<HttpBody> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        EventsApi.verifyEventArchiveSupport(instance);
        ctx.checkSystemPrivilege(SystemPrivilege.ReadEvents);

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual(GENTIME_COLUMN, request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore(GENTIME_COLUMN, request.getStop());
        }

        if (request.getSourceCount() > 0) {
            sqlb.whereColIn(SOURCE_COLUMN, request.getSourceList());
        }

        String severity = INFO;
        if (request.hasSeverity()) {
            severity = request.getSeverity().toUpperCase();
        }

        addSeverityFilter(sqlb, severity);

        if (request.hasQ()) {
            sqlb.where("body.message like ?", "%" + request.getQ() + "%");
        }

        String sql = sqlb.toString();

        var filter = request.hasFilter()
                ? EventFilterFactory.create(request.getFilter())
                : null;

        char delimiter = '\t';
        if (request.hasDelimiter()) {
            switch (request.getDelimiter()) {
            case "TAB":
                delimiter = '\t';
                break;
            case "SEMICOLON":
                delimiter = ';';
                break;
            case "COMMA":
                delimiter = ',';
                break;
            default:
                throw new BadRequestException("Unexpected column delimiter");
            }
        }

        CsvEventStreamer streamer = new CsvEventStreamer(observer, filter, delimiter);
        StreamFactory.stream(instance, sql, sqlb.getQueryArguments(), streamer);
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
        EventFilter filter;
        char columnDelimiter;

        CsvEventStreamer(Observer<HttpBody> observer, EventFilter filter, char columnDelimiter) {
            this.observer = observer;
            this.filter = filter;
            this.columnDelimiter = columnDelimiter;

            String[] rec = new String[5];
            int i = 0;
            rec[i++] = "Source";
            rec[i++] = "Generation Time";
            rec[i++] = "Reception Time";
            rec[i++] = "Event Type";
            rec[i++] = "Event Text";

            String dateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String filename = "event_export_" + dateString + ".csv";

            HttpBody metadata = HttpBody.newBuilder()
                    .setContentType(MediaType.CSV.toString())
                    .setFilename(filename)
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

            if (filter != null && !filter.matches(tuple)) {
                return;
            }

            Db.Event incoming = (Db.Event) tuple.getColumn("body");
            Event event = fromDbEvent(incoming);

            String[] rec = new String[5];
            int i = 0;
            rec[i++] = event.getSource();
            rec[i++] = Timestamps.toString(event.getGenerationTime());
            rec[i++] = Timestamps.toString(event.getReceptionTime());
            rec[i++] = event.getType();
            rec[i++] = event.getMessage();

            HttpBody body = HttpBody.newBuilder()
                    .setData(toByteString(rec))
                    .build();
            observer.next(body);
        }

        private ByteString toByteString(String[] rec) {
            ByteString.Output bout = ByteString.newOutput();
            CsvWriter writer = new CsvWriter(bout, columnDelimiter, StandardCharsets.UTF_8);
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

    private void addSeverityFilter(SqlBuilder sqlb, String severity) {
        switch (severity) {
        case INFO:
            break;
        case WATCH:
            sqlb.where("body.severity != 'INFO'");
            break;
        case WARNING:
            sqlb.whereColIn("body.severity", Arrays.asList(WARNING, DISTRESS, CRITICAL, SEVERE, ERROR));
            break;
        case DISTRESS:
            sqlb.whereColIn("body.severity", Arrays.asList(DISTRESS, CRITICAL, SEVERE, ERROR));
            break;
        case CRITICAL:
            sqlb.whereColIn("body.severity", Arrays.asList(CRITICAL, SEVERE, ERROR));
            break;
        case SEVERE:
            sqlb.whereColIn("body.severity", Arrays.asList(SEVERE, ERROR));
            break;
        default:
            sqlb.whereColIn("body.severity", Arrays.asList(severity));
        }
    }

    public static Event fromDbEvent(Db.Event other) {
        Event.Builder evb = Event.newBuilder()
                .putAllExtra(other.getExtraMap());
        if (other.hasSource()) {
            evb.setSource(other.getSource());
        }
        if (other.hasGenerationTime()) {
            evb.setGenerationTime(TimeEncoding.toProtobufTimestamp(other.getGenerationTime()));
        }
        if (other.hasReceptionTime()) {
            evb.setReceptionTime(TimeEncoding.toProtobufTimestamp(other.getReceptionTime()));
        }
        if (other.hasSeqNumber()) {
            evb.setSeqNumber(other.getSeqNumber());
        }
        if (other.hasType()) {
            evb.setType(other.getType());
        }
        if (other.hasMessage()) {
            evb.setMessage(other.getMessage());
        }
        if (other.hasSeverity()) {
            if (other.getSeverity() == EventSeverity.ERROR) {
                evb.setSeverity(EventSeverity.SEVERE);
            } else if (other.getSeverity() == EventSeverity.WARNING_NEW) {
                // Temporary during WARNING -> WARNING_NEW migration
                evb.setSeverity(EventSeverity.WARNING);
            } else {
                evb.setSeverity(other.getSeverity());
            }
        }
        if (other.hasCreatedBy()) {
            evb.setCreatedBy(other.getCreatedBy());
        }
        return evb.build();
    }
}
