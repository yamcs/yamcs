package org.yamcs.http.api;

import java.util.ArrayList;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.Context;
import org.yamcs.mdb.MdbFactory;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.AbstractParameterValuesApi;
import org.yamcs.protobuf.LoadParameterValuesRequest;
import org.yamcs.protobuf.LoadParameterValuesResponse;
import org.yamcs.security.ObjectPrivilegeType;
import org.yamcs.tctm.StreamParameterSender;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.utils.ValueUtility;
import org.yamcs.mdb.Mdb;
import org.yamcs.yarch.YarchDatabase;

public class ParameterValuesApi extends AbstractParameterValuesApi<Context> {

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
                        ctx.checkObjectPrivileges(ObjectPrivilegeType.WriteParameter,
                                pid.getParameter().getQualifiedName());

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
}
