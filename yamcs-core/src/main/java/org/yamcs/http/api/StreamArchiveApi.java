package org.yamcs.http.api;

import static org.yamcs.http.api.ParameterArchiveApi.isReplayAsked;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import org.yamcs.api.Observer;
import org.yamcs.archive.ParameterRecorder;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedMultiParameterRetrievalConsumer;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedSingleParameterRetrievalConsumer;
import org.yamcs.http.api.Downsampler.Sample;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractStreamArchiveApi;
import org.yamcs.protobuf.Archive.GetParameterSamplesRequest;
import org.yamcs.protobuf.Archive.ListParameterGroupsRequest;
import org.yamcs.protobuf.Archive.ParameterGroupInfo;
import org.yamcs.protobuf.Archive.StreamParameterValuesRequest;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Pvalue.TimeSeries;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.PacketReplayRequest;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;

import com.google.common.collect.BiMap;

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
}
