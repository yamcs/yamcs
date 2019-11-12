package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.api.Observer;
import org.yamcs.archive.AlarmRecorder;
import org.yamcs.archive.EventRecorder;
import org.yamcs.archive.GPBHelper;
import org.yamcs.archive.IndexRequestListener;
import org.yamcs.archive.IndexServer;
import org.yamcs.archive.ParameterRecorder;
import org.yamcs.archive.XtceTmRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HttpException;
import org.yamcs.http.HttpServer;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.NotFoundException;
import org.yamcs.http.ProtobufRegistry;
import org.yamcs.http.api.RestRequest.IntervalResult;
import org.yamcs.http.api.archive.ArchiveHelper;
import org.yamcs.http.api.archive.EventPageToken;
import org.yamcs.http.api.archive.PacketPageToken;
import org.yamcs.http.api.archive.RestDownsampler;
import org.yamcs.http.api.archive.RestDownsampler.Sample;
import org.yamcs.http.api.archive.RestReplays;
import org.yamcs.logging.Log;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractStreamArchiveApi;
import org.yamcs.protobuf.Alarms.AlarmData;
import org.yamcs.protobuf.Archive.CreateEventRequest;
import org.yamcs.protobuf.Archive.GetPacketRequest;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.IndexEntry;
import org.yamcs.protobuf.Archive.IndexGroup;
import org.yamcs.protobuf.Archive.IndexResponse;
import org.yamcs.protobuf.Archive.ListAlarmsRequest;
import org.yamcs.protobuf.Archive.ListAlarmsResponse;
import org.yamcs.protobuf.Archive.ListCommandHistoryIndexRequest;
import org.yamcs.protobuf.Archive.ListCompletenessIndexRequest;
import org.yamcs.protobuf.Archive.ListEventIndexRequest;
import org.yamcs.protobuf.Archive.ListEventSourcesRequest;
import org.yamcs.protobuf.Archive.ListEventSourcesResponse;
import org.yamcs.protobuf.Archive.ListEventsRequest;
import org.yamcs.protobuf.Archive.ListEventsResponse;
import org.yamcs.protobuf.Archive.ListPacketIndexRequest;
import org.yamcs.protobuf.Archive.ListPacketNamesRequest;
import org.yamcs.protobuf.Archive.ListPacketNamesResponse;
import org.yamcs.protobuf.Archive.ListPacketsRequest;
import org.yamcs.protobuf.Archive.ListPacketsResponse;
import org.yamcs.protobuf.Archive.ListParameterAlarmsRequest;
import org.yamcs.protobuf.Archive.ListParameterGroupsRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.Archive.ListParameterIndexRequest;
import org.yamcs.protobuf.Archive.ParameterGroupInfo;
import org.yamcs.protobuf.Archive.StreamParameterValuesRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Yamcs.ArchiveRecord;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.protobuf.Yamcs.IndexRequest;
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

    private ConcurrentMap<String, EventProducer> eventProducerMap = new ConcurrentHashMap<>();
    private AtomicInteger eventSequenceNumber = new AtomicInteger();
    private ProtobufRegistry protobufRegistry;

    @Override
    public void listEvents(Context ctx, ListEventsRequest request, Observer<ListEventsResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);

        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ReadEvents);

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");
        String severity = request.hasSeverity() ? request.getSeverity().toUpperCase() : "INFO";

        Set<String> sourceSet = new HashSet<>(request.getSourceList());

        PacketPageToken nextToken = null;
        if (request.hasNext()) {
            nextToken = PacketPageToken.decode(request.getNext());
        }

        SqlBuilder sqlb = new SqlBuilder(EventRecorder.TABLE_NAME);

        if (request.hasStart() || request.hasStop()) {
            long start = request.hasStart() ? TimeEncoding.fromProtobufTimestamp(request.getStart())
                    : TimeEncoding.INVALID_INSTANT;
            long stop = request.hasStop() ? TimeEncoding.fromProtobufTimestamp(request.getStop())
                    : TimeEncoding.INVALID_INSTANT;
            IntervalResult ir = new IntervalResult(start, stop);
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
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

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
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.WriteEvents);

        String instance = RestHandler.verifyInstance(request.getInstance());

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
        String instance = RestHandler.verifyInstance(request.getInstance());
        verifyEventArchiveSupport(instance);
        RestHandler.checkSystemPrivilege(ctx.user, SystemPrivilege.ReadEvents);

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
    public void listAlarms(Context ctx, ListAlarmsRequest request, Observer<ListAlarmsResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean ascending = request.getOrder().equals("asc");

        SqlBuilder sqlbParam = new SqlBuilder(AlarmRecorder.PARAMETER_ALARM_TABLE_NAME);
        SqlBuilder sqlbEvent = new SqlBuilder(AlarmRecorder.EVENT_ALARM_TABLE_NAME);

        if (request.hasStart() || request.hasStop()) {
            long start = request.hasStart() ? TimeEncoding.fromProtobufTimestamp(request.getStart())
                    : TimeEncoding.INVALID_INSTANT;
            long stop = request.hasStop() ? TimeEncoding.fromProtobufTimestamp(request.getStop())
                    : TimeEncoding.INVALID_INSTANT;
            IntervalResult ir = new IntervalResult(start, stop);
            sqlbParam.where(ir.asSqlCondition("triggerTime"));
            sqlbEvent.where(ir.asSqlCondition("triggerTime"));
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
        RestStreams.stream(instance, q, sqlbParam.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple, true);
                responseb.addAlarm(alarm);
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
        String instance = RestHandler.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean ascending = request.getOrder().equals("asc");

        SqlBuilder sqlb = new SqlBuilder(AlarmRecorder.PARAMETER_ALARM_TABLE_NAME);

        if (request.hasStart() || request.hasStop()) {
            long start = request.hasStart() ? TimeEncoding.fromProtobufTimestamp(request.getStart())
                    : TimeEncoding.INVALID_INSTANT;
            long stop = request.hasStop() ? TimeEncoding.fromProtobufTimestamp(request.getStop())
                    : TimeEncoding.INVALID_INSTANT;
            IntervalResult ir = new IntervalResult(start, stop);
            sqlb.where(ir.asSqlCondition("triggerTime"));
        }

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = RestHandler.verifyParameter(ctx.user, mdb, request.getParameter());
        sqlb.where("parameter = ?", p.getQualifiedName());

        /*
         * if (req.hasRouteParam("triggerTime")) { sqlb.where("triggerTime = " + req.getDateRouteParam("triggerTime"));
         * }
         */
        sqlb.descend(!ascending);
        sqlb.limit(pos, limit);
        ListAlarmsResponse.Builder responseb = ListAlarmsResponse.newBuilder();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                AlarmData alarm = ArchiveHelper.tupleToAlarmData(tuple, request.getDetail());
                responseb.addAlarm(alarm);
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
        String instance = RestHandler.verifyInstance(request.getInstance());
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
        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        String pathName = request.getName();

        ParameterWithId p = RestHandler.verifyParameterWithId(ctx.user, mdb, pathName);

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
        RestParameterReplayListener replayListener = new RestParameterReplayListener(pos, limit) {
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

        RestReplays.replay(instance, ctx.user, rr, replayListener);
    }

    @Override
    public void getParameterSamples(Context ctx, GetParameterSamplesRequest request,
            Observer<TimeSeries> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());

        XtceDb mdb = XtceDbFactory.getInstance(instance);
        Parameter p = RestHandler.verifyParameter(ctx.user, mdb, request.getName());

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

        RestDownsampler sampler = new RestDownsampler(start, stop, sampleCount);

        RestReplayListener replayListener = new RestReplayListener() {
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

        RestReplays.replay(instance, ctx.user, rr.build(), replayListener);
    }

    @Override
    public void listCommandHistoryIndex(Context ctx, ListCommandHistoryIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getNameCount() > 0) {
            for (String name : request.getNameList()) {
                requestb.addCmdName(NamedObjectId.newBuilder().setName(name.trim()));
            }
        } else {
            requestb.setSendAllCmd(true);
        }

        handleOneIndexResult(observer, indexServer, requestb.build(), limit, next);
    }

    @Override
    public void listEventIndex(Context ctx, ListEventIndexRequest request, Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getSourceCount() > 0) {
            for (String source : request.getSourceList()) {
                requestb.addEventSource(NamedObjectId.newBuilder().setName(source.trim()));
            }
        } else {
            requestb.setSendAllEvent(true);
        }

        handleOneIndexResult(observer, indexServer, requestb.build(), limit, next);
    }

    @Override
    public void listPacketIndex(Context ctx, ListPacketIndexRequest request, Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 2000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getNameCount() > 0) {
            for (String name : request.getNameList()) {
                requestb.addTmPacket(NamedObjectId.newBuilder().setName(name.trim()));
            }
        } else {
            requestb.setSendAllTm(true);
        }

        handleOneIndexResult(observer, indexServer, requestb.build(), limit, next);
    }

    @Override
    public void listParameterIndex(Context ctx, ListParameterIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);

        int mergeTime = request.hasMergeTime() ? request.getMergeTime() : 20000;
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setInstance(instance);
        requestb.setMergeTime(mergeTime);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        if (request.getGroupCount() > 0) {
            for (String group : request.getGroupList()) {
                requestb.addPpGroup(NamedObjectId.newBuilder().setName(group.trim()));
            }
        } else {
            requestb.setSendAllPp(true);
        }

        handleOneIndexResult(observer, indexServer, requestb.build(), limit, next);
    }

    @Override
    public void listCompletenessIndex(Context ctx, ListCompletenessIndexRequest request,
            Observer<IndexResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
        IndexServer indexServer = verifyIndexServer(instance);
        int limit = request.hasLimit() ? request.getLimit() : 500;

        IndexRequest.Builder requestb = IndexRequest.newBuilder();
        requestb.setSendCompletenessIndex(true);
        requestb.setInstance(instance);

        if (request.hasStart()) {
            long start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            requestb.setStart(start);
        }
        if (request.hasStop()) {
            long stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            requestb.setStop(stop);
        }
        String next = request.hasNext() ? request.getNext() : null;

        handleOneIndexResult(observer, indexServer, requestb.build(), limit, next);
    }

    @Override
    public void listPacketNames(Context ctx, ListPacketNamesRequest request,
            Observer<ListPacketNamesResponse> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());
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
                if (RestHandler.hasObjectPrivilege(ctx.user, ObjectPrivilegeType.ReadPacket, packetName)) {
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
        String instance = RestHandler.verifyInstance(request.getInstance());

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean desc = !request.getOrder().equals("asc");

        RestHandler.checkObjectPrivileges(ctx.user, ObjectPrivilegeType.ReadPacket, request.getNameList());
        Set<String> nameSet = new HashSet<>(request.getNameList());
        if (nameSet.isEmpty()) {
            nameSet.addAll(getTmPacketNames(instance, ctx.user));
        }

        PacketPageToken nextToken = null;
        if (request.hasNext()) {
            String next = request.getNext();
            nextToken = PacketPageToken.decode(next);
        }

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME);

        long start = request.hasStart() ? TimeEncoding.fromProtobufTimestamp(request.getStart())
                : TimeEncoding.INVALID_INSTANT;
        long stop = request.hasStop() ? TimeEncoding.fromProtobufTimestamp(request.getStop())
                : TimeEncoding.INVALID_INSTANT;

        IntervalResult ir = new IntervalResult(start, stop);

        // Query optimization to skip previously outputted pages at the Rocks level.
        // (because the gentime/seqnum condition used further down is unoptimized)
        if (nextToken != null) {
            if (desc) {
                ir.setStop(nextToken.gentime, true);
            } else {
                ir.setStart(nextToken.gentime, true);
            }
        }
        if (ir.hasInterval()) {
            sqlb.where(ir.asSqlCondition("gentime"));
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
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {

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
        String instance = RestHandler.verifyInstance(request.getInstance());
        long gentime = request.getGentime();
        int seqNum = request.getSeqnum();

        SqlBuilder sqlb = new SqlBuilder(XtceTmRecorder.TABLE_NAME)
                .where("gentime = ?", gentime)
                .where("seqNum = ?", seqNum);

        List<TmPacketData> packets = new ArrayList<>();
        RestStreams.stream(instance, sqlb.toString(), sqlb.getQueryArguments(), new StreamSubscriber() {
            @Override
            public void onTuple(Stream stream, Tuple tuple) {
                TmPacketData pdata = GPBHelper.tupleToTmPacketData(tuple);
                if (RestHandler.hasObjectPrivilege(ctx.user, ObjectPrivilegeType.ReadPacket, pdata.getId().getName())) {
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
    public void streamParameterValues(Context ctx, StreamParameterValuesRequest request,
            Observer<ParameterData> observer) {
        String instance = RestHandler.verifyInstance(request.getInstance());

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
            RestHandler.checkObjectPrivileges(ctx.user, ObjectPrivilegeType.ReadParameter, p.getQualifiedName());
            ids.add(id);
        }
        if (request.hasNamespace()) {
            namespace = request.getNamespace();
        }

        if (ids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (!RestHandler.hasObjectPrivilege(ctx.user, ObjectPrivilegeType.ReadParameter,
                        p.getQualifiedName())) {
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

        RestReplayListener replayListener = new RestParameterReplayListener() {

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

        RestReplays.replay(instance, ctx.user, rr.build(), replayListener);
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

    private IndexServer verifyIndexServer(String instance) throws HttpException {
        YamcsServer yamcs = YamcsServer.getServer();
        RestHandler.verifyInstance(instance);
        List<IndexServer> services = yamcs.getServices(instance, IndexServer.class);
        if (services.isEmpty()) {
            throw new BadRequestException("Index service not enabled for instance '" + instance + "'");
        } else {
            return services.get(0);
        }
    }

    /**
     * Submits an index request but returns only the first batch of results combined with a pagination token if the user
     * wishes to retrieve the next batch.
     * 
     * The batch size is determined by the IndexServer and is set to 500 (shared between all requested groups).
     */
    private void handleOneIndexResult(Observer<IndexResponse> observer, IndexServer indexServer,
            IndexRequest request, int limit, String token) throws HttpException {
        try {
            IndexResponse.Builder responseb = IndexResponse.newBuilder();
            Map<NamedObjectId, IndexGroup.Builder> groupBuilders = new HashMap<>();
            indexServer.submitIndexRequest(request, limit, token, new IndexRequestListener() {

                long last;

                @Override
                public void processData(ArchiveRecord rec) {
                    IndexGroup.Builder groupb = groupBuilders.get(rec.getId());
                    if (groupb == null) {
                        groupb = IndexGroup.newBuilder().setId(rec.getId());
                        groupBuilders.put(rec.getId(), groupb);
                    }
                    long first = TimeEncoding.fromProtobufTimestamp(rec.getFirst());
                    long last1 = TimeEncoding.fromProtobufTimestamp(rec.getLast());

                    IndexEntry.Builder ieb = IndexEntry.newBuilder()
                            .setStart(TimeEncoding.toString(first))
                            .setStop(TimeEncoding.toString(last1))
                            .setCount(rec.getNum());
                    if (rec.hasSeqFirst()) {
                        ieb.setSeqStart(rec.getSeqFirst());
                    }
                    if (rec.hasSeqLast()) {
                        ieb.setSeqStop(rec.getSeqLast());
                    }
                    groupb.addEntry(ieb);
                    last = Math.max(last, last1);
                }

                @Override
                public void finished(String token, boolean success) {
                    if (success) {
                        if (token != null) {
                            responseb.setContinuationToken(token);
                        }
                        List<IndexGroup.Builder> sortedGroups = new ArrayList<>(groupBuilders.values());
                        Collections.sort(sortedGroups, (g1, g2) -> {
                            return g1.getId().getName().compareTo(g2.getId().getName());
                        });
                        sortedGroups.forEach(groupb -> responseb.addGroup(groupb));
                        observer.complete(responseb.build());
                    } else {
                        observer.completeExceptionally(new InternalServerErrorException("Too many results"));
                    }
                }
            });
        } catch (YamcsException e) {
            throw new InternalServerErrorException("Too many results", e);
        }
    }
}
