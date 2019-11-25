package org.yamcs.http.websocket;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.http.api.RestHandler;
import org.yamcs.management.ManagementService;
import org.yamcs.management.ManagementService.LinkWithInfo;
import org.yamcs.protobuf.Cop1Status;
import org.yamcs.protobuf.Cop1SubscriptionRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.tctm.Link;
import org.yamcs.tctm.ccsds.Cop1Monitor;
import org.yamcs.tctm.ccsds.Cop1TcPacketHandler;

/**
 * Provides subscription to COP1 data links monitoring.
 * 
 */
public class Cop1Resource implements WebSocketResource {
    private static ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1);

    private ConnectedWebSocketClient client;
    private static final Logger log = LoggerFactory.getLogger(ParameterResource.class);

    private Map<String, MyCop1Monitor> subscribed = new HashMap<>();
    private ScheduledFuture<?> future = null;

    public Cop1Resource(ConnectedWebSocketClient client) {
        this.client = client;

    }

    @Override
    public String getName() {
        return "cop1";
    }

    @Override
    public WebSocketReply subscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        Cop1SubscriptionRequest req = verifyRequest(ctx, decoder);
        String instance = req.getInstance();
        String linkName = req.getLinkName();

        Cop1TcPacketHandler cop1Link = verifyCop1Link(ctx.getRequestId(), instance, linkName);
        MyCop1Monitor m = new MyCop1Monitor(cop1Link);
        MyCop1Monitor m1 = subscribed.put(getName(instance, linkName), m);
        if (m1 != null) {
            cop1Link.removeMonitor(m1);
        }
        cop1Link.addMonitor(m);
        if (future == null) {
            future = timer.scheduleAtFixedRate(() -> sendPeriodicStatus(), 1, 1, TimeUnit.SECONDS);
        }
        client.sendReply(WebSocketReply.ack(ctx.getRequestId()));

        sendPeriodicStatus();
        
        return null;
    }

    @Override
    public WebSocketReply unsubscribe(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        Cop1SubscriptionRequest req = verifyRequest(ctx, decoder);
        Cop1TcPacketHandler cop1Link = verifyCop1Link(ctx.getRequestId(), req.getInstance(), req.getLinkName());
        ubsubscribe(cop1Link);

        if (subscribed.isEmpty() && future != null) {
            future.cancel(true);
        }

        return WebSocketReply.ack(ctx.getRequestId());
    }

    @Override
    public void socketClosed() {
        for (MyCop1Monitor m : subscribed.values()) {
            m.cop1Link.removeMonitor(m);
        }
        subscribed.clear();
        if(future!=null) {
            future.cancel(true);
        }
    }

    public void sendPeriodicStatus() {
        for (MyCop1Monitor m : subscribed.values()) {
            m.sendStatus();
        }
    }

    private void ubsubscribe(Cop1TcPacketHandler cop1Link) {
        MyCop1Monitor m = subscribed.remove(getName(cop1Link.getYamcsInstance(), cop1Link.getName()));
        if (m != null) {
            m.cop1Link.removeMonitor(m);
        }
    }

    class MyCop1Monitor implements Cop1Monitor {
        final Cop1TcPacketHandler cop1Link;
        Cop1Status lastStatus;

        MyCop1Monitor(final Cop1TcPacketHandler cop1Link) {
            this.cop1Link = cop1Link;
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
                log.debug("Unsubscribing from COP1 link {}/{} because it is not running", cop1Link.getYamcsInstance(),
                        cop1Link.getName());
                ubsubscribe(cop1Link);
                return;
            }
            CompletableFuture<Cop1Status> cf = cop1Link.getCop1Status();
            cf.whenComplete((v, error) -> {
                if (error == null) {
                    if (lastStatus == null || !lastStatus.equals(v)) {
                        client.sendData(ProtoDataType.COP1_STATUS, v);
                        lastStatus = v;
                    }
                } else {
                    log.warn("Failed to get the Cop1Status ", error);
                    ubsubscribe(cop1Link);
                }
            });
        }

        @Override
        public void disabled() {
           sendStatus();
        }

    }

    private Cop1TcPacketHandler verifyCop1Link(int reqId, String instance, String name) throws WebSocketException {
        RestHandler.verifyInstance(instance);
        Optional<LinkWithInfo> o = ManagementService.getInstance().getLinkWithInfo(instance, name);
        if (!o.isPresent()) {
            throw new WebSocketException(reqId, "There is no link named '" + name + "' in instance " + instance);
        }
        Link link = o.get().getLink();
        if (link instanceof Cop1TcPacketHandler) {
            return (Cop1TcPacketHandler) link;
        }
        throw new WebSocketException(reqId, "Link '" + name + "' in instance " + instance + " does not support COP1");
    }

    private Cop1SubscriptionRequest verifyRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {
        if (ctx.getData() == null) {
            throw new WebSocketException(ctx.getRequestId(),
                    "Request must containa a body specifying the instance and linkName");
        }

        Cop1SubscriptionRequest req = decoder.decodeMessageData(ctx, Cop1SubscriptionRequest.newBuilder()).build();
        if (!(req.hasLinkName() && req.hasInstance())) {
            throw new WebSocketException(ctx.getRequestId(), "instance and linkName are mandatory in the request");
        }
        return req;
    }

    private String getName(String instance, String linkName) {
        return instance + "." + linkName;
    }

    @Override
    public void selectProcessor(Processor processor) throws ProcessorException {
        // ignore, we don't have anything to do with processors
    }

    @Override
    public void unselectProcessor() {
        // ignore, we don't have anything to do with processors
    }

}
