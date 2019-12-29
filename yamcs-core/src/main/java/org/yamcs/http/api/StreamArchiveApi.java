package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.Observer;
import org.yamcs.archive.AlarmRecorder;
import org.yamcs.archive.CommandHistoryRecorder;
import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.ParameterRecorder;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpServer;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractStreamArchiveApi;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.CreateEventRequest;
import org.yamcs.protobuf.Archive.GetPacketRequest;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListAlarmsRequest;
import org.yamcs.protobuf.Archive.ListAlarmsResponse;
import org.yamcs.protobuf.Archive.ListEventSourcesRequest;
import org.yamcs.protobuf.Archive.ListEventSourcesResponse;
import org.yamcs.protobuf.Archive.ListEventsRequest;
import org.yamcs.protobuf.Archive.ListEventsResponse;
import org.yamcs.protobuf.Archive.ListPacketNamesRequest;
import org.yamcs.protobuf.Archive.ListPacketNamesResponse;
import org.yamcs.protobuf.Archive.ListPacketsRequest;
import org.yamcs.protobuf.Archive.ListPacketsResponse;
import org.yamcs.protobuf.Archive.ListParameterAlarmsRequest;
import org.yamcs.protobuf.Archive.ListParameterGroupsRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.Archive.ParameterGroupInfo;
import org.yamcs.protobuf.Archive.StreamCommandsRequest;
import org.yamcs.protobuf.Archive.StreamEventsRequest;
import org.yamcs.protobuf.Archive.StreamPacketsRequest;
import org.yamcs.protobuf.Archive.StreamParameterValuesRequest;
import org.yamcs.protobuf.Commanding.CommandHistoryEntry;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Mdb.GetCommandRequest;
import org.yamcs.protobuf.Mdb.ListCommandsRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.ReplaySpeed;
import org.yamcs.protobuf.Yamcs.ReplaySpeed.ReplaySpeedType;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;
import com.google.protobuf.InvalidProtocolBufferException;

public class StreamArchiveApi extends AbstractStreamArchiveApi<Context> {

    private static final Log log = new Log(StreamArchiveApi.class);
    private static final Pattern PATTERN_COMMAND_ID = Pattern.compile("([0-9]+)(-(.*))?-([0-9]+)");

    private ConcurrentMap<String, EventProducer> eventProducerMap = new ConcurrentHashMap<>();
    private AtomicInteger eventSequenceNumber = new AtomicInteger();
    private ProtobufRegistry protobufRegistry;

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
    public void streamEvents(Context ctx, StreamEventsRequest request, Observer<Event> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        StreamArchiveApi.verifyEventArchiveSupport(instance);
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
    public void listAlarms(Context ctx, ListAlarmsRequest request, Observer<ListAlarmsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean ascending = request.getOrder().equals("asc");

        SqlBuilder sqlbParam = new SqlBuilder(AlarmRecorder.PARAMETER_ALARM_TABLE_NAME);
        SqlBuilder sqlbEvent = new SqlBuilder(AlarmRecorder.EVENT_ALARM_TABLE_NAME);

        if (request.hasStart()) {
            sqlbParam.whereColAfterOrEqual("triggerTime", request.getStart());
            sqlbEvent.whereColAfterOrEqual("triggerTime", request.getStart());
        }
        if (request.hasStop()) {
            sqlbParam.whereColBefore("triggerTime", request.getStop());
            sqlbEvent.whereColBefore("triggerTime", request.getStop());
        }

        /*
         * if (req.hasRouteParam("triggerTime")) { sqlb.where("triggerTime = " + req.getDateRouteParam("triggerTime"));
         * }
         */
        sqlbParam.descend(!ascending);
        sqlbEvent.descend(!ascending);
        sqlbParam.limit(pos, limit);
        sqlbEvent.limit(pos, limit);

        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        String q = "MERGE (" + sqlbParam.toString() + "), (" + sqlbEvent.toString() + ") USING triggerTime ORDER DESC";
        StreamFactory.stream(instance, q, sqlbParam.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple, true);
                responseb.addAlarms(alarm);
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void listParameterAlarms(Context ctx, ListParameterAlarmsRequest request,
            Observer<ListAlarmsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean ascending = request.getOrder().equals("asc");

        SqlBuilder sqlb = new SqlBuilder(AlarmRecorder.PARAMETER_ALARM_TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("triggerTime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("triggerTime", request.getStop());
        }

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = MdbApi.verifyParameter(ctx, mdb, request.getParameter());
        sqlb.where("parameter = ?", p.getQualifiedName());

        /*
         * if (req.hasRouteParam("triggerTime")) { sqlb.where("triggerTime = " + req.getDateRouteParam("triggerTime"));
         * }
         */
        sqlb.descend(!ascending);
        sqlb.limit(pos, limit);
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple, request.getDetail());
                responseb.addAlarms(alarm);
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void listParameterGroups(Context ctx, ListParameterGroupsRequest request,
            Observer<ParameterGroupInfo> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ParameterGroupInfo.Builder responseb = ParameterGroupInfo.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(ParameterRecorder.TABLE_NAME);
        BiMap<String, Short> enumValues = tableDefinition.getEnumValues("group");
        if (enumValues != null) {
            List<String> unsortedGroups = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                unsortedGroups.add(entry.getKey());
            }
            Collections.sort(unsortedGroups);
            responseb.addAllGroup(unsortedGroups);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listParameterHistory(Context ctx, ListParameterHistoryRequest request,
            Observer<ListParameterHistoryResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        String pathName = request.getName();

        ParameterWithId p = MdbApi.verifyParameterWithId(ctx, mdb, pathName);

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean noRepeat = request.getNorepeat();
        boolean descending = !request.getOrder().equals("asc");

        long start = TimeEncoding.INVALID_INSTANT;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }

        long stop = TimeEncoding.INVALID_INSTANT;
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        ReplayRequest rr = ArchiveHelper.toParameterReplayRequest(p.getId(), start, stop, descending);

        ListParameterHistoryResponse.Builder resultb = ListParameterHistoryResponse.newBuilder();
        ParameterReplayListener replayListener = new ParameterReplayListener(pos, limit) {
            @Override
            public void onParameterData(List<ParameterValueWithId> params) {
                for (ParameterValueWithId pvalid : params) {
                    resultb.addParameter(pvalid.toGbpParameterValue());
                }
            }

            @Override
            public void replayFailed(Throwable t) {
                observer.completeExceptionally(t);
            }

            @Override
            public void replayFinished() {
                observer.complete(resultb.build());
            }
        };
        replayListener.setNoRepeat(noRepeat);

        ReplayFactory.replay(instance, ctx.user, rr, replayListener);
    }

    @Override
    public void getParameterSamples(Context ctx, GetParameterSamplesRequest request,
            Observer<TimeSeries> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = MdbApi.verifyParameter(ctx, mdb, request.getName());

        ParameterType ptype = p.getParameterType();
        if ((ptype != null) && (!(ptype instanceof FloatParameterType) && !(ptype instanceof IntegerParameterType))) {
            throw new BadRequestException(
                    "Only integer or float parameters can be sampled. Got " + ptype.getTypeAsString());
        }

        ReplayRequest.Builder rr = ReplayRequest.newBuilder().setEndAction(EndAction.QUIT);
        rr.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));
        NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
        rr.setParameterRequest(ParameterReplayRequest.newBuilder().addNameFilter(id));

        long stop = TimeEncoding.getWallclockTime();
        long start = stop - (1000 * 60 * 60); // 1 hour

        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        rr.setStart(start);
        rr.setStop(stop);

        int sampleCount = request.hasCount() ? request.getCount() : 500;

        Downsampler sampler = new Downsampler(start, stop, sampleCount);

        ParameterReplayListener replayListener = new ParameterReplayListener() {
            @Override
            public void onParameterData(List<ParameterValueWithId> params) {
                for (ParameterValueWithId pvalid : params) {
                    sampler.process(pvalid.getParameterValue());
                }
            }

            @Override
            public void replayFinished() {
                TimeSeries.Builder series = TimeSeries.newBuilder();
                for (Sample s : sampler.collect()) {
                    series.addSample(ArchiveHelper.toGPBSample(s));
                }
                observer.complete(series.build());
            }

            @Override
            public void replayFailed(Throwable t) {
                observer.completeExceptionally(t);
            }
        };

        ReplayFactory.replay(instance, ctx.user, rr.build(), replayListener);
    }

    @Override
    public void listPacketNames(Context ctx, ListPacketNamesRequest request,
            Observer<ListPacketNamesResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        ListPacketNamesResponse.Builder responseb = ListPacketNamesResponse.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(XtceTmRecorder.TABLE_NAME);
        if (tableDefinition == null) {
            observer.complete(responseb.build());
            return;
        }

        BiMap<String, Short> enumValues = tableDefinition.getEnumValues(XtceTmRecorder.PNAME_COLUMN);
        if (enumValues != null) {
            List<String> unsortedPackets = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                String packetName = entry.getKey();
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, packetName)) {
                    unsortedPackets.add(packetName);
                }
            }
            Collections.sort(unsortedPackets);
            responseb.addAllName(unsortedPackets);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listPackets(Context ctx, ListPacketsRequest request, Observer<ListPacketsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, request.getNameList());
        Set<String> nameSet = new HashSet<>(request.getNameList());
        if (nameSet.isEmpty()) {
            for (String packetName : getTmPacketNames(instance, ctx.user)) {
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, packetName)) {
                    nameSet.add(packetName);
                }
            }
        }
        if (nameSet.isEmpty()) {
            // No permissions for any packet
            observer.complete(ListPacketsResponse.getDefaultInstance());
            return;
        }

        PacketPageToken nextToken = null;
        if (request.hasNext()) {
            String next = request.getNext();
            nextToken = PacketPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (!nameSet.isEmpty()) {
            sqlb.whereColIn("pname", nameSet);
        }
        if (nextToken != null) {
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

        ListPacketsResponse.Builder responseb = ListPacketsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            TmPacketData last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (++count <= limit) {
                    TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                    responseb.addPacket(pdata);
                    last = pdata;
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    PacketPageToken token = new PacketPageToken(
                            TimeEncoding.fromProtobufTimestamp(last.getGenerationTime()),
                            last.getSequenceNumber());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getPacket(Context ctx, GetPacketRequest request, Observer<TmPacketData> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());
        long gentime = request.getGentime();
        int seqNum = request.getSeqnum();

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME)
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum);

        List<TmPacketData> packets = new ArrayList<>();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, pdata.getId().getName())) {
                    packets.add(pdata);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (packets.isEmpty()) {
                    observer.completeExceptionally(
                            new NotFoundException("No packet for id (" + gentime + ", " + seqNum + ")"));
                } else if (packets.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    observer.complete(packets.get(0));
                }
            }
        });
    }

    @Override
    public void streamPackets(Context ctx, StreamPacketsRequest request, Observer<TmPacketData> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadPacket, request.getNameList());

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getNameCount() > 0) {
            sqlb.whereColIn("pname", request.getNameList());
        }

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                if (ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, pdata.getId().getName())) {
                    observer.next(pdata);
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    @Override
    public void streamParameterValues(Context ctx, StreamParameterValuesRequest request,
            Observer<ParameterData> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        ReplayRequest.Builder rr = ReplayRequest.newBuilder().setEndAction(EndAction.QUIT);
        rr.setSpeed(ReplaySpeed.newBuilder().setType(ReplaySpeedType.AFAP));

        List<NamedObjectId> ids = new ArrayList<>();
        XtceDb mdb = XtceDbFactory.getInstance(instance);
        String namespace = null;

        if (request.hasStart()) {
            rr.setStart(TimeEncoding.fromProtobufTimestamp(request.getStart()));
        }
        if (request.hasStop()) {
            rr.setStop(TimeEncoding.fromProtobufTimestamp(request.getStop()));
        }
        for (NamedObjectId id : request.getIdsList()) {
            Parameter p = mdb.getParameter(id);
            if (p == null) {
                throw new BadRequestException("Invalid parameter name specified " + id);
            }
            ctx.checkObjectPrivileges(ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
            ids.add(id);
        }
        if (request.hasNamespace()) {
            namespace = request.getNamespace();
        }

        if (ids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (!ctx.user.hasObjectPrivilege(ObjectPrivilegeType.ReadParameter, p.getQualifiedName())) {
                    continue;
                }
                if (namespace != null) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        ids.add(NamedObjectId.newBuilder().setNamespace(namespace).setName(alias).build());
                    }
                } else {
                    ids.add(NamedObjectId.newBuilder().setName(p.getQualifiedName()).build());
                }
            }
        }
        rr.setParameterRequest(ParameterReplayRequest.newBuilder().addAllNameFilter(ids));

        ParameterReplayListener replayListener = new ParameterReplayListener() {

            @Override
            protected void onParameterData(List<ParameterValueWithId> params) {
                ParameterData.Builder pd = ParameterData.newBuilder();
                for (ParameterValueWithId pvalid : params) {
                    ParameterValue pval = pvalid.toGbpParameterValue();
                    pd.addParameter(pval);
                }
                observer.next(pd.build());
            }

            @Override
            public void replayFinished() {
                observer.complete();
            }

            @Override
            public void replayFailed(Throwable t) {
                observer.completeExceptionally(t);
            }
        };
        observer.setCancelHandler(replayListener::requestReplayAbortion);

        ReplayFactory.replay(instance, ctx.user, rr.build(), replayListener);
    }

    @Override
    public void listCommands(Context ctx, ListCommandsRequest request, Observer<ListCommandsResponse> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);
        if (ydb.getTable(CommandHistoryRecorder.TABLE_NAME) == null) {
            observer.complete(ListCommandsResponse.getDefaultInstance());
            return;
        }

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        CommandPageToken nextToken = null;
        if (request.hasNext()) {
            String next = request.getNext();
            nextToken = CommandPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.hasQ()) {
            sqlb.where("cmdName like ?", "%" + request.getQ() + "%");
        }
        if (nextToken != null) {
            // TODO this currently ignores the origin column (also part of the key)
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
        sqlb.limit(pos, limit + 1); // one more to detect hasMore

        ListCommandsResponse.Builder responseb = ListCommandsResponse.newBuilder();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            CommandHistoryEntry last;
            int count;

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                if (++count <= limit) {
                    CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                    responseb.addEntry(che);
                    last = che;
                }
            }

            @Override
            public void streamClosed(Stream stream) {
                if (count > limit) {
                    CommandId cmdId = last.getCommandId();
                    CommandPageToken token = new CommandPageToken(
                            cmdId.getGenerationTime(), cmdId.getOrigin(),
                            cmdId.getSequenceNumber());
                    responseb.setContinuationToken(token.encodeAsString());
                }
                observer.complete(responseb.build());
            }
        });
    }

    @Override
    public void getCommand(Context ctx, GetCommandRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        Matcher matcher = PATTERN_COMMAND_ID.matcher(request.getId());
        if (!matcher.matches()) {
            throw new BadRequestException("Invalid command id");
        }

        long gentime = Long.parseLong(matcher.group(1));
        String origin = matcher.group(3) != null ? matcher.group(3) : "";
        int seqNum = Integer.parseInt(matcher.group(4));

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME)
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum)
                .where("origin = ?", origin);

        List<CommandHistoryEntry> commands = new ArrayList<>();
        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry che = GPBHelper.tupleToCommandHistoryEntry(tuple);
                commands.add(che);
            }

            @Override
            public void streamClosed(Stream stream) {
                if (commands.isEmpty()) {
                    observer.completeExceptionally(new NotFoundException());
                } else if (commands.size() > 1) {
                    observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                } else {
                    observer.complete(commands.get(0));
                }
            }
        });
    }

    @Override
    public void streamCommands(Context ctx, StreamCommandsRequest request, Observer<CommandHistoryEntry> observer) {
        String instance = ManagementApi.verifyInstance(request.getInstance());

        ctx.checkObjectPrivileges(ObjectPrivilegeType.CommandHistory, request.getNameList());

        SqlBuilder sqlb = new SqlBuilder(CommandHistoryRecorder.TABLE_NAME);

        if (request.hasStart()) {
            sqlb.whereColAfterOrEqual("gentime", request.getStart());
        }
        if (request.hasStop()) {
            sqlb.whereColBefore("gentime", request.getStop());
        }

        if (request.getNameCount() > 0) {
            sqlb.whereColIn("cmdName", request.getNameList());
        }

        StreamFactory.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                CommandHistoryEntry entry = GPBHelper.tupleToCommandHistoryEntry(tuple);
                observer.next(entry);
            }

            @Override
            public void streamClosed(Stream stream) {
                observer.complete();
            }
        });
    }

    /**
     * Get packet names this user has appropriate privileges for.
     */
    public Collection<String> getTmPacketNames(String yamcsInstance, User user) {
        XtceDb xtcedb = XtceDbFactory.getInstance(yamcsInstance);
        ArrayList<String> tl = new ArrayList<>();
        for (SequenceContainer sc : xtcedb.getSequenceContainers()) {
            if (user.hasObjectPrivilege(ObjectPrivilegeType.ReadPacket, sc.getQualifiedName())) {
                tl.add(sc.getQualifiedName());
            }
        }
        return tl;
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

    private ProtobufRegistry getProtobufRegistry() {
        YamcsServer yamcs = YamcsServer.getServer();
        if (protobufRegistry == null) {
            List<HttpServer> services = yamcs.getGlobalServices(HttpServer.class);
            protobufRegistry = services.get(0).getProtobufRegistry();
        }
        return protobufRegistry;
    }
}
