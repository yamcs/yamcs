package org.yamcs.http.api;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.yamcs.YamcsServerInstance;
import org.yamcs.api.Observer;
import org.yamcs.http.BadRequestException;
import org.yamcs.http.Context;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.logging.Log;
import org.yamcs.management.LinkManager;
import org.yamcs.management.LinkManager.LinkWithInfo;
import org.yamcs.protobuf.AbstractCop1Api;
import org.yamcs.protobuf.Cop1Config;
import org.yamcs.protobuf.Cop1Status;
import org.yamcs.protobuf.DisableRequest;
import org.yamcs.protobuf.GetConfigRequest;
import org.yamcs.protobuf.GetStatusRequest;
import org.yamcs.protobuf.InitializeRequest;
import org.yamcs.protobuf.ResumeRequest;
import org.yamcs.protobuf.SetConfigRequest;
import org.yamcs.protobuf.SubscribeStatusRequest;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.ccsds.Cop1Monitor;
import org.yamcs.tctm.ccsds.Cop1TcPacketHandler;
import org.yamcs.utils.ExceptionUtil;

import com.google.protobuf.Empty;

public class Cop1Api extends AbstractCop1Api<Context> {

    private static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    @Override
    public void initialize(Context ctx, InitializeRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getLink());

        if (!request.hasType()) {
            throw new BadRequestException("No initialization type specified");
        }
        CompletableFuture<Void> cf;

        switch (request.getType()) {
        case SET_VR:
            if (!request.hasVR()) {
                throw new BadRequestException("No vR specified for the SET_VR initialization request");
            }
            cf = cop1Link.initiateADWithVR(request.getVR());
            break;
        case UNLOCK:
            cf = cop1Link.initiateADWithUnlock();
            break;
        case WITH_CLCW_CHECK:

            if (request.hasClcwCheckInitializeTimeout()) {
                cf = cop1Link.initiateAD(true, request.getClcwCheckInitializeTimeout());
            } else {
                cf = cop1Link.initiateAD(true);
            }
            break;
        case WITHOUT_CLCW_CHECK:
            cf = cop1Link.initiateAD(false);
            break;
        default:
            throw new IllegalStateException("Unknown request type " + request.getType());
        }
        sendEmptyResponse(observer, cf);
    }

    @Override
    public void resume(Context ctx, ResumeRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getLink());
        sendEmptyResponse(observer, cop1Link.resume());
    }

    @Override
    public void disable(Context ctx, DisableRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getLink());
        boolean bypassAll = request.hasSetBypassAll() ? request.getSetBypassAll() : true;
        cop1Link.disableCop1(bypassAll);
        observer.complete(Empty.getDefaultInstance());
    }

    @Override
    public void setConfig(Context ctx, SetConfigRequest request, Observer<Empty> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getLink());
        if (!request.hasCop1Config()) {
            throw new BadRequestException("Cop1Config not specified");
        }
        sendEmptyResponse(observer, cop1Link.setConfig(request.getCop1Config()));
    }

    @Override
    public void getConfig(Context ctx, GetConfigRequest request, Observer<Cop1Config> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getLink());
        CompletableFuture<Cop1Config> cf = cop1Link.getCop1Config();
        cf.whenComplete((v, error) -> {
            if (error == null) {
                observer.complete(v);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(new InternalServerErrorException(t));
            }
        });
    }

    @Override
    public void getStatus(Context ctx, GetStatusRequest request, Observer<Cop1Status> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getLink());
        CompletableFuture<Cop1Status> cf = cop1Link.getCop1Status();
        cf.whenComplete((v, error) -> {
            if (error == null) {
                observer.complete(v);
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(new InternalServerErrorException(t));
            }
        });
    }

    @Override
    public void subscribeStatus(Context ctx, SubscribeStatusRequest request, Observer<Cop1Status> observer) {
        Cop1TcPacketHandler cop1Link = verifyCop1Link(request.getInstance(), request.getLink());

        MyCop1Monitor monitor = new MyCop1Monitor(cop1Link, observer);
        cop1Link.addMonitor(monitor);

        ScheduledFuture<?> future = timer.scheduleAtFixedRate(
                () -> monitor.sendStatus(), 0, 1, TimeUnit.SECONDS);

        observer.setCancelHandler(() -> {
            cop1Link.removeMonitor(monitor);
            future.cancel(false);
        });
    }

    private Cop1TcPacketHandler verifyCop1Link(String instance, String linkName) {
        ManagementApi.verifyLink(instance, linkName);
        ManagementApi.verifyInstance(instance);
        YamcsServerInstance ysi = ManagementApi.verifyInstanceObj(instance);
        LinkManager lmgr = ysi.getLinkManager();
        Optional<LinkWithInfo> o = lmgr.getLinkWithInfo(linkName);
        if (!o.isPresent()) {
            throw new BadRequestException("There is no link named '" + linkName + "' in instance " + instance);
        }
        Link link = o.get().getLink();
        if (link instanceof Cop1TcPacketHandler) {
            return (Cop1TcPacketHandler) link;
        }
        throw new BadRequestException(String.format(
                "Link '%s' for instance '%s' does not support COP1",
                linkName, instance));
    }

    private void sendEmptyResponse(Observer<Empty> observer, CompletableFuture<Void> cf) {
        cf.whenComplete((v, error) -> {
            if (error == null) {
                observer.complete(Empty.getDefaultInstance());
            } else {
                Throwable t = ExceptionUtil.unwind(error);
                observer.completeExceptionally(new InternalServerErrorException(t));
            }
        });
    }

    private static class MyCop1Monitor implements Cop1Monitor {

        private static final Log log = new Log(MyCop1Monitor.class);

        private final Cop1TcPacketHandler cop1Link;
        private Cop1Status lastStatus;
        private Observer<Cop1Status> observer;

        MyCop1Monitor(Cop1TcPacketHandler cop1Link, Observer<Cop1Status> observer) {
            this.cop1Link = cop1Link;
            this.observer = observer;
        }

        @Override
        public void suspended(int suspendState) {
        }

        @Override
        public void alert(AlertType alert) {
        }

        @Override
        public void stateChanged(int oldState, int newState) {
            sendStatus();
        }

        void sendStatus() {
            if (!cop1Link.isRunning()) {
                log.debug("Unsubscribing from COP1 link {}/{} because it is not running",
                        cop1Link.getYamcsInstance(), cop1Link.getName());
                cop1Link.removeMonitor(this);
                return;
            }
            CompletableFuture<Cop1Status> cf = cop1Link.getCop1Status();
            cf.whenComplete((status, error) -> {
                if (error == null) {
                    if (lastStatus == null || !lastStatus.equals(status)) {
                        observer.next(status);
                        lastStatus = status;
                    }
                } else {
                    log.warn("Failed to get Cop1Status", error);
                    cop1Link.removeMonitor(this);
                }
            });
        }

        @Override
        public void disabled() {
            sendStatus();
        }
    }
}
