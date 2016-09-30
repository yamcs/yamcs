package org.yamcs.hornetq;

import static org.yamcs.api.artemis.Protocol.DATA_TO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.LINK_CONTROL_ADDRESS;
import static org.yamcs.api.artemis.Protocol.LINK_INFO_ADDRESS;
import static org.yamcs.api.artemis.Protocol.REPLYTO_HEADER_NAME;
import static org.yamcs.api.artemis.Protocol.REQUEST_TYPE_HEADER_NAME;

import java.io.IOException;
import java.io.ObjectOutputStream;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.YamcsServer;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.artemis.Protocol;
import org.yamcs.api.artemis.YamcsClient;
import org.yamcs.api.artemis.YamcsSession;
import org.yamcs.management.LinkListener;
import org.yamcs.management.ManagementService;
import org.yamcs.protobuf.YamcsManagement.LinkInfo;
import org.yamcs.protobuf.YamcsManagement.MissionDatabaseRequest;
import org.yamcs.security.HornetQAuthManager;
import org.yamcs.security.HqClientMessageToken;
import org.yamcs.security.Privilege;
import org.yamcs.utils.ActiveMQBufferOutputStream;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * provides management services via hornetq/artemis
 * @author nm
 *
 */
public class ArtemisManagement extends AbstractService implements LinkListener {
    YamcsSession ysession;
    YamcsClient yclient, linkControlServer;
    static Logger log=LoggerFactory.getLogger(ArtemisManagement.class.getName());
    static Logger staticlog=LoggerFactory.getLogger(ArtemisManagement.class);
    
    ManagementService mservice;
    HornetQCommandQueueManagement hornetCmdQueueMgr;
    HornetQProcessorManagement hornetProcessorMgr;
    
    public ArtemisManagement() throws ConfigurationException {
        if(yamcsSession!=null) {
            throw new ConfigurationException("This service cannot be instantiated more than once");
        }
    }
    static YamcsSession yamcsSession;
    static YamcsClient ctrlAddressClient;

    public static void setupYamcsServerControl() throws Exception {
        //create already the queue here to reduce (but not eliminate :( ) the chance that somebody connects to it before yamcs is started fully
        yamcsSession = YamcsSession.newBuilder().build();
        ctrlAddressClient = yamcsSession.newClientBuilder().setRpcAddress(Protocol.YAMCS_SERVER_CONTROL_ADDRESS).setDataProducer(true).build();
        
        

        ctrlAddressClient.rpcConsumer.setMessageHandler(new MessageHandler() {
            @Override
            public void onMessage(ClientMessage msg) {
                SimpleString replyto=msg.getSimpleStringProperty(REPLYTO_HEADER_NAME);
                if(replyto==null) {
                    staticlog.warn("did not receive a replyto header. Ignoring the request");
                    return;
                }
                try {
                    String req=msg.getStringProperty(REQUEST_TYPE_HEADER_NAME);
                    staticlog.debug("received request "+req);
                    if("getYamcsInstances".equalsIgnoreCase(req)) {
                        ctrlAddressClient.sendReply(replyto, "OK", YamcsServer.getYamcsInstances());
                    } else  if("getMissionDatabase".equalsIgnoreCase(req)) {

                        Privilege priv = Privilege.getInstance();
                        HqClientMessageToken authToken = new HqClientMessageToken(msg, null);
                        if( ! priv.hasPrivilege(authToken, Privilege.Type.SYSTEM, "MayGetMissionDatabase" ) ) {
                            staticlog.warn("User '{}' does not have 'MayGetMissionDatabase' privilege.");
                            ctrlAddressClient.sendErrorReply(replyto, "Privilege required but missing: MayGetMissionDatabase");
                            return;
                        }
                        SimpleString dataAddress=msg.getSimpleStringProperty(DATA_TO_HEADER_NAME);
                        if(dataAddress == null) {
                            staticlog.warn("Received a getMissionDatabase without a "+DATA_TO_HEADER_NAME +" header");
                            ctrlAddressClient.sendErrorReply(replyto, "no data address specified");
                            return;
                        }
                        MissionDatabaseRequest mdr = (MissionDatabaseRequest)Protocol.decode(msg, MissionDatabaseRequest.newBuilder());
                        sendMissionDatabase(mdr, replyto, dataAddress);
                    } else {
                        staticlog.warn("Received invalid request: "+req);
                    }
                } catch (Exception e) {
                    staticlog.warn("exception received when sending the reply: ", e);
                }
            }
        });
    }

    private static void sendMissionDatabase(MissionDatabaseRequest mdr, SimpleString replyTo, SimpleString dataAddress) throws ActiveMQException {
        final XtceDb xtcedb;
        try {
            if(mdr.hasInstance()) {
                xtcedb=XtceDbFactory.getInstance(mdr.getInstance());
            } else if(mdr.hasDbConfigName()){
                xtcedb=XtceDbFactory.createInstance(mdr.getDbConfigName());
            } else {
                staticlog.warn("getMissionDatabase request received with none of the instance or dbConfigName specified");
                ctrlAddressClient.sendErrorReply(replyTo, "Please specify either instance or dbConfigName");
                return;
            }
        
            ClientMessage msg=yamcsSession.session.createMessage(false);
            ObjectOutputStream oos=new ObjectOutputStream(new ActiveMQBufferOutputStream(msg.getBodyBuffer()));
            oos.writeObject(xtcedb);
            oos.close();
            ctrlAddressClient.sendReply(replyTo, "OK", null);
            ctrlAddressClient.sendData(dataAddress, msg);
        } catch (ConfigurationException e) {
            YamcsException ye=new YamcsException(e.toString());
            ctrlAddressClient.sendErrorReply(replyTo, ye);
        } catch (IOException e) { //this should not happen since all the ObjectOutputStream happens in memory
            throw new RuntimeException(e);
        }
    }
    
    private void processLinkControlMessage(ClientMessage msg) throws YamcsApiException, ActiveMQException {
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
            yclient.sendData(LINK_INFO_ADDRESS, msg);
        }   catch (ActiveMQException e) {
            log.error("Exception while updating link status: ", e);
        }
    }


    @Override
    protected void doStart() {
       
        this.mservice = ManagementService.getInstance();    

        try {
            ysession=YamcsSession.newBuilder().build();

            //trick to make sure that the link and channel info queues exists
            yclient = ysession.newClientBuilder().setDataConsumer(LINK_INFO_ADDRESS, LINK_INFO_ADDRESS).build();
            yclient.close();

            yclient = ysession.newClientBuilder().setDataProducer(true).build();

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
            
            hornetCmdQueueMgr = new HornetQCommandQueueManagement(mservice);
            hornetProcessorMgr = new HornetQProcessorManagement(mservice);
            mservice.addLinkListener(this);
            mservice.addCommandQueueListener(hornetCmdQueueMgr);
            mservice.addManagementListener(hornetProcessorMgr);

            notifyStarted();

        }
        catch (Exception e) {
            notifyFailed(e);
        }

    }

    @Override
    protected void doStop() {
        try {
            ysession.close();
            hornetCmdQueueMgr.stop();
            hornetProcessorMgr.close();
            Protocol.closeKiller();
            
            notifyStopped();
        } catch (Exception e) {
            log.error("Failed to close the yamcs session",e);
        }
    }

    public static void configureNonBlocking(SimpleString dataAddress) {
        // TODO Auto-generated method stub
        
    }
}
