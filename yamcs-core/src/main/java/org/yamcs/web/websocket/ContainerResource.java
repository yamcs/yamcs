package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.NoPermissionException;
import org.yamcs.YProcessor;
import org.yamcs.YProcessorException;
import org.yamcs.container.ContainerValueWithId;
import org.yamcs.container.ContainerWithIdConsumer;
import org.yamcs.container.ContainerWithIdRequestHelper;
import org.yamcs.protobuf.Cvalue.ContainerData;
import org.yamcs.protobuf.SchemaCvalue;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.security.AuthenticationToken;

public class ContainerResource extends AbstractWebSocketResource implements ContainerWithIdConsumer {
	Logger log;
	int subscriptionId = -1;

    public static final String WSR_subscribe = "subscribe";
    public static final String WSR_unsubscribe = "unsubscribe";
	
    ContainerWithIdRequestHelper cidrm;
    
	public ContainerResource(YProcessor yproc, WebSocketServerHandler wsHandler) {
		super(yproc, wsHandler);
		 
        log = LoggerFactory.getLogger(ParameterResource.class.getName() + "[" + yproc.getInstance() + "]");
        
        cidrm = new ContainerWithIdRequestHelper(yproc.getParameterRequestManager(), this);
        
        wsHandler.addResource("container", this);
	}

	@Override
	public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder,
			AuthenticationToken authToken) throws WebSocketException {
        switch (ctx.getOperation()) {
        case WSR_subscribe:
            NamedObjectList subscribeList = decoder.decodeMessageData(ctx, SchemaYamcs.NamedObjectList.MERGE).build();
            return subscribe(ctx.getRequestId(), subscribeList, authToken);
        case WSR_unsubscribe:
            NamedObjectList unsubscribeList = decoder.decodeMessageData(ctx, SchemaYamcs.NamedObjectList.MERGE).build();
            return unsubscribe(ctx.getRequestId(), unsubscribeList, authToken);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
	}
	
	private WebSocketReplyData subscribe(int requestId, NamedObjectList paraList, AuthenticationToken authToken)
			throws WebSocketException {
		
		List<NamedObjectId> idlist = paraList.getListList();		
		try {
			if (subscriptionId != -1) {
				cidrm.subscribeContainers(subscriptionId, idlist, authToken);
			} else {
				subscriptionId = cidrm.subscribeContainers(idlist, authToken);
			}
			WebSocketReplyData reply = toAckReply(requestId);
			wsHandler.sendReply(reply);
			return null;			
		} catch (InvalidIdentification e) {
			NamedObjectList nol = NamedObjectList.newBuilder().addAllList(e.invalidParameters).build();
			WebSocketException ex = new WebSocketException(requestId, e);
			ex.attachData("InvalidIdentification", nol, SchemaYamcs.NamedObjectList.WRITE);
			throw ex;
		} catch (InvalidRequestIdentification e) {
			log.error("got invalid subscription id", e);
			throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
		} catch (IOException e) {
			log.error("Exception when sending data", e);
			return null;
		} catch (NoPermissionException e) {
			log.error("no permission for parameters", e);
			throw new WebSocketException(requestId, "internal error: " + e.toString(), e);
		}
	}

    private WebSocketReplyData unsubscribe(int requestId, NamedObjectList paraList, AuthenticationToken authToken) throws WebSocketException {
        if(subscriptionId!=-1) {
            try {
                cidrm.unsubscribeContainer(subscriptionId, paraList.getListList(), authToken);
            } catch (NoPermissionException e) {
                throw new WebSocketException(requestId, "No permission", e);
            }
            return toAckReply(requestId);
        } else {
            throw new WebSocketException(requestId, "Not subscribed to anything");
        }
    }	

	@Override
	public void update(int subscriptionId, List<ContainerValueWithId> containers) {
        if(wsHandler==null) {
        	return;
        }
        
        if(containers == null || containers.size() == 0) {
            return;
        }
        
        ContainerData.Builder cdata= ContainerData.newBuilder();
        for (ContainerValueWithId container: containers) {
        	cdata.addContainer(container.toGbpContainerData());
        }

        try {
            wsHandler.sendData(ProtoDataType.CONTAINER, cdata.build(), SchemaCvalue.ContainerData.WRITE);
        } catch (Exception e) {
            log.warn("got error when sending container updates, quitting", e);
            quit();
        }        	
	}

	@Override
	public void quit() {
		if (subscriptionId != -1) {
			cidrm.removeSubscription(subscriptionId);
		}
	}
	
    public void switchYProcessor(YProcessor c, AuthenticationToken authToken)
            throws YProcessorException, NoPermissionException {
        try {
            cidrm.switchPrm(c.getParameterRequestManager(), authToken);
        } catch (InvalidIdentification e) {
            log.warn("got InvalidIdentification when resubscribing");
            e.printStackTrace();
        }
    }	
}
