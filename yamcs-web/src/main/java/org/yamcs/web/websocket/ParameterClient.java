package org.yamcs.web.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.*;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Comp.ComputationDef;
import org.yamcs.protobuf.Comp.ComputationDefList;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.SchemaComp;
import org.yamcs.protobuf.SchemaPvalue;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.web.Computation;
import org.yamcs.web.ComputationFactory;

import java.io.InputStream;
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

    public ParameterClient(Channel channel, WebSocketServerHandler wsHandler) {
	super(channel, wsHandler);
	log = LoggerFactory.getLogger(ParameterClient.class.getName() + "[" + channel.getInstance() + "]");
	pidrm = new ParameterWithIdRequestHelper(channel.getParameterRequestManager(), this);
    }

    public void processRequest(WebSocketDecodeContext ctx, InputStream in) throws WebSocketException {
	WebSocketDecoder decoder = wsHandler.getJsonDecoder();
	switch (ctx.getOperation()) {
	    case "subscribe":
		NamedObjectList subscribeList = decoder.decodeMessage(ctx, in, SchemaYamcs.NamedObjectList.MERGE).build();
		subscribe(ctx.getRequestId(), subscribeList);
		break;
	    case "subscribeAll":
		StringMessage stringMessage = decoder.decodeMessage(ctx, in, SchemaYamcs.StringMessage.MERGE).build();
		subscribeAll(ctx.getRequestId(), stringMessage.getMessage());
		break;
	    case "unsubscribe":
		NamedObjectList unsubscribeList = decoder.decodeMessage(ctx, in, SchemaYamcs.NamedObjectList.MERGE).build();
		unsubscribe(ctx.getRequestId(), unsubscribeList);
		break;
	    case "unsubscribeAll":
		unsubscribeAll(ctx.getRequestId());
		break;
	    case "subscribeComputations":
		ComputationDefList cdefList = decoder.decodeMessage(ctx, in, SchemaComp.ComputationDefList.MERGE).build();
		subscribeComputations(ctx.getRequestId(), cdefList);
		break;
	    default:
		throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
	}
    }

    private void subscribe(int requestId, NamedObjectList paraList) throws WebSocketException {
	//TODO check permissions and subscription limits
	try {
	    if(subscriptionId!=-1) {
		pidrm.addItemsToRequest(subscriptionId, paraList.getListList());
	    } else {
		subscriptionId=pidrm.addRequest(paraList.getListList());
	    }
	    wsHandler.sendAckReply(requestId);
	} catch (InvalidIdentification e) {
	    NamedObjectList nol = NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
	    throw new StructuredWebSocketException(requestId, "InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
	} catch (InvalidRequestIdentification e) {
	    log.error("got invalid subscription id", e);
	    throw new WebSocketException(requestId, "internal error: "+e.toString(), e);
	}
    }

    private void unsubscribe(int requestId, NamedObjectList paraList) throws WebSocketException {
	//TODO check permissions and subscription limits
	if(subscriptionId!=-1) {
	    pidrm.removeItemsFromRequest(subscriptionId, paraList.getListList());
	    wsHandler.sendAckReply(requestId);
	} else {
	    throw new WebSocketException(requestId, "Not subscribed to anything");
	}
	wsHandler.sendAckReply(requestId);
    }

    private void subscribeAll(int requestId, String namespace) throws WebSocketException {
	//TODO check permissions and subscription limits
	if(subscriptionId!=-1) {
	    throw new WebSocketException(requestId, "Already subscribed for this client");
	}
	subscriptionId=pidrm.subscribeAll(namespace);
	wsHandler.sendAckReply(requestId);
    }

    private void subscribeComputations(int requestId, ComputationDefList cdefList) throws WebSocketException {
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
	    throw new StructuredWebSocketException(requestId, "InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
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
	wsHandler.sendAckReply(requestId);
    }

    private void unsubscribeAll(int requestId) throws WebSocketException {
	if(subscriptionId==-1) {
	    throw new WebSocketException(requestId, "Not subscribed");
	}
	ParameterRequestManager prm=channel.getParameterRequestManager();
	boolean r=prm.unsubscribeAll(subscriptionId);
	if(r) {
	    wsHandler.sendAckReply(requestId);
	    subscriptionId=-1;
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
	ParameterRequestManager prm=channel.getParameterRequestManager();
	if(subscriptionId!=-1) prm.removeRequest(subscriptionId);
	if(compSubscriptionId!=-1) prm.removeRequest(compSubscriptionId);
    }

    public void switchChannel(Channel c) throws ChannelException {
	try {
	    pidrm.switchPrm(c.getParameterRequestManager());
	} catch (InvalidIdentification e) {
	    log.warn("got InvalidIdentification when resubscribing");
	    e.printStackTrace();
	}
    }
}
