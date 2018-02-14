package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.ProcessorException;
import org.yamcs.Processor;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Comp.ComputationDef;
import org.yamcs.protobuf.Comp.ComputationDefList;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.SchemaComp;
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
import org.yamcs.web.Computation;
import org.yamcs.web.ComputationFactory;

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
    
    private int compSubscriptionId = -1; // subscription id used for computations

    final CopyOnWriteArrayList<Computation> compList = new CopyOnWriteArrayList<>();

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
        case "subscribeComputations":
            ComputationDefList cdefList = decoder.decodeMessageData(ctx, SchemaComp.ComputationDefList.MERGE).build();
            return subscribeComputations(ctx.getRequestId(), cdefList, client.getAuthToken());
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
                    if (idList.size() == e.getInvalidParameters().size()) {
                        log.warn("Received subscribe attempt will all-invalid parameters");
                    } else {
                        Set<NamedObjectId>valid = new HashSet<>(idList);
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

    private WebSocketReplyData subscribeComputations(int requestId, ComputationDefList cdefList,
            AuthenticationToken authToken)
            throws WebSocketException {

        List<ComputationDef> computations = cdefList.getComputationList();
        List<NamedObjectId> allArguments = new ArrayList<>();
        for (ComputationDef computation : computations) {
            allArguments.addAll(computation.getArgumentList());
        }

        try {
            try {
                if (compSubscriptionId != -1) {
                    pidrm.addItemsToRequest(compSubscriptionId, allArguments, authToken);
                } else {
                    compSubscriptionId = pidrm.addRequest(allArguments, authToken);
                }
            } catch (InvalidIdentification e) {
                if (cdefList.hasAbortOnInvalid() && cdefList.getAbortOnInvalid()) {
                    NamedObjectList nol = NamedObjectList.newBuilder().addAllList(e.getInvalidParameters()).build();
                    WebSocketException ex = new WebSocketException(requestId, e);
                    ex.attachData("InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
                    throw ex;
                } else {
                    // remove computations that have as arguments the invalid parameters
                    computations = new ArrayList<>();
                    allArguments = new ArrayList<>();
                    ListIterator<ComputationDef> it = computations.listIterator();
                    NamedObjectList.Builder invalidList = NamedObjectList.newBuilder();
                    while (it.hasNext()) {
                        ComputationDef computation = it.next();
                        boolean remove = false;
                        for (NamedObjectId argId : computation.getArgumentList()) {
                            if (e.getInvalidParameters().contains(argId)) {
                                remove = true;
                                break;
                            }
                        }
                        if (remove) {
                            it.remove();
                        } else {
                            allArguments.addAll(computation.getArgumentList());
                        }
                    }

                    if (computations.isEmpty()) {
                        log.warn("All requested computations have invalid arguments");
                    } else {
                        log.warn(
                                "Got invalid computation arguments, but continuing subscribe attempt with remaining valids: {} ",
                                computations);
                        if (compSubscriptionId != -1) {
                            pidrm.addItemsToRequest(compSubscriptionId, allArguments, authToken);
                        } else {
                            compSubscriptionId = pidrm.addRequest(allArguments, authToken);
                        }
                    }
                    // TODO send back invalid list as part of nominal response. Requires work in the websocket framework
                    // which
                    // currently only supports ACK responses in the reply itself
                }
            }
        } catch (InvalidIdentification e) {
            log.error("got invalid identification. Should not happen, because checked before", e);
            throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
        } catch (NoPermissionException e) {
            throw new WebSocketException(requestId, "No permission", e);
        }

        try {
            for (ComputationDef cdef : computations) {
                Computation c = ComputationFactory.getComputation(cdef);
                compList.add(c);
            }
        } catch (Exception e) {
            log.warn("Cannot create computation: ", e);
            throw new WebSocketException(requestId, "Could not create computation", e);
        }
        return toAckReply(requestId);
    }

    private WebSocketReplyData unsubscribeAll(int requestId) throws WebSocketException {
        if ((allSubscriptionId == -1) && (subscriptionId==-1)) {
            throw new WebSocketException(requestId, "Not subscribed");
        }
        ParameterRequestManager prm = processor.getParameterRequestManager();
        if(allSubscriptionId != -1) {
            prm.unsubscribeAll(subscriptionId);
        }
        if(subscriptionId != -1) {
            prm.removeRequest(subscriptionId);
        }
        if (compSubscriptionId != -1) {
            prm.removeRequest(compSubscriptionId);
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

        if (subscrId == compSubscriptionId) {
            updateComputations(paramList);
            return;
        }
        ParameterData.Builder pd = ParameterData.newBuilder();
        for (ParameterValueWithId pvwi : paramList) {
            ParameterValue pv = pvwi.getParameterValue();
            pd.addParameter(pv.toGpb(pvwi.getId()));
        }
        sendParameterUpdate(pd.build());
    }

    private void updateComputations(List<ParameterValueWithId> paramList) {
        Map<NamedObjectId, ParameterValue> parameters = new HashMap<>();
        for (ParameterValueWithId pvwi : paramList) {
            parameters.put(pvwi.getId(), pvwi.getParameterValue());
        }
        ParameterData.Builder pd = ParameterData.newBuilder();
        for (Computation c : compList) {
            org.yamcs.protobuf.Pvalue.ParameterValue pv = c.evaluate(parameters);
            if (pv != null) {
                pd.addParameter(pv);
            }
        }
        if (pd.getParameterCount() == 0) {
            return;
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
     * called when the socket is closed.
     * unsubscribe all parameters
     */
    @Override
    public void quit() {
        ParameterRequestManager prm = processor.getParameterRequestManager();
        if (subscriptionId != -1) {
            prm.removeRequest(subscriptionId);
        }
        if (compSubscriptionId != -1) {
            prm.removeRequest(compSubscriptionId);
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
