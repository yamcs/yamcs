package org.yamcs.hornetq;

import static org.yamcs.api.Protocol.LINK_CONTROL_ADDRESS;
import static org.yamcs.api.Protocol.LINK_INFO_ADDRESS;
import static org.yamcs.api.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.Protocol.REQUEST_TYPE_HEADER_NAME;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.management.LinkListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;

/**
 * provides management services via hornetq
 * @author nm
 *
 */
public class HornetQManagement implements LinkListener {
    YamcsSession ysession;
    YamcsClient yclient, linkControlServer;
    static Logger log=LoggerFactory.getLogger(HornetQManagement.class.getName());
    
    ManagementService mservice;
    
    public HornetQManagement(ManagementService mservice) throws YamcsApiException, HornetQException {
        this.mservice=mservice;       
        
        if(ysession!=null) return;
        ysession=YamcsSession.newBuilder().build();
        
        //trick to make sure that the link and channel info queues exists
        yclient=ysession.newClientBuilder().setDataConsumer(LINK_INFO_ADDRESS, LINK_INFO_ADDRESS).build();
        yclient.close();
        
        yclient=ysession.newClientBuilder().setDataProducer(true).build();
       
        linkControlServer=ysession.newClientBuilder().setRpcAddress(LINK_CONTROL_ADDRESS).build();
        linkControlServer.rpcConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage message) {
                try {
                    processLinkControlMessage(message);
                } catch (Exception e) {
                    log.error("Error when processing request", e);
                }
            }
        });
    }
    
    
    private void processLinkControlMessage(ClientMessage msg) throws YamcsApiException, HornetQException {
        SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
        if(replyto==null) {
            log.warn("Did not receive a replyto header. Ignoring the request");
            return;
        }
        try {
            String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
            if("enableLink".equalsIgnoreCase(req)) {
                LinkInfo li=(LinkInfo)Protocol.decode(msg, LinkInfo.newBuilder());
                mservice.enableLink(li.getInstance(), li.getName());
                linkControlServer.sendReply(replyto, "OK", null);
            } else if("disableLink".equalsIgnoreCase(req)) {
                LinkInfo li=(LinkInfo)Protocol.decode(msg, LinkInfo.newBuilder());
                mservice.disableLink(li.getInstance(), li.getName());
                linkControlServer.sendReply(replyto, "OK", null);
            } else  {
                throw new YamcsException("Unknown request '"+req+"'");
            }
        } catch (YamcsException e) {
            log.warn("Sending error reply ", e);
            linkControlServer.sendErrorReply(replyto, e.getMessage());
        } 
        
    }
    
    public void stop() {
        try {
            ysession.close();
        } catch (HornetQException e) {
            log.error("Failed to close the yamcs session",e);
        }
    }
    
    @Override
    public void registerLink(LinkInfo linkInfo) {
        linkChanged(linkInfo);
    }
    
    @Override
    public void unregisterLink(String instance, String name) {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void linkChanged(LinkInfo linkInfo) {
        try {
            ClientMessage msg=ysession.session.createMessage(false);
            String lvn=linkInfo.getInstance()+"."+linkInfo.getName();
            msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
            Protocol.encode(msg, linkInfo);
            yclient.dataProducer.send(LINK_INFO_ADDRESS, msg);
        }   catch (HornetQException e) {
            log.error("Exception while updating link status: ", e);
        }
    }
}
