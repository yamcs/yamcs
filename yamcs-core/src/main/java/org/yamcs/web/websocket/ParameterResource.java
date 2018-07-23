package org.yamcs.web.websocket;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.Processor;
import org.yamcs.ProcessorException;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Web.ParameterSubscriptionRequest;
import org.yamcs.protobuf.Web.ParameterSubscriptionResponse;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.security.User;
import org.yamcs.utils.StringConverter;

/**
 * Provides realtime parameter subscription via web.
 *
 * @author nm
 *
 */
public class ParameterResource extends AbstractWebSocketResource implements ParameterWithIdConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParameterResource.class);
    public static final String RESOURCE_NAME = "parameter";

    public static final String WSR_SUBSCRIBE = "subscribe";
    public static final String WSR_UNSUBSCRIBE = "unsubscribe";
    public static final String WSR_SUBSCRIBE_ALL = "subscribeAll";
    public static final String WSR_UNSUBSCRIBE_ALL = "unsubscribeAll";

    private int firstSubscriptionId = -1;
    private int allSubscriptionId = -1;

    ParameterWithIdRequestHelper pidrm;

    public ParameterResource(WebSocketClient client) {
        super(client);
        pidrm = new ParameterWithIdRequestHelper(processor.getParameterRequestManager(), this);
    }

    @Override
    public WebSocketReply processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {

        ParameterSubscriptionRequest req;
        req = decoder.decodeMessageData(ctx, ParameterSubscriptionRequest.newBuilder()).build();
        if (req.getIdCount() == 0) { // maybe using old method
            if (req.getListCount() > 0) {
                log.warn("Client using old parameter subscription method: {}",
                        wsHandler.getChannel().remoteAddress());
                req = ParameterSubscriptionRequest.newBuilder().addAllId(req.getListList()).setAbortOnInvalid(true)
                        .build();
            }
        }
        int subscriptionId = getSubscriptionId(req);

        switch (ctx.getOperation()) {
        case WSR_SUBSCRIBE:
            return subscribe(subscriptionId, ctx.getRequestId(), req, client.getUser());
        case WSR_SUBSCRIBE_ALL:
            StringMessage stringMessage = decoder.decodeMessageData(ctx, StringMessage.newBuilder()).build();
            return subscribeAll(ctx.getRequestId(), stringMessage.getMessage(), client.getUser());
        case WSR_UNSUBSCRIBE:
            NamedObjectList unsubscribeList = decoder.decodeMessageData(ctx, NamedObjectList.newBuilder()).build();
            return unsubscribe(subscriptionId, ctx.getRequestId(), unsubscribeList, client.getUser());
        case WSR_UNSUBSCRIBE_ALL:
            return unsubscribeAll(subscriptionId, ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private int getSubscriptionId(ParameterSubscriptionRequest req) {
        if (!req.hasSubscriptionId() || req.getSubscriptionId() == 0) {
            return firstSubscriptionId;
        } else {
            return req.getSubscriptionId();
        }
    }

    private WebSocketReply subscribe(int subscriptionId, int requestId, ParameterSubscriptionRequest req, User user)
            throws WebSocketException {
        List<NamedObjectId> idList = req.getIdList();
        try {
            WebSocketReply reply = new WebSocketReply(requestId);
            try {
                if (subscriptionId != -1) {
                    pidrm.addItemsToRequest(subscriptionId, idList, user);
                } else {
                    subscriptionId = pidrm.addRequest(idList, req.getUpdateOnExpiration(), user);
                    if (firstSubscriptionId == -1) {
                        firstSubscriptionId = subscriptionId;
                    }
                }
                ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.newBuilder()
                        .setSubscriptionId(subscriptionId)
                        .build();
                reply.attachData("ParameterSubscriptionResponse", psr);
            } catch (InvalidIdentification e) {
                NamedObjectList invalidList = NamedObjectList.newBuilder().addAllList(e.getInvalidParameters()).build();

                if (!req.hasAbortOnInvalid() || req.getAbortOnInvalid()) {
                    WebSocketException ex = new WebSocketException(requestId, e);
                    ex.attachData("InvalidIdentification", invalidList);
                    throw ex;
                } else {
                    if (idList.size() == e.getInvalidParameters().size()) {
                        log.warn("Received subscribe attempt will all-invalid parameters");
                        idList = Collections.emptyList();
                    } else {
                        Set<NamedObjectId> valid = new HashSet<>(idList);
                        valid.removeAll(e.getInvalidParameters());
                        idList = new ArrayList<>(valid);

                        log.warn(
                                "Received subscribe attempt with {} invalid parameters. Subscription will continue with {} remaining valids.",
                                e.getInvalidParameters().size(), idList.size());
                        if (log.isDebugEnabled()) {
                            log.debug("The invalid IDs are: {}",
                                    StringConverter.idListToString(e.getInvalidParameters()));
                        }
                        if (subscriptionId != -1) {
                            pidrm.addItemsToRequest(subscriptionId, idList, user);
                        } else {
                            subscriptionId = pidrm.addRequest(idList, req.getUpdateOnExpiration(), user);
                        }
                        if (firstSubscriptionId == -1) {
                            firstSubscriptionId = subscriptionId;
                        }
                    }
                    ParameterSubscriptionResponse psr = ParameterSubscriptionResponse.newBuilder()
                            .setSubscriptionId(subscriptionId)
                            .addAllInvalid(e.getInvalidParameters()).build();
                    reply.attachData(ParameterSubscriptionResponse.class.getSimpleName(), psr);
                }
            }
            wsHandler.sendReply(reply);
            if ((!req.hasSendFromCache() || req.getSendFromCache())) {
                List<ParameterValueWithId> pvlist = pidrm.getValuesFromCache(idList, user);
                if (!pvlist.isEmpty()) {
                    update(subscriptionId, pvlist);
                }
            }

            return null;
        } catch (InvalidIdentification e) {
            log.warn("got invalid identification: {}", e.getMessage());
            throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
        } catch (InvalidRequestIdentification e) {
            log.warn("got invalid subscription id for {}", subscriptionId);
            throw new WebSocketException(requestId, "invalid subscription id " + e.getSubscriptionId());
        } catch (NoPermissionException e) {
            log.warn("no permission for parameters: {}", e.getMessage());
            throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
    }

    private WebSocketReply unsubscribe(int subscriptionId, int requestId, NamedObjectList paraList, User user)
            throws WebSocketException {
        if (subscriptionId != -1) {
            try {
                pidrm.removeItemsFromRequest(subscriptionId, paraList.getListList(), user);
            } catch (NoPermissionException e) {
                throw new WebSocketException(requestId, "No permission", e);
            }
            return WebSocketReply.ack(requestId);
        } else {
            throw new WebSocketException(requestId, "Not subscribed to anything");
        }
    }

    private WebSocketReply subscribeAll(int requestId, String namespace, User user)
            throws WebSocketException {
        if (allSubscriptionId != -1) {
            throw new WebSocketException(requestId, "Already subscribed for this client");
        }
        try {
            allSubscriptionId = pidrm.subscribeAll(namespace, user);
        } catch (NoPermissionException e) {
            throw new WebSocketException(requestId, "No permission", e);
        }
        return WebSocketReply.ack(requestId);
    }

    private WebSocketReply unsubscribeAll(int subscriptionId, int requestId) throws WebSocketException {
        if ((allSubscriptionId == -1) && (subscriptionId == -1)) {
            throw new WebSocketException(requestId, "Not subscribed");
        }
        ParameterRequestManager prm = processor.getParameterRequestManager();
        if (allSubscriptionId != -1) {
            prm.unsubscribeAll(subscriptionId);
        }

        if (subscriptionId != -1) {
            pidrm.removeRequest(subscriptionId);
            if (subscriptionId == firstSubscriptionId) {
                firstSubscriptionId = -1;
            }
        }

        return WebSocketReply.ack(requestId);
    }

    @Override
    public void update(int subscrId, List<ParameterValueWithId> paramList) {
        if (wsHandler == null) {
            return;
        }
        if (paramList == null || paramList.isEmpty()) {
            return;
        }
        ParameterData.Builder pd = ParameterData.newBuilder()
                .setSubscriptionId(subscrId);
        for (ParameterValueWithId pvwi : paramList) {
            ParameterValue pv = pvwi.getParameterValue();
            pd.addParameter(pv.toGpb(pvwi.getId()));
        }
        sendParameterUpdate(pd.build());
    }

    private void sendParameterUpdate(ParameterData pd) {
        try {
            wsHandler.sendData(ProtoDataType.PARAMETER, pd);
        } catch (ClosedChannelException e) {
            log.warn("got channel closed when trying sending parameter updates, quitting");
            quit();
        } catch (Exception e) {
            log.warn("got error when sending parameter updates, quitting", e);
            quit();
        }
    }

    /**
     * called when the socket is closed. unsubscribe all parameters
     */
    @Override
    public void quit() {
        ParameterRequestManager prm = processor.getParameterRequestManager();
        pidrm.quit();
    }

    @Override
    public void switchProcessor(Processor oldProcessor, Processor newProcessor) throws ProcessorException {
        try {
            List<NamedObjectId> invalid = pidrm.switchPrm(newProcessor.getParameterRequestManager(), client.getUser());
            super.switchProcessor(oldProcessor, newProcessor);
            if (!invalid.isEmpty()) {
                // send notification for invalid parameters
                ParameterData.Builder pd = ParameterData.newBuilder();
                long now = newProcessor.getCurrentTime();
                for (NamedObjectId id : invalid) {
                    pd.addParameter(org.yamcs.protobuf.Pvalue.ParameterValue.newBuilder().setId(id)
                            .setAcquisitionTime(now)
                            .setAcquisitionStatus(AcquisitionStatus.INVALID).build());
                }
                sendParameterUpdate(pd.build());
            }
        } catch (NoPermissionException e) {
            throw new ProcessorException("No permission", e);
        }
    }
}
