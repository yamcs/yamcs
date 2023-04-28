package org.yamcs.http.api;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.YamcsServer;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.HttpException;
import org.yamcs.http.NotFoundException;
import org.yamcs.protobuf.AbstractTimeCorrelationApi;
import org.yamcs.protobuf.AddTimeOfFlightIntervalsRequest;
import org.yamcs.protobuf.DeleteTimeOfFlightIntervalsRequest;
import org.yamcs.protobuf.GetTcoConfigRequest;
import org.yamcs.protobuf.GetTcoStatusRequest;
import org.yamcs.protobuf.SetCoefficientsRequest;
import org.yamcs.protobuf.SetTcoConfigRequest;
import org.yamcs.protobuf.TcoConfig;
import org.yamcs.protobuf.TcoResetRequest;
import org.yamcs.protobuf.TcoStatus;
import org.yamcs.protobuf.TofInterval;
import org.yamcs.security.SystemPrivilege;
import org.yamcs.time.Instant;
import org.yamcs.time.TimeCorrelationService;
import org.yamcs.time.TimeOfFlightEstimator;
import org.yamcs.utils.TimeEncoding;

import com.google.protobuf.Empty;
import com.google.protobuf.Timestamp;

public class TimeCorrelationApi extends AbstractTimeCorrelationApi<Context> {

    @Override
    public void getConfig(Context ctx, GetTcoConfigRequest request, Observer<TcoConfig> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TimeCorrelationService tco = verifyService(ctx, instance, request.getServiceName());
        observer.complete(tco.getTcoConfig());
    }

    @Override
    public void setConfig(Context ctx, SetTcoConfigRequest request, Observer<Empty> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TimeCorrelationService tco = verifyService(ctx, instance, request.getServiceName());

        TcoConfig conf = request.getConfig();

        if (conf.hasAccuracy()) {
            tco.setAccuracy(verifyPositive("accuracy", conf.getAccuracy()));
        }

        if (conf.hasValidity()) {
            tco.setValidity(verifyPositive("validity", conf.getValidity()));
        }

        if (conf.hasDefaultTof()) {
            tco.setDefaultTof(verifyPositive("defaultTof", conf.getDefaultTof()));
        }

        if (conf.hasOnboardDelay()) {
            tco.setOnboardDelay(verifyPositive("onboardDelay", conf.getOnboardDelay()));
        }

        observer.complete(Empty.getDefaultInstance());
    }

    private double verifyPositive(String name, double value) {
        if (value < 0 || !Double.isFinite(value)) {
            throw new BadRequestException("Invalid " + name);
        }
        return value;
    }

    @Override
    public void getStatus(Context ctx, GetTcoStatusRequest request, Observer<TcoStatus> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TimeCorrelationService tco = verifyService(ctx, instance, request.getServiceName());

        observer.complete(tco.getStatus());
    }

    @Override
    public void setCoefficients(Context ctx, SetCoefficientsRequest request, Observer<Empty> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TimeCorrelationService tco = verifyService(ctx, instance, request.getServiceName());
        if (!request.hasCoefficients()) {
            throw new BadRequestException("no coefficients provided");
        }

        org.yamcs.protobuf.TcoCoefficients pcoef = request.getCoefficients();
        if (!pcoef.hasUtc()) {
            throw new BadRequestException("no UTC provided");
        }
        if (!pcoef.hasObt()) {
            throw new BadRequestException("no OBT provided");
        }
        double gradient = pcoef.hasGradient() ? pcoef.getGradient() : 0;
        double offset = pcoef.hasOffset() ? pcoef.getOffset() : 0;

        tco.forceCoefficients(TimeEncoding.fromProtobufHresTimestamp(pcoef.getUtc()), pcoef.getObt(), offset, gradient);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void reset(Context ctx, TcoResetRequest request, Observer<Empty> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TimeCorrelationService tco = verifyService(ctx, instance, request.getServiceName());
        tco.reset();
        observer.complete();
    }

    @Override
    public void addTimeOfFlightIntervals(Context ctx, AddTimeOfFlightIntervalsRequest request,
            Observer<Empty> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TimeCorrelationService tco = verifyService(ctx, instance, request.getServiceName());
        TimeOfFlightEstimator tofEstimator = tco.getTofEstimator();
        if (tofEstimator == null) {
            throw new BadRequestException(
                    "Time of flight estimator not configured for this service ( 'useTofEstimator: true' in the configuration)");
        }
        List<TimeOfFlightEstimator.TofInterval> intervalList = new ArrayList<>();

        for (TofInterval ti : request.getIntervalsList()) {
            Instant start = verifyInstant("ertStart", ti.getErtStart());
            Instant stop = verifyInstant("ertStop", ti.getErtStop());

            if (ti.getPolCoefCount() == 0) {
                throw new BadRequestException("no polynomial coefficient has been specified");
            }
            double[] coef = ti.getPolCoefList().stream().mapToDouble(d -> d).toArray();
            intervalList.add(new TimeOfFlightEstimator.TofInterval(start, stop, coef));
        }

        tofEstimator.addIntervals(intervalList);
        observer.complete(Empty.getDefaultInstance());
    }

    private Instant verifyInstant(String name, Timestamp value) {
        if (value == null) {
            throw new BadRequestException(name + " has not been provided");
        }
        return TimeEncoding.fromProtobufHresTimestamp(value);
    }

    @Override
    public void deleteTimeOfFlightIntervals(Context ctx, DeleteTimeOfFlightIntervalsRequest request,
            Observer<Empty> observer) {
        String instance = InstancesApi.verifyInstance(request.getInstance());
        TimeCorrelationService tco = verifyService(ctx, instance, request.getServiceName());
        TimeOfFlightEstimator tofEstimator = tco.getTofEstimator();
        Instant start = verifyInstant("start", request.getStart());
        Instant stop = verifyInstant("stop", request.getStop());

        tofEstimator.deleteSplineIntervals(start, stop);
        observer.complete(Empty.getDefaultInstance());
    }

    TimeCorrelationService verifyService(Context ctx, String yamcsInstance, String serviceName) throws HttpException {
        ctx.checkSystemPrivilege(SystemPrivilege.ControlTimeCorrelation);
        TimeCorrelationService tco = YamcsServer.getServer().getInstance(yamcsInstance)
                .getService(TimeCorrelationService.class, serviceName);
        if (tco == null) {
            throw new NotFoundException("Time correlation service '" + serviceName + "'not found");
        }
        return tco;
    }
}
