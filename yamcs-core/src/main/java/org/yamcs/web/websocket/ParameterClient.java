package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.*;
import org.yamcs.parameter.ParameterRequestManagerImpl;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Comp.ComputationDef;
import org.yamcs.protobuf.Comp.ComputationDefList;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.SchemaComp;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.web.Computation;
import org.yamcs.web.ComputationFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Provides realtime parameter subscription via web.  
 *
 * TODO better deal with exceptions
 *
 * @author nm
 *
 */
public class ParameterClient extends AbstractWebSocketResource implements ParameterWithIdConsumer {
    Logger log;
    int subscriptionId=-1;

    //subscription id used for computations
    int compSubscriptionId=-1;

    final CopyOnWriteArrayList<Computation> compList=new CopyOnWriteArrayList<>();

    ParameterWithIdRequestHelper pidrm;

    public ParameterClient(YProcessor channel, WebSocketServerHandler wsHandler) {
	super(channel, wsHandler);
	log = LoggerFactory.getLogger(ParameterClient.class.getName() + "[" + channel.getInstance() + "]");
	pidrm = new ParameterWithIdRequestHelper(channel.getParameterRequestManager(), this);
	wsHandler.addResource("parameter", this);
	wsHandler.addResource("request", this);
    }

    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
	switch (ctx.getOperation()) {
	    case "subscribe":
		NamedObjectList subscribeList = decoder.decodeMessageData(ctx, SchemaYamcs.NamedObjectList.MERGE).build();
		return subscribe(ctx.getRequestId(), subscribeList);
	    case "subscribeAll":
		StringMessage stringMessage = decoder.decodeMessageData(ctx, SchemaYamcs.StringMessage.MERGE).build();
		return subscribeAll(ctx.getRequestId(), stringMessage.getMessage());
	    case "unsubscribe":
		NamedObjectList unsubscribeList = decoder.decodeMessageData(ctx, SchemaYamcs.NamedObjectList.MERGE).build();
		return unsubscribe(ctx.getRequestId(), unsubscribeList);
	    case "unsubscribeAll":
		return unsubscribeAll(ctx.getRequestId());
	    case "subscribeComputations":
		ComputationDefList cdefList = decoder.decodeMessageData(ctx, SchemaComp.ComputationDefList.MERGE).build();
		return subscribeComputations(ctx.getRequestId(), cdefList);
	    default:
		throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
	}
    }

    private WebSocketReplyData subscribe(int requestId, NamedObjectList paraList) throws WebSocketException {
	//TODO check permissions and subscription limits
        List<NamedObjectId> idlist = paraList.getListList();
	try {
	    if(subscriptionId!=-1) {
		pidrm.addItemsToRequest(subscriptionId, idlist);
	    } else {
		subscriptionId=pidrm.addRequest(idlist);
	    }
	    WebSocketReplyData reply = toAckReply(requestId);
	    wsHandler.sendReply(reply);
	    List<ParameterValueWithId> pvlist = pidrm.getValuesFromCache(idlist);
	    if(!pvlist.isEmpty()) {
	        update(subscriptionId, pvlist);
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
	}
    }

    private WebSocketReplyData unsubscribe(int requestId, NamedObjectList paraList) throws WebSocketException {
	//TODO check permissions and subscription limits
	if(subscriptionId!=-1) {
	    pidrm.removeItemsFromRequest(subscriptionId, paraList.getListList());
	    return toAckReply(requestId);
	} else {
	    throw new WebSocketException(requestId, "Not subscribed to anything");
	}
    }

    private WebSocketReplyData subscribeAll(int requestId, String namespace) throws WebSocketException {
	//TODO check permissions and subscription limits
	if(subscriptionId!=-1) {
	    throw new WebSocketException(requestId, "Already subscribed for this client");
	}
	subscriptionId=pidrm.subscribeAll(namespace);
	return toAckReply(requestId);
    }

    private WebSocketReplyData subscribeComputations(int requestId, ComputationDefList cdefList) throws WebSocketException {
	//TODO check permissions and subscription limits
	List<NamedObjectId> argList=new ArrayList<>();
	for(ComputationDef c: cdefList.getCompDefList()) {
	    argList.addAll(c.getArgumentList());
	}

	try {
	    if(compSubscriptionId!=-1) {
		pidrm.addItemsToRequest(compSubscriptionId, argList);
	    } else {
		compSubscriptionId=pidrm.addRequest(argList);
	    }
	} catch (InvalidIdentification e) {
	    NamedObjectList nol=NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
	    WebSocketException ex = new WebSocketException(requestId, e);
	    ex.attachData("InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
	    throw ex;
	}

	try {
	    for(ComputationDef cdef:cdefList.getCompDefList()) {
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
	ParameterRequestManagerImpl prm=channel.getParameterRequestManager();
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
    public void quit() {
	ParameterRequestManagerImpl prm=channel.getParameterRequestManager();
	if(subscriptionId!=-1) prm.removeRequest(subscriptionId);
	if(compSubscriptionId!=-1) prm.removeRequest(compSubscriptionId);
    }

    public void switchYProcessor(YProcessor c) throws YProcessorException {
	try {
	    pidrm.switchPrm(c.getParameterRequestManager());
	} catch (InvalidIdentification e) {
	    log.warn("got InvalidIdentification when resubscribing");
	    e.printStackTrace();
	}
    }
}
