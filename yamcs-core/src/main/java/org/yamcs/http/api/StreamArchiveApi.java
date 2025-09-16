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

import org.yamcs.api.HttpBody;
import org.yamcs.api.Observer;
import org.yamcs.archive.ParameterRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.MediaType;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedMultiParameterRetrievalConsumer;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedSingleParameterRetrievalConsumer;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractStreamArchiveApi;
import org.yamcs.protobuf.Archive.ExportParameterValuesRequest;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterGroupsRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryRequest;
import org.yamcs.protobuf.Archive.ListParameterHistoryResponse;
import org.yamcs.protobuf.Archive.ParameterGroupInfo;
import org.yamcs.protobuf.Archive.StreamParameterValuesRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.ParameterFormatter.Header;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;
import com.google.protobuf.ByteString;

public class StreamArchiveApi extends AbstractStreamArchiveApi<Context> {

    @Override
    public void listParameterGroups(Context ctx, ListParameterGroupsRequest request,
            Observer<ParameterGroupInfo> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
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
            responseb.addAllGroups(unsortedGroups);
        }
        observer.complete(responseb.build());
    }

    @Override
    public void listParameterHistory(Context ctx, ListParameterHistoryRequest request,
            Observer<ListParameterHistoryResponse> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());

        var ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        var prs = ParameterArchiveApi.getParameterRetrievalService(ysi);

        Mdb mdb = MdbFactory.getInstance(instance);
        String pathName = request.getName();

        ParameterWithId p = MdbApi.verifyParameterWithId(ctx, mdb, pathName);

        long pos = request.hasPos() ? request.getPos() : 0;
        int limit = request.hasLimit() ? request.getLimit() : 100;
        boolean noRepeat = request.getNorepeat();
        boolean ascending = request.getOrder().equals("asc");
        int maxBytes = request.hasMaxBytes() ? request.getMaxBytes() : -1;

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

        boolean replayRequested = request.hasSource() && isReplayAsked(request.getSource());
        ParameterRetrievalOptions opts = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withAscending(ascending)
                .withRetrieveParameterStatus(false)
                .withoutRealtime(request.getNorealtime())
                .withoutParchive(replayRequested)
                .withoutReplay(!replayRequested)
                .build();

        ListParameterHistoryResponse.Builder resultb = ListParameterHistoryResponse.newBuilder();
        PaginatedSingleParameterRetrievalConsumer replayListener = new PaginatedSingleParameterRetrievalConsumer(pos,
                limit) {
            @Override
            public void onParameterData(ParameterValueWithId pvalid) {
                resultb.addParameter(toGpb(pvalid, maxBytes));
            }
        };
        replayListener.setNoRepeat(noRepeat);

        prs.retrieveSingle(p, opts, replayListener).thenRun(() -> {
            observer.complete(resultb.build());
        }).exceptionally(e -> {
            observer.completeExceptionally(e);
            return null;
        });
    }

    public static ParameterValue toGpb(ParameterValueWithId pvalWithId, int maxBytes) {
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
    public void getParameterSamples(Context ctx, GetParameterSamplesRequest request,
            Observer<TimeSeries> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        var ysi = InstancesApi.verifyInstanceObj(request.getInstance());
        var prs = ParameterArchiveApi.getParameterRetrievalService(ysi);

        Mdb mdb = MdbFactory.getInstance(instance);
        Parameter p = MdbApi.verifyParameter(ctx, mdb, request.getName());

        ParameterType ptype = p.getParameterType();
        if ((ptype != null) && (!(ptype instanceof FloatParameterType) && !(ptype instanceof IntegerParameterType))) {
            throw new BadRequestException(
                    "Only integer or float parameters can be sampled. Got " + ptype.getTypeAsString());
        }

        long stop = TimeEncoding.getWallclockTime();
        long start = stop - (1000 * 60 * 60); // 1 hour

        if (request.hasStart()) {
            start = TimeEncoding.fromProtobufTimestamp(request.getStart());
        }
        if (request.hasStop()) {
            stop = TimeEncoding.fromProtobufTimestamp(request.getStop());
        }

        if (start > stop) {
            throw new BadRequestException("Start date must be before stop date");
        }

        int sampleCount = request.hasCount() ? request.getCount() : 500;
        Downsampler sampler = new Downsampler(start, stop, sampleCount);
        sampler.setUseRawValue(request.hasUseRawValue() && request.getUseRawValue());
        sampler.setGapTime(request.hasGapTime() ? request.getGapTime() : 120000);

        boolean replayRequested = request.hasSource() && isReplayAsked(request.getSource());
        ParameterRetrievalOptions opts = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withRetrieveParameterStatus(false)
                .withoutRealtime(request.getNorealtime())
                .withoutParchive(replayRequested)
                .withoutReplay(!replayRequested)
                .build();

        PaginatedSingleParameterRetrievalConsumer replayListener = new PaginatedSingleParameterRetrievalConsumer() {
            @Override
            public void onParameterData(ParameterValueWithId pvalid) {
                sampler.process(pvalid.getParameterValue());
            }
        };

        NamedObjectId id = NamedObjectId.newBuilder().setName(p.getQualifiedName()).build();
        prs.retrieveSingle(new ParameterWithId(p, id, null), opts, replayListener).thenRun(() -> {
            TimeSeries.Builder series = TimeSeries.newBuilder();
            for (Sample s : sampler.collect()) {
                series.addSample(toGPBSample(s));
            }
            observer.complete(series.build());
        }).exceptionally(e -> {
            observer.completeExceptionally(e);
            return null;
        });

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
                    ParameterValue pval = pvalid.toGbpParameterValue();
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

        ParameterRetrievalOptions opts = ParameterRetrievalOptions.newBuilder()
                .withStartStop(start, stop)
                .withRetrieveParameterStatus(false)
                .withAscending(ascending)
                .withoutParchive(true)
                .withoutRealtime(true)
                .withoutReplay(false)
                .build();

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

    public static TimeSeries.Sample toGPBSample(Sample sample) {
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
}
