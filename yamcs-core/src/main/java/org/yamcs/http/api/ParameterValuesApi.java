package org.yamcs.http.api;

import static org.yamcs.http.api.ParameterArchiveApi.isReplayAsked;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.ParameterRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.MediaType;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedMultiParameterRetrievalConsumer;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedSingleParameterRetrievalConsumer;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.http.api.ParameterRanger.Range;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameter.ParameterRetrievalService;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractParameterValuesApi;
import org.yamcs.protobuf.ExportParameterValuesRequest;
import org.yamcs.protobuf.GetParameterRangesRequest;
import org.yamcs.protobuf.GetParameterSamplesRequest;
import org.yamcs.protobuf.ListParameterGroupsRequest;
import org.yamcs.protobuf.ListParameterGroupsResponse;
import org.yamcs.protobuf.ListParameterHistoryRequest;
import org.yamcs.protobuf.ListParameterHistoryResponse;
import org.yamcs.protobuf.LoadParameterValuesRequest;
import org.yamcs.protobuf.LoadParameterValuesResponse;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.Ranges;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.StreamParameterValuesRequest;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.tctm.StreamParameterSender;
import org.yamcs.time.TimeService;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.ParameterFormatter.Header;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Parameter;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;
import com.google.protobuf.ByteString;

public class ParameterValuesApi extends AbstractParameterValuesApi<Context> {

    private static final Log log = new Log(ParameterValuesApi.class);

    @Override
    public void listParameterHistory(Context ctx, ListParameterHistoryRequest request,
            Observer<ListParameterHistoryResponse> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(ysi.getName());
        ParameterWithId requestedParamWithId = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());

        int limit = request.hasLimit() ? request.getLimit() : 100;
        int maxBytes = request.hasMaxBytes() ? request.getMaxBytes() : -1;

        long start = 0;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = TimeEncoding.getWallclockTime();
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        if (start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        boolean ascending = request.getOrder().equals("asc");
        if (request.hasNext()) {
            TimeSortedPageToken token = TimeSortedPageToken.decode(request.getNext());
            if (ascending) {
                start = token.time;
            } else {
                stop = token.time;
            }
        }
        var optsb = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withAscending(ascending)
                .withRetrieveParameterStatus(false);

        if (request.hasSource() && isReplayAsked(request.getSource())) {
            optsb = optsb
                    .withoutParchive(true)
                    .withoutReplay(false);
        } else {
            if (request.hasNoreplay()) {
                optsb = optsb.withoutReplay(request.getNoreplay());
            }
            optsb = optsb.withoutRealtime(request.getNorealtime());
        }

        ParameterRetrievalOptions opts = optsb.build();
        ParameterRetrievalService prs = getParameterRetrievalService(ysi);

        ListParameterHistoryResponse.Builder resultb = ListParameterHistoryResponse.newBuilder();
        final int fLimit = limit + 1; // one extra to detect continuation token

        PaginatedSingleParameterRetrievalConsumer replayListener = new PaginatedSingleParameterRetrievalConsumer(0,
                fLimit) {
            @Override
            public void onParameterData(ParameterValueWithId pvwid) {
                if (resultb.getParameterCount() < fLimit - 1) {
                    resultb.addParameter(toGpb(pvwid, maxBytes));
                } else {
                    TimeSortedPageToken token = new TimeSortedPageToken(pvwid.getParameterValue().getGenerationTime());
                    resultb.setContinuationToken(token.encodeAsString());
                }
            }
        };

        replayListener.setNoRepeat(request.getNorepeat());
        prs.retrieveSingle(requestedParamWithId, opts, replayListener)
                .thenRun(() -> {
                    observer.complete(resultb.build());
                })
                .exceptionally(e -> {
                    log.warn("Received exception during parameter retrieval", e);
                    observer.completeExceptionally(new InternalServerErrorException(e.toString()));
                    return null;
                });
    }

    public static org.yamcs.protobuf.Pvalue.ParameterValue toGpb(ParameterValueWithId pvalWithId, int maxBytes) {
        var gpb = pvalWithId.toGbpParameterValue();
        if (maxBytes >= 0) {
            var hasRawBinaryValue = gpb.hasRawValue() && gpb.getRawValue().hasBinaryValue();
            var hasEngBinaryValue = gpb.hasEngValue() && gpb.getEngValue().hasBinaryValue();
            if (hasRawBinaryValue || hasEngBinaryValue) {
                var truncated = org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder(gpb);
                if (hasRawBinaryValue) {
                    var binaryValue = gpb.getRawValue().getBinaryValue();
                    if (binaryValue.size() > maxBytes) {
                        truncated.getRawValueBuilder().setBinaryValue(
                                binaryValue.substring(0, maxBytes));
                    }
                }
                if (hasEngBinaryValue) {
                    var binaryValue = gpb.getEngValue().getBinaryValue();
                    if (binaryValue.size() > maxBytes) {
                        truncated.getEngValueBuilder().setBinaryValue(
                                binaryValue.substring(0, maxBytes));
                    }
                }
                return truncated.build();
            }
        }
        return gpb;
    }

    @Override
    public void exportParameterValues(Context ctx, ExportParameterValuesRequest request, Observer<HttpBody> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        var ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        var prs = ParameterArchiveApi.getParameterRetrievalService(ysi);

        List<NamedObjectId> ids = new ArrayList<>();
        Mdb mdb = MdbFactory.getInstance(instance);
        String namespace = null;
        int interval = -1;
        boolean ascending = !request.getOrder().equals("desc");

        long start = TimeEncoding.INVALID_INSTANT;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = TimeEncoding.INVALID_INSTANT;
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }
        if (start != TimeEncoding.INVALID_INSTANT && stop != TimeEncoding.INVALID_INSTANT && start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        List<ParameterWithId> pids = new ArrayList<>();

        for (String id : request.getParametersList()) {
            ParameterWithId paramWithId = MdbApi.verifyParameterWithId(ctx, mdb, id);
            ids.add(paramWithId.getId());
            pids.add(paramWithId);
        }
        if (request.hasNamespace()) {
            namespace = request.getNamespace();
        }
        if (request.hasInterval() && request.getInterval() >= 0) {
            interval = request.getInterval();
        }

        if (request.hasList()) {
            var plistService = ParameterListsApi.verifyService(instance);
            var plist = ParameterListsApi.verifyParameterList(plistService, request.getList());
            for (Parameter p : ParameterListsApi.resolveParameters(ctx, mdb, plist)) {
                if (!ctx.user.hasParameterPrivilege(ObjectPrivilegeType.ReadParameter, p)) {
                    continue;
                }
                if (namespace != null) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        var id = NamedObjectId.newBuilder().setNamespace(namespace).setName(alias).build();
                        ids.add(id);
                        pids.add(new ParameterWithId(p, id, null));
                    }
                } else {
                    var id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
                    ids.add(id);
                    pids.add(new ParameterWithId(p, id, null));
                }
            }
        } else if (ids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (!ctx.user.hasParameterPrivilege(ObjectPrivilegeType.ReadParameter, p)) {
                    continue;
                }
                if (namespace != null) {
                    String alias = p.getAlias(namespace);
                    if (alias != null) {
                        var id = NamedObjectId.newBuilder().setNamespace(namespace).setName(alias).build();
                        ids.add(id);
                        pids.add(new ParameterWithId(p, id, null));
                    }
                } else {
                    var id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
                    ids.add(id);
                    pids.add(new ParameterWithId(p, id, null));
                }
            }
        }

        String filename;
        if (request.hasFilename()) {
            filename = request.getFilename();
        } else {
            String dateString = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            if (ids.size() == 1) {
                NamedObjectId id = ids.get(0);
                String parameterName = id.hasNamespace() ? id.getName() : id.getName().substring(1);
                filename = parameterName.replace('/', '_') + "_export_" + dateString + ".csv";
            } else {
                filename = "parameter_export_" + dateString + ".csv";
            }
        }

        boolean addRaw = false;
        boolean addMonitoring = false;
        for (String extra : request.getExtraList()) {
            if (extra.equals("raw")) {
                addRaw = true;
            } else if (extra.equals("monitoring")) {
                addMonitoring = true;
            } else {
                throw new BadRequestException("Unexpected option for parameter 'extra': " + extra);
            }
        }

        char columnDelimiter = '\t';
        if (request.hasDelimiter()) {
            switch (request.getDelimiter()) {
            case "TAB":
                columnDelimiter = '\t';
                break;
            case "SEMICOLON":
                columnDelimiter = ';';
                break;
            case "COMMA":
                columnDelimiter = ',';
                break;
            default:
                throw new BadRequestException("Unexpected column delimiter");
            }
        }

        var header = Header.QUALIFIED_NAME;
        if (request.hasHeader()) {
            switch (request.getHeader()) {
            case "QUALIFIED_NAME":
                header = Header.QUALIFIED_NAME;
                break;
            case "SHORT_NAME":
                header = Header.SHORT_NAME;
                break;
            case "NONE":
                header = Header.NONE;
                break;
            default:
                throw new BadRequestException("Unexpected value for header option");
            }
        }

        var preserveLastValue = request.hasPreserveLastValue() ? request.getPreserveLastValue() : false;

        long pos = -1;
        int limit = -1;
        if (request.hasPos()) {
            pos = request.getPos();
        }
        if (request.hasLimit()) {
            pos = Math.max(0, pos);
            limit = request.getLimit();
        }

        var optsb = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withRetrieveParameterStatus(false)
                .withAscending(ascending)
                .withoutRealtime(true)
                .withoutReplay(false);

        if (request.hasSource() && isReplayAsked(request.getSource())) {
            optsb = optsb.withoutParchive(true);
        } else {
            optsb = optsb.withoutParchive(false);
        }

        var opts = optsb.build();

        var listener = new CsvParameterStreamer(observer, pos, limit, filename, ids, addRaw, addMonitoring,
                preserveLastValue, interval, columnDelimiter, header);

        prs.retrieveMulti(pids, opts, listener).thenRun(() -> {
            listener.finished();
        }).exceptionally(e -> {
            listener.failed(e);
            return null;
        });
        // observer.setCancelHandler(listener::requestReplayAbortion);
    }

    @Override
    public void streamParameterValues(Context ctx, StreamParameterValuesRequest request,
            Observer<ParameterData> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        var ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        var prs = ParameterArchiveApi.getParameterRetrievalService(ysi);

        Mdb mdb = MdbFactory.getInstance(instance);

        var optsb = ParameterRetrievalOptions.newBuilder()
                .withRetrieveParameterStatus(false)
                .withoutParchive(true)
                .withoutRealtime(true)
                .withoutReplay(false);

        var start = TimeEncoding.INVALID_INSTANT;
        var stop = TimeEncoding.INVALID_INSTANT;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
            optsb.withStart(start);
        }
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
            optsb.withStop(stop);
        }
        if (start != TimeEncoding.INVALID_INSTANT && stop != TimeEncoding.INVALID_INSTANT && start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        List<ParameterWithId> pids = new ArrayList<>();

        for (NamedObjectId id : request.getIdsList()) {
            ParameterWithId paramWithId = MdbApi.verifyParameterWithId(ctx, mdb, id);
            pids.add(paramWithId);
        }

        if (pids.isEmpty()) {
            for (Parameter p : mdb.getParameters()) {
                if (ctx.user.hasParameterPrivilege(ObjectPrivilegeType.ReadParameter, p)) {
                    var id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
                    pids.add(new ParameterWithId(p, id, null));
                }
            }
        }

        if (request.getTmLinksCount() > 0) {
            optsb.withPacketReplayRequest(PacketReplayRequest.newBuilder()
                    .addAllTmLinks(request.getTmLinksList())
                    .build());
        }

        var replayListener = new PaginatedMultiParameterRetrievalConsumer() {
            @Override
            protected void onParameterData(List<ParameterValueWithId> params) {
                ParameterData.Builder pd = ParameterData.newBuilder();
                for (ParameterValueWithId pvalid : params) {
                    var pval = pvalid.toGbpParameterValue();
                    pd.addParameter(pval);
                }
                observer.next(pd.build());
            }
        };

        prs.retrieveMulti(pids, optsb.build(), replayListener).thenRun(() -> {
            observer.complete();
        }).exceptionally(e -> {
            observer.completeExceptionally(e);
            return null;
        });
    }

    @Override
    public Observer<LoadParameterValuesRequest> loadParameterValues(Context ctx,
            Observer<LoadParameterValuesResponse> observer) {
        return new Observer<>() {

            int count = 0;
            long minGenerationTime = TimeEncoding.INVALID_INSTANT;
            long maxGenerationTime = TimeEncoding.INVALID_INSTANT;
            Mdb mdb;
            TimeService timeService;
            StreamParameterSender sender;

            @Override
            public void next(LoadParameterValuesRequest request) {
                if (count == 0) {
                    var instance = InstancesApi.verifyInstance(request.getInstance());
                    var streamName = request.hasStream() ? request.getStream() : "pp_dump";
                    var stream = YarchDatabase.getInstance(instance).getStream(streamName);
                    mdb = MdbFactory.getInstance(instance);
                    timeService = YamcsServer.getTimeService(instance);
                    sender = new StreamParameterSender(instance, stream);
                }

                var acquisitionTime = timeService.getMissionTime();

                var valueCount = request.getValuesCount();
                if (valueCount > 0) {
                    var pvals = new ArrayList<ParameterValue>(valueCount);
                    for (var update : request.getValuesList()) {
                        var pid = MdbApi.verifyParameterWithId(ctx, mdb, update.getParameter());
                        ctx.checkParameterPrivilege(ObjectPrivilegeType.WriteParameter, pid.getParameter());

                        var pval = new ParameterValue(pid.getParameter());
                        pval.setAcquisitionTime(acquisitionTime);
                        if (update.hasValue()) {
                            pval.setEngValue(ValueUtility.fromGpb(update.getValue()));
                        }
                        if (update.hasGenerationTime()) {
                            var instant = TimeEncoding.fromProtobufTimestamp(update.getGenerationTime());
                            if (minGenerationTime == TimeEncoding.INVALID_INSTANT || instant < minGenerationTime) {
                                minGenerationTime = instant;
                            }
                            if (maxGenerationTime == TimeEncoding.INVALID_INSTANT || instant > maxGenerationTime) {
                                maxGenerationTime = instant;
                            }
                            pval.setGenerationTime(instant);
                        }
                        if (update.hasExpiresIn()) {
                            pval.setExpireMillis(update.getExpiresIn());
                        }
                        pvals.add(pval);
                    }

                    sender.sendParameters(pvals);
                    count += valueCount;
                }
            }

            @Override
            public void completeExceptionally(Throwable t) {
                observer.completeExceptionally(t);
            }

            @Override
            public void complete() {
                var responseb = LoadParameterValuesResponse.newBuilder()
                        .setValueCount(count);
                if (minGenerationTime != TimeEncoding.INVALID_INSTANT) {
                    responseb.setMinGenerationTime(TimeEncoding.toProtobufTimestamp(minGenerationTime));
                }
                if (maxGenerationTime != TimeEncoding.INVALID_INSTANT) {
                    responseb.setMaxGenerationTime(TimeEncoding.toProtobufTimestamp(maxGenerationTime));
                }
                observer.complete(responseb.build());
            }
        };
    }

    @Override
    public void getParameterSamples(Context ctx, GetParameterSamplesRequest request, Observer<TimeSeries> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(ysi.getName());

        ParameterWithId pid = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());

        /*
         * TODO check commented out, in order to support sampling system parameters which don't have a type
         * 
         * ParameterType ptype = p.getParameterType(); if (ptype == null) { throw new
         * BadRequestException("Requested parameter has no type"); } else if (!(ptype instanceof FloatParameterType) &&
         * !(ptype instanceof IntegerParameterType)) { throw new
         * BadRequestException("Only integer or float parameters can be sampled. Got " + ptype.getTypeAsString()); }
         */

        long defaultStop = TimeEncoding.getWallclockTime();
        long defaultStart = defaultStop - (1000 * 60 * 60); // 1 hour

        long start = defaultStart;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = defaultStop;
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        if (start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        int sampleCount = request.hasCount() ? request.getCount() : 500;
        boolean useRawValue = request.hasUseRawValue() && request.getUseRawValue();

        Downsampler sampler = new Downsampler(start, stop, sampleCount);
        sampler.setUseRawValue(useRawValue);
        sampler.setGapTime(request.hasGapTime() ? request.getGapTime() : 120000);

        ParameterRetrievalService prs = getParameterRetrievalService(ysi);
        ParameterRetrievalOptions opts = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withAscending(true)
                .withRetrieveRawValues(useRawValue)
                .withRetrieveEngineeringValues(!useRawValue)
                .withoutRealtime(request.getNorealtime())
                .withoutParchive(request.hasSource() && isReplayAsked(request.getSource()))
                .build();
        prs.retrieveScalar(pid, opts, sampler)
                .thenRun(() -> {
                    TimeSeries.Builder series = TimeSeries.newBuilder();
                    for (Sample s : sampler.collect()) {
                        series.addSample(toGPBSample(s));
                    }
                    observer.complete(series.build());
                })
                .exceptionally(e -> {
                    log.warn("Received exception during parameter retrieval", e);
                    observer.completeExceptionally(new InternalServerErrorException(e.toString()));
                    return null;
                });
    }

    @Override
    public void getParameterRanges(Context ctx, GetParameterRangesRequest request, Observer<Ranges> observer) {
        YamcsServerInstance ysi = InstancesApi.verifyInstanceObj(request.getInstance());

        Mdb mdb = MdbFactory.getInstance(ysi.getName());

        ParameterWithId pid = MdbApi.verifyParameterWithId(ctx, mdb, request.getName());

        long start = 0;
        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        long stop = TimeEncoding.getWallclockTime();
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        if (start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        long minGap = request.hasMinGap() ? request.getMinGap() : 0;
        long maxGap = request.hasMaxGap() ? request.getMaxGap() : Long.MAX_VALUE;
        long minRange = request.hasMinRange() ? request.getMinRange() : -1;
        int maxValues = request.hasMaxValues() ? request.getMaxValues() : -1;

        ParameterRanger ranger = new ParameterRanger(minGap, maxGap, minRange, maxValues);

        ParameterRetrievalService prs = getParameterRetrievalService(ysi);
        ParameterRetrievalOptions opts = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withRetrieveRawValues(false)
                .withoutRealtime(request.getNorealtime())
                .build();

        prs.retrieveScalar(pid, opts, ranger)
                .thenRun(() -> {
                    Ranges.Builder ranges = Ranges.newBuilder();
                    for (Range r : ranger.getRanges()) {
                        ranges.addRange(toGPBRange(r));
                    }
                    observer.complete(ranges.build());
                })
                .exceptionally(e -> {
                    log.warn("Received exception during parameter retrieval", e);
                    observer.completeExceptionally(new InternalServerErrorException(e.toString()));
                    return null;
                });
    }

    @Override
    public void listParameterGroups(Context ctx, ListParameterGroupsRequest request,
            Observer<ListParameterGroupsResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(instance);

        var responseb = ListParameterGroupsResponse.newBuilder();
        TableDefinition tableDefinition = ydb.getTable(ParameterRecorder.TABLE_NAME);
        BiMap<String, Short> enumValues = tableDefinition.getEnumValues("group");
        if (enumValues != null) {
            List<String> unsortedGroups = new ArrayList<>();
            for (Entry<String, Short> entry : enumValues.entrySet()) {
                unsortedGroups.add(entry.getKey());
            }
            Collections.sort(unsortedGroups);
            responseb.addAllGroups(unsortedGroups);
        }
        observer.complete(responseb.build());
    }

    private static ParameterRetrievalService getParameterRetrievalService(YamcsServerInstance ysi)
            throws BadRequestException {
        var services = ysi.getServices(ParameterRetrievalService.class);
        if (services.isEmpty()) {
            throw new BadRequestException("ParameterRetrievalService not configured for this instance");
        }

        return services.get(0);
    }

    private static Ranges.Range toGPBRange(Range r) {
        Ranges.Range.Builder b = Ranges.Range.newBuilder();
        b.setCount(r.totalCount());
        b.setStart(TimeEncoding.toProtobufTimestamp(r.start));
        b.setStop(TimeEncoding.toProtobufTimestamp(r.stop));
        var valueCount = 0;
        for (int i = 0; i < r.valueCount(); i++) {
            b.addEngValues(ValueUtility.toGbp(r.getValue(i)));
            b.addCounts(r.getCount(i));
            valueCount += r.getCount(i);
        }
        b.setOtherCount(r.totalCount() - valueCount);

        return b.build();
    }

    private static class CsvParameterStreamer extends PaginatedMultiParameterRetrievalConsumer {

        Observer<HttpBody> observer;
        ParameterFormatter formatter;

        CsvParameterStreamer(Observer<HttpBody> observer, long pos, int limit, String filename, List<NamedObjectId> ids,
                boolean addRaw, boolean addMonitoring, boolean preserveLastValue, int interval, char columnDelimiter,
                Header header) {
            super(pos, limit);
            this.observer = observer;

            formatter = new ParameterFormatter(null, ids, columnDelimiter);
            formatter.setWriteHeader(header);
            formatter.setPrintRaw(addRaw);
            formatter.setPrintMonitoring(addMonitoring);
            formatter.setKeepValues(preserveLastValue);
            formatter.setTimeWindow(interval);

            HttpBody metadata = HttpBody.newBuilder()
                    .setContentType(MediaType.CSV.toString())
                    .setFilename(filename)
                    .build();
            observer.next(metadata);
        }

        @Override
        protected void onParameterData(List<ParameterValueWithId> params) {
            ByteString.Output data = ByteString.newOutput();
            formatter.updateWriter(data, StandardCharsets.UTF_8);

            try {
                formatter.writeParameters(params);
                formatter.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            HttpBody body = HttpBody.newBuilder()
                    .setData(data.toByteString())
                    .build();
            observer.next(body);
        }

        public void failed(Throwable t) {
            observer.completeExceptionally(t);
        }

        public void finished() {
            ByteString.Output data = ByteString.newOutput();
            formatter.updateWriter(data, StandardCharsets.UTF_8);
            try {
                formatter.close();
                if (data.size() > 0) {
                    HttpBody body = HttpBody.newBuilder()
                            .setData(data.toByteString())
                            .build();
                    observer.next(body);
                }
                observer.complete();
            } catch (IOException e) {
                observer.completeExceptionally(e);
            }
        }
    }

    private static TimeSeries.Sample toGPBSample(Sample sample) {
        TimeSeries.Sample.Builder b = TimeSeries.Sample.newBuilder();
        b.setTime(TimeEncoding.toProtobufTimestamp(sample.t));
        b.setN(sample.n);

        if (sample.n > 0) {
            b.setAvg(sample.avg);
            b.setMin(sample.min);
            b.setMax(sample.max);
            b.setMinTime(TimeEncoding.toProtobufTimestamp(sample.minTime));
            b.setMaxTime(TimeEncoding.toProtobufTimestamp(sample.maxTime));
            b.setFirstTime(TimeEncoding.toProtobufTimestamp(sample.firstTime));
            b.setLastTime(TimeEncoding.toProtobufTimestamp(sample.lastTime));
        }

        return b.build();
    }
}
