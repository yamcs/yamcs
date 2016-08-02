package org.yamcs.ui;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.ws.ConnectionListener;
import org.yamcs.api.ws.WebSocketClientCallback;
import org.yamcs.api.ws.WebSocketRequest;
import org.yamcs.api.ws.WebSocketResponseHandler;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketExceptionData;
import org.yamcs.protobuf.Web.WebSocketServerMessage.WebSocketSubscriptionData;
import org.yamcs.protobuf.YamcsManagement.LinkEvent;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.web.websocket.LinkResource;


/**
 * enables/disables TM/TC/Parameter links
 * @author nm
 *
 */
public class LinkControlClient implements ConnectionListener, WebSocketResponseHandler, WebSocketClientCallback {
    LinkListener linkListener;
    YamcsConnector yconnector;
    
    
    public LinkControlClient(YamcsConnector yconnector) {
        this.yconnector = yconnector;
        yconnector.addConnectionListener(this);
    }

    public void setLinkListener(LinkListener linkListener) {
        this.linkListener=linkListener;
    }
    
    public void enable(LinkInfo li) throws YamcsApiException, YamcsException{
//        yclient.executeRpc(LINK_CONTROL_ADDRESS, "enableLink", li, null);
    }

    public void disable(LinkInfo li) throws YamcsApiException, YamcsException {
 //       yclient.executeRpc(LINK_CONTROL_ADDRESS, "disableLink", li, null);
    }

    /**
     * reads the full link status
     */
    public void receiveInitialConfig() {
        WebSocketRequest wsr = new WebSocketRequest(LinkResource.RESOURCE_NAME, LinkResource.OP_subscribe);
        yconnector.performSubscription(wsr, this);
    }

    @Override
    public void connected(String url) {
        receiveInitialConfig();
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {}

    @Override
    public void disconnected() {
    }

    @Override
    public void log(String message) {}
    
    @Override
    public void connecting(String url) {    }

    @Override
    public void onException(WebSocketExceptionData e) {
        System.out.println("LinkControlClient.onException "+e);
    }

    @Override
    public void onMessage(WebSocketSubscriptionData data) {
        if(data.hasLinkEvent()) {
            LinkEvent linkEv = data.getLinkEvent();
            linkListener.updateLink(linkEv.getLinkInfo());
        }
    }
}
