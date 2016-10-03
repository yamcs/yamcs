package org.yamcs.ui;

import java.util.concurrent.CompletableFuture;

import io.netty.handler.codec.http.HttpMethod;

import org.yamcs.YamcsException;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.rest.RestClient;
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
    final LinkListener linkListener;
    YamcsConnector yconnector;
  
    
    public LinkControlClient(YamcsConnector yconnector, LinkListener linkListener) {
        this.yconnector = yconnector;
        yconnector.addConnectionListener(this);
        this.linkListener = linkListener;
    }

    
    public void enable(LinkInfo li) throws YamcsApiException, YamcsException {
        RestClient restClient = yconnector.getRestClient();
        // PATCH /api/links/:instance/:name
        String resource = "/links/"+li.getInstance()+"/"+li.getName()+"?state=enabled";
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                linkListener.log("Exception enabling link: "+exception.getMessage());
            }
        });
    }

    public void disable(LinkInfo li) throws YamcsApiException, YamcsException {
        RestClient restClient = yconnector.getRestClient();
        // PATCH /api/links/:instance/:name
        String resource = "/links/"+li.getInstance()+"/"+li.getName()+"?state=disabled";
        CompletableFuture<byte[]> cf = restClient.doRequest(resource, HttpMethod.PATCH);
        cf.whenComplete((result, exception) -> {
            if(exception!=null) {
                linkListener.log("Exception disabling link: "+exception.getMessage());
            }
        });
    }

    @Override
    public void connected(String url) {
        receiveInitialConfig();
    }

    /**
     * this is called at the initial connection and when the instance is changed in the Yamcs Monitor.
     * 
     * The server is nice enough to send us the full configuration each time we subscribe even when we are already subscribed.
     */
    public void receiveInitialConfig() {
        WebSocketRequest wsr = new WebSocketRequest(LinkResource.RESOURCE_NAME, LinkResource.OP_subscribe);
        yconnector.performSubscription(wsr, this, this);
        
    }
    /** do nothing - we are only interested in connected in order to subscribe to link info information*/
    @Override
    public void connectionFailed(String url, YamcsException exception) {}

    /** do nothing - we are only interested in connected in order to subscribe to link info information*/
    @Override
    public void disconnected() {   }
    
    /** do nothing - we are only interested in connected in order to subscribe to link info information*/
    @Override
    public void connecting(String url) {    }

    /** do nothing - we are only interested in connected in order to subscribe to link info information*/
    @Override
    public void log(String message) {}
    
    
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
