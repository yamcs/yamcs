package org.yamcs.ui;

import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.Protocol;
import static org.yamcs.api.Protocol.LINK_INFO_ADDRESS;
import static org.yamcs.api.Protocol.LINK_CONTROL_ADDRESS;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;


/**
 * enables/disables TM/TC/Parameter links
 * @author nm
 *
 */
public class LinkControlClient implements ConnectionListener {
    LinkListener linkListener;
    YamcsConnector yconnector;
    YamcsClient yclient;
    
    
    public LinkControlClient(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }

    public void setLinkListener(LinkListener linkListener) {
        this.linkListener=linkListener;
    }
    
    public void enable(LinkInfo li) throws YamcsApiException, YamcsException{
        yclient.executeRpc(LINK_CONTROL_ADDRESS, "enableLink", li, null);
    }

    public void disable(LinkInfo li) throws YamcsApiException, YamcsException {
        yclient.executeRpc(LINK_CONTROL_ADDRESS, "disableLink", li, null);
    }

    /**
     * reads the full link status
     */
    public void receiveInitialConfig() {
        try {
            if(yclient==null) {
                yclient=yconnector.getSession().newClientBuilder().setDataConsumer(LINK_INFO_ADDRESS, null).setRpc(true).build();
            } else {
                yclient.dataConsumer.setMessageHandler(null); //unsubscribe temporarely
            }
            YamcsClient browser=yconnector.getSession().newClientBuilder().setDataConsumer(LINK_INFO_ADDRESS, LINK_INFO_ADDRESS).setBrowseOnly(true).build();
            ClientMessage msg;
            while((msg=browser.dataConsumer.receiveImmediate())!=null) {//send all the messages from the queue first
                LinkInfo li=(LinkInfo)Protocol.decode(msg, LinkInfo.newBuilder());
                linkListener.updateLink(li);
            }
            browser.close();

            yclient.dataConsumer.setMessageHandler(new MessageHandler() {
                @Override
                public void onMessage(ClientMessage msg1) {
                    try {
                        LinkInfo li = (LinkInfo)Protocol.decode(msg1, LinkInfo.newBuilder());
                        linkListener.updateLink(li);
                    } catch (YamcsApiException e) {
                        linkListener.log("Error when decoding message "+e.getMessage());
                    }

                }
            });
        } catch(Exception e) {
            e.printStackTrace();
            linkListener.log("Error when updating links "+e.getMessage());
        }
    }

   

    @Override
    public void connected(String url) {
        yclient=null;
        receiveInitialConfig();
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {}

    @Override
    public void disconnected() {
        yclient=null;
    }

    @Override
    public void log(String message) {}
    
    @Override
    public void connecting(String url) {    }

}
