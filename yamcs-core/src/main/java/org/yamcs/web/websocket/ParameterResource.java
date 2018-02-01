package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaWeb;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Web.ParameterSubscriptionRequest;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.security.AuthenticationToken;
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

    private int subscriptionId = -1;
    private int allSubscriptionId = -1;

    ParameterWithIdRequestHelper pidrm;

    public ParameterResource(WebSocketProcessorClient client) {
        super(client);
        pidrm = new ParameterWithIdRequestHelper(processor.getParameterRequestManager(), this);
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder)
            throws WebSocketException {

        switch (ctx.getOperation()) {
        case WSR_SUBSCRIBE:
        case "subscribe2": // TODO: remove subscribe2 after making sure nobody uses it.
            ParameterSubscriptionRequest req;
            req = decoder.decodeMessageData(ctx, SchemaWeb.ParameterSubscriptionRequest.MERGE).build();
            if (req.getIdCount() == 0) { // maybe using old method
                if (req.getListCount() > 0) {
                    log.warn("Client using old parameter subscription method: {}",
                            wsHandler.getChannel().remoteAddress());
                    req = ParameterSubscriptionRequest.newBuilder().addAllId(req.getListList()).setAbortOnInvalid(true)
                            .build();
                }
            }
            return subscribe(ctx.getRequestId(), req, client.getAuthToken());
        case WSR_SUBSCRIBE_ALL:
            StringMessage stringMessage = decoder.decodeMessageData(ctx, SchemaYamcs.StringMessage.MERGE).build();
            return subscribeAll(ctx.getRequestId(), stringMessage.getMessage(), client.getAuthToken());
        case WSR_UNSUBSCRIBE:
            NamedObjectList unsubscribeList = decoder.decodeMessageData(ctx, SchemaYamcs.NamedObjectList.MERGE).build();
            return unsubscribe(ctx.getRequestId(), unsubscribeList, client.getAuthToken());
        case WSR_UNSUBSCRIBE_ALL:
            return unsubscribeAll(ctx.getRequestId());
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    private WebSocketReplyData subscribe(int requestId, ParameterSubscriptionRequest req, AuthenticationToken authToken)
            throws WebSocketException {
        List<NamedObjectId> idList = req.getIdList();
        try {
            WebSocketReply reply = new WebSocketReply(requestId);
            try {
                if (subscriptionId != -1) {
                    pidrm.addItemsToRequest(subscriptionId, idList, authToken);
                } else {
                    subscriptionId = pidrm.addRequest(idList, req.getUpdateOnExpiration(), authToken);
                }
            } catch (InvalidIdentification e) {
                NamedObjectList invalidList = NamedObjectList.newBuilder().addAllList(e.getInvalidParameters()).build();

                if (!req.hasAbortOnInvalid() || req.getAbortOnInvalid()) {
                    WebSocketException ex = new WebSocketException(requestId, e);
                    ex.attachData("InvalidIdentification", invalidList, SchemaYamcs.NamedObjectList.WRITE);
                    throw ex;
                } else {
                    idList = new ArrayList<>(idList);
                    idList.removeAll(e.getInvalidParameters());
                    if (idList.isEmpty()) {
                        log.warn("Received subscribe attempt will all-invalid parameters");
                    } else {
                        log.warn(
                                "Received subscribe attempt with {} invalid parameters. Subscription will continue with {} remaining valids.",
                                e.getInvalidParameters().size(), idList.size());
                        if (log.isDebugEnabled()) {
                            log.debug("The invalid IDs are: {}",
                                    StringConverter.idListToString(e.getInvalidParameters()));
                        }
                        if (subscriptionId != -1) {
                            pidrm.addItemsToRequest(subscriptionId, idList, authToken);
                        } else {
                            subscriptionId = pidrm.addRequest(idList, req.getUpdateOnExpiration(), authToken);
                        }
                    }
                    reply.attachData("InvalidIdentification", invalidList, SchemaYamcs.NamedObjectList.WRITE);
                }
            }

            wsHandler.sendReply(reply);
            if (req.hasSendFromCache() && req.getSendFromCache() && pidrm.hasParameterCache()) {
                List<ParameterValueWithId> pvlist = pidrm.getValuesFromCache(idList, authToken);
                if (!pvlist.isEmpty()) {
                    update(subscriptionId, pvlist);
                }
            }

            return null;
        } catch (InvalidIdentification e) {
            log.warn("got invalid identification: {}", e.getMessage());
            throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
        } catch (InvalidRequestIdentification e) {
            log.error("got invalid subscription id", e);
            throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
        } catch (NoPermissionException e) {
            log.error("no permission for parameters: {}", e.getMessage());
            throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
    }

    private WebSocketReplyData unsubscribe(int requestId, NamedObjectList paraList, AuthenticationToken authToken)
            throws WebSocketException {
        if (subscriptionId != -1) {
            try {
                pidrm.removeItemsFromRequest(subscriptionId, paraList.getListList(), authToken);
            } catch (NoPermissionException e) {
                throw new WebSocketException(requestId, "No permission", e);
            }
            return toAckReply(requestId);
        } else {
            throw new WebSocketException(requestId, "Not subscribed to anything");
        }
    }

    private WebSocketReplyData subscribeAll(int requestId, String namespace, AuthenticationToken authToken)
            throws WebSocketException {
        if (allSubscriptionId != -1) {
            throw new WebSocketException(requestId, "Already subscribed for this client");
        }
        try {
            allSubscriptionId = pidrm.subscribeAll(namespace, authToken);
        } catch (NoPermissionException e) {
            throw new WebSocketException(requestId, "No permission", e);
        }
        return toAckReply(requestId);
    }

    private WebSocketReplyData unsubscribeAll(int requestId) throws WebSocketException {
        if ((allSubscriptionId == -1) && (subscriptionId == -1)) {
            throw new WebSocketException(requestId, "Not subscribed");
        }
        ParameterRequestManager prm = processor.getParameterRequestManager();
        if (allSubscriptionId != -1) {
            prm.unsubscribeAll(subscriptionId);
        }
        if (subscriptionId != -1) {
            prm.removeRequest(subscriptionId);
        }

        return toAckReply(requestId);
    }

    @Override
    public void update(int subscrId, List<ParameterValueWithId> paramList) {
        if (wsHandler == null) {
            return;
        }
        if (paramList == null || paramList.isEmpty()) {
            return;
        }
        ParameterData.Builder pd = ParameterData.newBuilder();
        for (ParameterValueWithId pvwi : paramList) {
            ParameterValue pv = pvwi.getParameterValue();
            pd.addParameter(pv.toGpb(pvwi.getId()));
        }
        sendParameterUpdate(pd.build());
    }

    private void sendParameterUpdate(ParameterData pd) {
        try {
            wsHandler.sendData(ProtoDataType.PARAMETER, pd, SchemaPvalue.ParameterData.WRITE);
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
        if (subscriptionId != -1) {
            prm.removeRequest(subscriptionId);
        }
    }

    @Override
    public void switchProcessor(Processor oldProcessor, Processor newProcessor) throws ProcessorException {
        try {
            List<NamedObjectId> invalid = pidrm.switchPrm(newProcessor.getParameterRequestManager(),
                    client.getAuthToken());
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
