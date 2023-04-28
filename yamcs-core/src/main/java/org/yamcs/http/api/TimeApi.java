package org.yamcs.http.api;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.protobuf.AbstractTimeApi;
import org.yamcs.protobuf.LeapSecondsTable;
import org.yamcs.protobuf.LeapSecondsTable.ValidityRange;
import org.yamcs.protobuf.SetTimeRequest;
import org.yamcs.protobuf.SubscribeTimeRequest;
import org.yamcs.time.SimulationTimeService;
import org.yamcs.time.TimeService;
import org.yamcs.utils.TaiUtcConverter.ValidityLine;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;

public class TimeApi extends AbstractTimeApi<Context> {

    @Override
    public void getLeapSeconds(Context ctx, Empty request, Observer<LeapSecondsTable> observer) {
        LeapSecondsTable.Builder b = LeapSecondsTable.newBuilder();
        List<ValidityLine> lines = TimeEncoding.getTaiUtcConversionTable();
        for (int i = 0; i < lines.size(); i++) {
            ValidityLine line = lines.get(i);
            long instant = TimeEncoding.fromUnixMillisec(line.unixMillis);
            ValidityRange.Builder rangeb = ValidityRange.newBuilder()
                    .setStart(TimeEncoding.toString(instant))
                    .setLeapSeconds(line.seconds - 10)
                    .setTaiDifference(line.seconds);
            if (i != lines.size() - 1) {
                ValidityLine next = lines.get(i + 1);
                instant = TimeEncoding.fromUnixMillisec(next.unixMillis);
                rangeb.setStop(TimeEncoding.toString(instant));
            }
            b.addRanges(rangeb);
        }
        observer.complete(b.build());
    }

    @Override
    public void setTime(Context ctx, SetTimeRequest request, Observer<Empty> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        YamcsServer yamcs = YamcsServer.getServer();
        TimeService timeService = yamcs.getInstance(instance).getTimeService();

        if (timeService instanceof SimulationTimeService) {
            SimulationTimeService sts = (SimulationTimeService) timeService;

            if (request.hasTime0()) {
                sts.setTime0(TimeEncoding.fromProtobufTimestamp(request.getTime0()));
            }
            if (request.hasSpeed()) {
                sts.setSimSpeed(request.getSpeed());
            }
            if (request.hasElapsedTime()) {
                sts.setSimElapsedTime(request.getElapsedTime());
            }

            observer.complete(Empty.getDefaultInstance());
        } else {
            observer.completeExceptionally(new BadRequestException("Cannot set time for a non-simulation TimeService"));
        }
    }

    @Override
    public void subscribeTime(Context ctx, SubscribeTimeRequest request, Observer<Timestamp> observer) {
        var instance = InstancesApi.verifyInstance(request.getInstance());
        TimeProvider provider;
        if (request.hasProcessor()) {
            var processor = ProcessingApi.verifyProcessor(request.getInstance(), request.getProcessor());
            provider = () -> processor.getCurrentTime();
        } else {
            var yamcs = YamcsServer.getServer();
            var timeService = yamcs.getInstance(instance).getTimeService();
            provider = () -> timeService.getMissionTime();
        }

        var exec = YamcsServer.getServer().getThreadPoolExecutor();
        var future = exec.scheduleAtFixedRate(() -> {
            var time = provider.getTime();
            observer.next(TimeEncoding.toProtobufTimestamp(time));
        }, 0, 1, TimeUnit.SECONDS);

        observer.setCancelHandler(() -> future.cancel(false));
    }

    @FunctionalInterface
    private static interface TimeProvider {
        long getTime();
    }
}
