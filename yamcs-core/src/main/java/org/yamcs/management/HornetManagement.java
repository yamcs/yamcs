package org.yamcs.management;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

import static org.yamcs.api.Protocol.*;

import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;

/**
 * provides managmenet services via hornetq
 * @author nm
 *
 */
public class HornetManagement {
    YamcsSession ysession;
    YamcsClient yclient, linkControlServer;;
    static Logger log=LoggerFactory.getLogger(HornetManagement.class.getName());
    List<LinkControlImpl> links=new CopyOnWriteArrayList<LinkControlImpl>();
    ManagementService mservice;
    
    public HornetManagement(ManagementService mservice, ScheduledThreadPoolExecutor timer) throws YamcsApiException, HornetQException {
        this.mservice=mservice;
        timer.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {checkLinkUpdate();}
        }, 1, 1, TimeUnit.SECONDS);
       
        
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
                    log.error("Error when processing request");
                    e.printStackTrace();
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
                log.debug("received enableLink for "+li);
                boolean found=false;
                for(int i=0;i<links.size();i++) {
                    LinkControlImpl lci=links.get(i);
                    LinkInfo li2=lci.getLinkInfo();
                    if(li2.getInstance().equals(li.getInstance()) && li2.getName().equals(li.getName())) {
                        found=true;
                        lci.enable();
                        break;
                    }
                }
                if(!found) throw new YamcsException("There is no link named '"+li.getName()+"' in instance "+li.getInstance());
                linkControlServer.sendReply(replyto, "OK", null);
            } else if("disableLink".equalsIgnoreCase(req)) {
                LinkInfo li=(LinkInfo)Protocol.decode(msg, LinkInfo.newBuilder());
                log.debug("received disableLink for "+li);
                boolean found=false;
                for(int i=0;i<links.size();i++) {
                    LinkControlImpl lci=links.get(i);
                    LinkInfo li2=lci.getLinkInfo();
                    if(li2.getInstance().equals(li.getInstance()) && li2.getName().equals(li.getName())) {
                        found=true;
                        lci.disable();
                        break;
                    }
                }
                if(!found) throw new YamcsException("There is no link named '"+li.getName()+"' in instance "+li.getInstance());
                linkControlServer.sendReply(replyto, "OK", null);
            } else  {
                throw new YamcsException("Unknown request '"+req+"'");
            }
        } catch (YamcsException e) {
            log.warn("sending error reply "+e);
            linkControlServer.sendErrorReply(replyto, e.getMessage());
        } 
        
    }
    
    
    public void registerLink(String instance, LinkControlImpl lci) {
        try {
            sendLinkUpdate(lci);
            links.add(lci);
        } catch (Exception e) {
            log.error("Exception when registering link in hornet: "+e.getMessage());
            e.printStackTrace();
        }
    }

    

    public void unRegisterLink(String instance, String name) {
        // TODO Auto-generated method stub
        
    }
    
    private void sendLinkUpdate(LinkControlImpl lci) {
        try {
            ClientMessage msg=ysession.session.createMessage(false);
            LinkInfo li=lci.getLinkInfo();
            String lvn=li.getInstance()+"."+li.getName();
            msg.putStringProperty(Message.HDR_LAST_VALUE_NAME, new SimpleString(lvn));
            Protocol.encode(msg, li);
            yclient.dataProducer.send(LINK_INFO_ADDRESS, msg);
        }   catch (HornetQException e) {
            log.error("Exception while updating links status: "+e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void checkLinkUpdate() {
        // see if any link has changed
        for(LinkControlImpl lci:links) {
            if(lci.hasChanged()) {
                sendLinkUpdate(lci);
            }
        }
    }
}
