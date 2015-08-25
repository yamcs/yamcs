package org.yamcs.web.websocket;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.parameter.ParameterValueWithId;
import org.yamcs.parameter.ParameterWithIdConsumer;
import org.yamcs.parameter.ParameterWithIdRequestHelper;
import org.yamcs.protobuf.Websocket.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.security.AuthenticationToken;

public class ContainerResource extends AbstractWebSocketResource implements ParameterWithIdConsumer{
	Logger log;
	int subscriptionId = -1;
	
	public ContainerResource(YProcessor yproc, WebSocketServerHandler wsHandler) {
		super(yproc, wsHandler);
		 
        log = LoggerFactory.getLogger(ParameterResource.class.getName() + "[" + yproc.getInstance() + "]");
        //pidrm = new ParameterWithIdRequestHelper(yproc.getParameterRequestManager(), this);
        wsHandler.addResource("parameter", this);
        wsHandler.addResource("request", this);
	}

	@Override
	public void update(int subscriptionId, List<ParameterValueWithId> params) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public WebSocketReplyData processRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder,
			AuthenticationToken authToken) throws WebSocketException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void quit() {
		// TODO Auto-generated method stub
		
	}

}
