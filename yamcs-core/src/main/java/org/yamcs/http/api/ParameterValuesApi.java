package org.yamcs.http.api;

import static org.yamcs.http.api.ParameterArchiveApi.isReplayAsked;

import java.util.ArrayList;

import org.yamcs.YamcsServer;
import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.api.AbstractPaginatedParameterRetrievalConsumer.PaginatedSingleParameterRetrievalConsumer;
import org.yamcs.logging.Log;
import org.yamcs.mdb.Mdb;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterRetrievalOptions;
import org.yamcs.parameter.ParameterRetrievalService;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithId;
import org.yamcs.protobuf.AbstractParameterValuesApi;
import org.yamcs.protobuf.ListParameterHistoryRequest;
import org.yamcs.protobuf.ListParameterHistoryResponse;
import org.yamcs.protobuf.LoadParameterValuesRequest;
import org.yamcs.protobuf.LoadParameterValuesResponse;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.tctm.StreamParameterSender;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.yarch.YarchDatabase;

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

    private static ParameterRetrievalService getParameterRetrievalService(YamcsServerInstance ysi)
            throws BadRequestException {
        var services = ysi.getServices(ParameterRetrievalService.class);
        if (services.isEmpty()) {
            throw new BadRequestException("ParameterRetrievalService not configured for this instance");
        }

        return services.get(0);
    }
}
