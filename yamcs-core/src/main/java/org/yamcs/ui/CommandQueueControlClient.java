package org.yamcs.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.MessageHandler;

import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;

import static org.yamcs.api.Protocol.CMDQUEUE_INFO_ADDRESS;
import static org.yamcs.api.Protocol.CMDQUEUE_CONTROL_ADDRESS;
import static org.yamcs.api.Protocol.HDR_EVENT_NAME;

import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnector;
import org.yamcs.protobuf.Commanding.CommandQueueEntry;
import org.yamcs.protobuf.Commanding.CommandQueueInfo;
import org.yamcs.protobuf.Commanding.CommandQueueRequest;

/**
 * Controls yamcs command queues via Hornet
 * Allows to register one CommandQueueListener per (instance, yprocessor)
 * 
 * @author nm
 *
 */
public class CommandQueueControlClient implements ConnectionListener {
    YamcsConnector yconnector;
    YamcsClient yclient;
    
    List<CommandQueueListener> listeners=new CopyOnWriteArrayList<CommandQueueListener>(); //listeners for instance.chanelName
    
    public CommandQueueControlClient(YamcsConnector yconnector) {
        this.yconnector=yconnector;
        yconnector.addConnectionListener(this);
    }

    public void addCommandQueueListener(CommandQueueListener cmdQueueListener) {
        listeners.add(cmdQueueListener);
    }

    
    @Override
    public void connected(String url) {
        yclient=null;
        receiveInitialConfig();
    }

    /*this should do some filtering for the registered listeners*/
    public void receiveInitialConfig() {
        try {
            if(yclient!=null) yclient.dataConsumer.setMessageHandler(null);
            YamcsClient browser=yconnector.getSession().newClientBuilder().setDataConsumer(CMDQUEUE_INFO_ADDRESS, CMDQUEUE_INFO_ADDRESS).setBrowseOnly(true).build();
            yclient=yconnector.getSession().newClientBuilder().setDataConsumer(CMDQUEUE_INFO_ADDRESS, null).setRpc(true).build();

            ClientMessage msg;
            while((msg=browser.dataConsumer.receiveImmediate())!=null) {//send all the messages from the queue first
                sendUpdate(msg);
            }
            browser.close();

            yclient.dataConsumer.setMessageHandler(new MessageHandler() {
                @Override
                public void onMessage(ClientMessage m) {
                    sendUpdate(m);
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            sendLog("error when retrieving the initial command queue list: "+e);
        }
    }

    private void sendUpdate(ClientMessage msg) {
    	try {
    		String eventName=msg.getStringProperty(HDR_EVENT_NAME);
    		for(int i=0;i<listeners.size();i++) {
    			CommandQueueListener cql=listeners.get(i);
    			if("commandAdded".equals(eventName)) {
    				CommandQueueEntry cqe=(CommandQueueEntry)Protocol.decode(msg, CommandQueueEntry.newBuilder());
    				cql.commandAdded(cqe);
    			} else if("commandRejected".equals(eventName)) {
    				CommandQueueEntry cqe=(CommandQueueEntry)Protocol.decode(msg, CommandQueueEntry.newBuilder());
    				cql.commandRejected(cqe);
    			} else if("commandSent".equals(eventName)) {
    				CommandQueueEntry cqe=(CommandQueueEntry)Protocol.decode(msg, CommandQueueEntry.newBuilder());
    				cql.commandSent(cqe);
    			} else if("queueUpdated".equals(eventName)) {
    				CommandQueueInfo cqi=(CommandQueueInfo)Protocol.decode(msg, CommandQueueInfo.newBuilder());
    				listeners.get(i).updateQueue(cqi);
    			} else {
    				cql.log("received an unknown event on the command update queue: '"+eventName+"'");
    			}
    		}
        } catch (YamcsApiException e) {
            sendLog("error when decoding command queue info message: "+e);
        }
    }
    
    private void sendLog(String message) {
    	 for(int i=0;i<listeners.size();i++) {
             listeners.get(i).log(message);
         }
    }
    
    /**
     * Send a message to the server to change the queue state.
     * If newState=ENABLED, then changing the queue state may result in some commands being sent.
     *  The rebuild flag indicates that the command shall be rebuild (new timestamp, new pvt checks, etc)
     */
    public void setQueueState(CommandQueueInfo cqi, boolean rebuild) throws YamcsApiException, YamcsException {
    	 CommandQueueRequest cqr=CommandQueueRequest.newBuilder().setQueueInfo(cqi).setRebuild(rebuild).build();
         yclient.executeRpc(CMDQUEUE_CONTROL_ADDRESS, "setQueueState", cqr, null);
    }
    
    /**
     * Send a message to the server to release the command
     * @param cqe - reference to the command to be released
     * @param rebuild - indicate that the binary shall be rebuilt (new timestamp, new pvt checks, etc)
     * @throws YamcsException 
     */
    public void sendCommand(CommandQueueEntry cqe, boolean rebuild) throws YamcsApiException, YamcsException {
       CommandQueueRequest cqr=CommandQueueRequest.newBuilder().setQueueEntry(cqe).setRebuild(rebuild).build();
       yclient.executeRpc(CMDQUEUE_CONTROL_ADDRESS, "sendCommand", cqr, null);
        
    }
    
    /**
     * Send a message to the server to reject the command
     * @param cqe
     */
    public void rejectCommand(CommandQueueEntry cqe) throws YamcsApiException, YamcsException {
        CommandQueueRequest cqr=CommandQueueRequest.newBuilder().setQueueEntry(cqe).build();
        yclient.executeRpc(CMDQUEUE_CONTROL_ADDRESS, "rejectCommand", cqr, null);
    }
    
    @Override
    public void connecting(String url) {    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {}

    @Override
    public void disconnected() {}

    @Override
    public void log(String message) {}
}