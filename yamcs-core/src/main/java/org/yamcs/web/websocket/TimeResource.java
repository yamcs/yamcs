package org.yamcs.web.websocket;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YProcessor;
import org.yamcs.protobuf.SchemaYamcs;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketReplyData;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.TimeInfo;
import org.yamcs.security.AuthenticationToken;
import org.yamcs.utils.TimeEncoding;


public class TimeResource extends AbstractWebSocketResource {

    private static final Logger log = LoggerFactory.getLogger(TimeResource.class);

    public static final String OP_subscribe = "subscribe";
    private static ScheduledThreadPoolExecutor timer =  new ScheduledThreadPoolExecutor(1);

    private ScheduledFuture<?> future = null;

    public TimeResource(YProcessor yproc, WebSocketFrameHandler wsHandler) {
        super(yproc, wsHandler);
        wsHandler.addResource("time", this);
    }


    @Override
    public WebSocketReplyData processRequest(WebSocketDecodeContext ctx,    WebSocketDecoder decoder, AuthenticationToken authToken)   throws WebSocketException {
        switch (ctx.getOperation()) {
        case OP_subscribe:
            return processSubscribeRequest(ctx, decoder);
        default:
            throw new WebSocketException(ctx.getRequestId(), "Unsupported operation '" + ctx.getOperation() + "'");
        }
    }


    private WebSocketReplyData processSubscribeRequest(WebSocketDecodeContext ctx, WebSocketDecoder decoder) throws WebSocketException {
        future = timer.scheduleAtFixedRate(() -> {
            try {
                long currentTime = processor.getCurrentTime();
                TimeInfo ti = TimeInfo.newBuilder().setCurrentTime(currentTime).setCurrentTimeUTC(TimeEncoding.toString(currentTime)).build();
                wsHandler.sendData(ProtoDataType.TIME_INFO, ti, SchemaYamcs.TimeInfo.WRITE);
            } catch (IOException e) {
                log.debug("Could not send time info data", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
        return toAckReply(ctx.getRequestId());
    }

    @Override
    public void quit() {
        if(future!=null) {
            future.cancel(false);
        }
    }
}
