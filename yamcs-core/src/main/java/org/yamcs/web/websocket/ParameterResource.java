package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.ProcessorException;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Comp.ComputationDef;
import org.yamcs.protobuf.Comp.ComputationDefList;
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
 * TODO better deal with exceptions
 *
 * @author nm
 *
 */
public class ParameterResource extends AbstractWebSocketResource implements ParameterWithIdConsumer {
    private static final Logger log = LoggerFactory.getLogger(ParameterResource.class);

    private int subscriptionId = -1;
    public static final String WSR_subscribe = "subscribe";
    public static final String WSR_unsubscribe = "unsubscribe";
    public static final String WSR_subscribeAll = "subscribeAll";
    public static final String WSR_unsubscribeAll = "unsubscribeAll";

    //subscription id used for computations
    int compSubscriptionId=-1;

    final CopyOnWriteArrayList<Computation> compList=new CopyOnWriteArrayList<>();

    ParameterWithIdRequestHelper pidrm;

    public ParameterResource(YProcessor yproc, WebSocketFrameHandler wsHandler) {
        super(yproc, wsHandler);
        pidrm = new ParameterWithIdRequestHelper(yproc.getParameterRequestManager(), this);
        wsHandler.addResource("parameter", this);
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder, AuthenticationToken authToken) throws WebSocketException {

        switch (ctx.getOperation()) {
        case WSR_subscribe:
            NamedObjectList subscribeList = decoder.decodeMessageData(ctx, SchemaYamcs.NamedObjectList.MERGE).build();
            return subscribe(ctx.getRequestId(), subscribeList, authToken);
        case "subscribe2":
            // TODO Experimental, but intended to replace WSR_subscribe in a next version. Provides same functionality
            // but with the option to ignore invalid parameters and continue with the subscription
            ParameterSubscriptionRequest req = decoder.decodeMessageData(ctx, SchemaWeb.ParameterSubscriptionRequest.MERGE).build();
            return subscribe2(ctx.getRequestId(), req, authToken);
        case WSR_subscribeAll:
            StringMessage stringMessage = decoder.decodeMessageData(ctx, SchemaYamcs.StringMessage.MERGE).build();
            return subscribeAll(ctx.getRequestId(), stringMessage.getMessage(), authToken);
        case WSR_unsubscribe:
            NamedObjectList unsubscribeList = decoder.decodeMessageData(ctx, SchemaYamcs.NamedObjectList.MERGE).build();
            return unsubscribe(ctx.getRequestId(), unsubscribeList, authToken);
        case WSR_unsubscribeAll:
            return unsubscribeAll(ctx.getRequestId());
        case "subscribeComputations":
            ComputationDefList cdefList = decoder.decodeMessageData(ctx, SchemaComp.ComputationDefList.MERGE).build();
            return subscribeComputations(ctx.getRequestId(), cdefList, authToken);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }

    @Deprecated
    private WebSocketReplyData subscribe(int requestId, NamedObjectList paraList, AuthenticationToken authToken) throws WebSocketException {
        List<NamedObjectId> idlist = paraList.getListList();
        try {
            if(subscriptionId!=-1) {
                pidrm.addItemsToRequest(subscriptionId, idlist, authToken);
            } else {
                subscriptionId=pidrm.addRequest(idlist, authToken);
            }
            WebSocketReplyData reply = toAckReply(requestId);
            wsHandler.sendReply(reply);
            if(pidrm.hasParameterCache()) {
                List<ParameterValueWithId> pvlist = pidrm.getValuesFromCache(idlist, authToken);
                if(!pvlist.isEmpty()) {
                    update(subscriptionId, pvlist);
                }
            }

            return null;
        } catch (InvalidIdentification e) {
            NamedObjectList nol = NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
            WebSocketException ex = new WebSocketException(requestId, e);
            ex.attachData("InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
            throw ex;
        } catch (InvalidRequestIdentification e) {
            log.error("got invalid subscription id", e);
            throw new WebSocketException(requestId, "internal error: "+e.toString(), e);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        } catch (NoPermissionException e) {
            log.error("no permission for parameters", e);
            throw new WebSocketException(requestId, "internal error: "+e.toString(), e);
        }
    }

    private WebSocketReplyData subscribe2(int requestId, ParameterSubscriptionRequest req, AuthenticationToken authToken) throws WebSocketException {
        List<NamedObjectId> idList = req.getIdList();
        try {
            try {
                if(subscriptionId!=-1) {
                    pidrm.addItemsToRequest(subscriptionId, idList, authToken);
                } else {
                    subscriptionId=pidrm.addRequest(idList, authToken);
                }
            } catch (InvalidIdentification e) {
                if (req.hasAbortOnInvalid() && req.getAbortOnInvalid()) {
                    NamedObjectList nol = NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
                    WebSocketException ex = new WebSocketException(requestId, e);
                    ex.attachData("InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
                    throw ex;
                } else {
                    idList = new ArrayList<>(idList);
                    idList.removeAll(e.invalidParameters);
                    if (idList.isEmpty()) {
                        log.warn("Received subscribe attempt will all-invalid parameters");
                    } else {
                        log.warn("Received subscribe attempt with {} invalid parameters. Subscription will continue with {} remaining valids.",
                                e.invalidParameters.size(), idList.size());
                        if (log.isDebugEnabled()) {
                            log.debug("The invalid IDs are: {}", StringConverter.idListToString(e.invalidParameters));
                        }
                        if(subscriptionId!=-1) {
                            pidrm.addItemsToRequest(subscriptionId, idList, authToken);
                        } else {
                            subscriptionId=pidrm.addRequest(idList, authToken);
                        }
                    }
                    // TODO send back invalid list as part of nominal response. Requires work in the websocket framework which
                    // currently only supports ACK responses in the reply itself
                }
            }

            WebSocketReplyData reply = toAckReply(requestId);
            wsHandler.sendReply(reply);
            if(pidrm.hasParameterCache()) {
                List<ParameterValueWithId> pvlist = pidrm.getValuesFromCache(idList, authToken);
                if(!pvlist.isEmpty()) {
                    update(subscriptionId, pvlist);
                }
            }

            return null;
        } catch (InvalidIdentification e) {
            log.error("got invalid identification. Likely coming from cache", e);
            throw new WebSocketException(requestId, "internal error: "+e.toString(), e);
        } catch (InvalidRequestIdentification e) {
            log.error("got invalid subscription id", e);
            throw new WebSocketException(requestId, "internal error: "+e.toString(), e);
        } catch (NoPermissionException e) {
            log.error("no permission for parameters", e);
            throw new WebSocketException(requestId, "internal error: "+e.toString(), e);
        } catch (IOException e) {
            log.error("Exception when sending data", e);
            return null;
        }
    }

    private WebSocketReplyData unsubscribe(int requestId, NamedObjectList paraList, AuthenticationToken authToken) throws WebSocketException {
        if(subscriptionId!=-1) {
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

    private WebSocketReplyData subscribeAll(int requestId, String namespace, AuthenticationToken authToken) throws WebSocketException {
        if(subscriptionId!=-1) {
            throw new WebSocketException(requestId, "Already subscribed for this client");
        }
        try {
            subscriptionId=pidrm.subscribeAll(namespace, authToken);
        } catch (NoPermissionException e) {
            throw new WebSocketException(requestId, "No permission", e);
        }
        return toAckReply(requestId);
    }

    private WebSocketReplyData subscribeComputations(int requestId, ComputationDefList cdefList, AuthenticationToken authToken)
            throws WebSocketException {

        List<ComputationDef> computations = cdefList.getComputationList();
        List<NamedObjectId> allArguments=new ArrayList<>();
        for(ComputationDef computation : computations) {
            allArguments.addAll(computation.getArgumentList());
        }

        try {
            try {
                if(compSubscriptionId!=-1) {
                    pidrm.addItemsToRequest(compSubscriptionId, allArguments, authToken);
                } else {
                    compSubscriptionId=pidrm.addRequest(allArguments, authToken);
                }
            } catch (InvalidIdentification e) {
                if (cdefList.hasAbortOnInvalid() && cdefList.getAbortOnInvalid()) {
                    NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
                    WebSocketException ex = new WebSocketException(requestId, e);
                    ex.attachData("InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
                    throw ex;
                } else {
                    //remove computations that have as arguments the invalid parameters
                    computations = new ArrayList<>();
                    allArguments = new ArrayList<>();
                    ListIterator<ComputationDef> it = computations.listIterator();
                    while (it.hasNext()) {
                        ComputationDef computation = it.next();
                        boolean remove = false;
                        for (NamedObjectId argId : computation.getArgumentList()) {
                            if (e.invalidParameters.contains(argId)) {
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
                        log.warn("Got invalid computation arguments, but continuing subscribe attempt with remaining valids: {} ", computations);
                        if(compSubscriptionId!=-1) {
                            pidrm.addItemsToRequest(compSubscriptionId, allArguments, authToken);
                        } else {
                            compSubscriptionId=pidrm.addRequest(allArguments, authToken);
                        }
                    }
                    // TODO send back invalid list as part of nominal response. Requires work in the websocket framework which
                    // currently only supports ACK responses in the reply itself
                }
            }
        } catch (InvalidIdentification e) {
            log.error("got invalid identification. Should not happen, because checked before", e);
            throw new WebSocketException(requestId, "internal error: "+e.toString(), e);
        } catch (NoPermissionException e) {
            throw new WebSocketException(requestId, "No permission", e);
        }

        try {
            for(ComputationDef cdef : computations) {
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
        if(subscriptionId==-1) {
            throw new WebSocketException(requestId, "Not subscribed");
        }
        ParameterRequestManagerImpl prm=processor.getParameterRequestManager();
        boolean r=prm.unsubscribeAll(subscriptionId);
        if(r) {
            subscriptionId=-1;
            return toAckReply(requestId);
        } else {
            throw new WebSocketException(requestId, "There is no subscribeAll subscription for this client");
        }
    }

    @Override
    public void update(int subscrId, List<ParameterValueWithId> paramList) {
        if(wsHandler==null) return;
        if(paramList == null || paramList.size() == 0)
        {
            return;
        }

        if(subscrId==compSubscriptionId) {
            updateComputations(paramList);
            return;
        }
        ParameterData.Builder pd=ParameterData.newBuilder();
        for(ParameterValueWithId pvwi:paramList) {
            ParameterValue pv=pvwi.getParameterValue();
            pd.addParameter(pv.toGpb(pvwi.getId()));
        }
        try {
            wsHandler.sendData(ProtoDataType.PARAMETER, pd.build(), SchemaPvalue.ParameterData.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending parameter updates, quitting", e);
            quit();
        }
    }

    private void updateComputations(List<ParameterValueWithId> paramList) {
        Map<NamedObjectId, ParameterValue> parameters=new HashMap<>();
        for(ParameterValueWithId pvwi:paramList) {
            parameters.put(pvwi.getId(), pvwi.getParameterValue());
        }
        ParameterData.Builder pd=ParameterData.newBuilder();
        for(Computation c:compList) {
            org.yamcs.protobuf.Pvalue.ParameterValue pv=c.evaluate(parameters);
            if(pv!=null) pd.addParameter(pv);
        }
        if(pd.getParameterCount()==0) return;

        try {
            wsHandler.sendData(ProtoDataType.PARAMETER, pd.build(), SchemaPvalue.ParameterData.WRITE);
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
        ParameterRequestManagerImpl prm=processor.getParameterRequestManager();
        if(subscriptionId!=-1) prm.removeRequest(subscriptionId);
        if(compSubscriptionId!=-1) prm.removeRequest(compSubscriptionId);
    }

    @Override
    public void switchYProcessor(YProcessor c, AuthenticationToken authToken) throws ProcessorException {
        try {
            pidrm.switchPrm(c.getParameterRequestManager(), authToken);
            processor = c;
        } catch (InvalidIdentification e) {
            log.warn("got InvalidIdentification when resubscribing", e);
        } catch (NoPermissionException e) {
            throw new ProcessorException("No permission", e);
        }
    }
}
